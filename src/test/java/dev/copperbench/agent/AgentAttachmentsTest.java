package dev.copperbench.agent;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AgentAttachmentsTest {
    @TempDir Path root;

    static AgentAttachments.Item add(AgentAttachments store, String name, byte[] data) throws IOException {
        String id = AgentJson.required(store.handle(AgentJson.object("operation", "attachmentBegin", "name", name, "size", data.length), Set.of()), "id");
        for (int offset = 0; offset < data.length; offset += AgentAttachments.CHUNK_BYTES) store.handle(AgentJson.object("operation", "attachmentChunk", "id", id, "offset", offset,
                "data", Base64.getEncoder().encodeToString(Arrays.copyOfRange(data, offset, Math.min(data.length, offset + AgentAttachments.CHUNK_BYTES)))), Set.of());
        store.handle(AgentJson.object("operation", "attachmentCommit", "id", id), Set.of());
        return store.read(id);
    }

    @Test void chunksAreOrderedBoundedAndCancelledUploadsCannotBeReused() throws Exception {
        try (AgentAttachments store = new AgentAttachments(root)) {
            JsonObject begin = store.handle(AgentJson.object("operation", "attachmentBegin", "name", "notes.txt", "size", 2), Set.of());
            String id = AgentJson.required(begin, "id");
            assertThrows(IOException.class, () -> store.handle(AgentJson.object("operation", "attachmentChunk", "id", id, "offset", 1, "data", "YQ=="), Set.of()));
            assertThrows(IOException.class, () -> store.handle(AgentJson.object("operation", "attachmentCommit", "id", id), Set.of()));
            store.handle(AgentJson.object("operation", "attachmentChunk", "id", id, "offset", 0, "data", "YQ=="), Set.of());
            assertThrows(IOException.class, () -> store.handle(AgentJson.object("operation", "attachmentChunk", "id", id, "offset", 0, "data", "YQ=="), Set.of()));
            store.handle(AgentJson.object("operation", "attachmentDiscard", "id", id), Set.of());
            assertThrows(IOException.class, () -> store.handle(AgentJson.object("operation", "attachmentCommit", "id", id), Set.of()));
            assertThrows(IOException.class, () -> store.handle(AgentJson.object("operation", "attachmentBegin", "name", "../outside.txt", "size", 1), Set.of()));
            assertThrows(IOException.class, () -> store.handle(AgentJson.object("operation", "attachmentBegin", "name", "big.bin", "size", AgentAttachments.MAX_FILE_BYTES + 1), Set.of()));
            assertThrows(IOException.class, () -> store.read("../../outside"));
        }
    }

    @Test void userSelectedCopiesSurviveOriginalRemovalAndCannotSilentlyChange() throws Exception {
        Path original = root.resolve("source.md"); Files.writeString(original, "Player's sketch");
        String id;
        try (AgentAttachments store = new AgentAttachments(root)) {
            JsonObject item = store.importFiles(List.of(original)).get(0).getAsJsonObject(); id = AgentJson.required(item, "id");
            Files.delete(original);
            assertFalse(item.has("path")); assertFalse(item.has("data"));
            assertEquals("Player's sketch", Files.readString(store.read(id).path()));
            store.handle(AgentJson.object("operation", "attachmentDiscard", "id", id), Set.of(id));
            assertNotNull(store.read(id), "A draft removal must not delete a file referenced by history");
        }
        try (AgentAttachments reopened = new AgentAttachments(root)) {
            AgentAttachments.Item item = reopened.read(id); Files.writeString(item.path(), "modified");
            assertThrows(IOException.class, () -> reopened.read(id));
        }
    }

    @Test void imageTypeComesFromBytesAndPreviewContainsOnlyBoundedPngPixels() throws Exception {
        Path image = root.resolve("picture.dat"); ImageIO.write(new BufferedImage(640, 320, BufferedImage.TYPE_INT_RGB), "png", image.toFile());
        try (AgentAttachments store = new AgentAttachments(root)) {
            AgentAttachments.Item item = add(store, "unusual-name.dat", Files.readAllBytes(image));
            assertEquals("image/png", item.mediaType());
            JsonObject preview = store.handle(AgentJson.object("operation", "attachmentPreview", "id", item.id()), Set.of());
            String encoded = AgentJson.required(preview, "dataUrl"); assertTrue(encoded.startsWith("data:image/png;base64,"));
            BufferedImage thumbnail = ImageIO.read(new java.io.ByteArrayInputStream(Base64.getDecoder().decode(encoded.split(",", 2)[1])));
            assertEquals(240, thumbnail.getWidth()); assertEquals(120, thumbnail.getHeight());
            assertThrows(IOException.class, () -> add(store, "forged.png", "not an image".getBytes()));
            JsonArray ids = new JsonArray(); for (int i = 0; i < 9; i++) ids.add(item.id());
            assertThrows(IOException.class, () -> store.resolve(AgentJson.object("attachments", ids)));
        }
    }

    @Test void nativeBatchFailureDoesNotLeaveEarlierFilesAdmitted() throws Exception {
        Path source = root.resolve("file.txt"); Files.writeString(source, "text");
        try (AgentAttachments store = new AgentAttachments(root)) {
            assertThrows(IOException.class, () -> store.importFiles(List.of(source, root)));
            try (var entries = Files.list(root.resolve(".copperbench/agent-chat/attachments"))) { assertEquals(0, entries.count()); }
        }
    }

    @Test void pendingDraftOwnershipSurvivesARejectedSendAndOnlyUnusedDraftsAreReclaimed() throws Exception {
        try (AgentAttachments store = new AgentAttachments(root)) {
            var pending = add(store, "pending.txt", "pending".getBytes());
            var sent = add(store, "sent.txt", "sent".getBytes());
            assertTrue(store.isDraft(pending.id()));
            store.sent(AgentJson.array(sent.json())); assertFalse(store.isDraft(sent.id()));
            store.discardUnusedDrafts(Set.of(pending.id(), sent.id()));
            assertNotNull(store.read(pending.id()));
            store.discardUnusedDrafts(Set.of(sent.id()));
            assertFalse(Files.exists(pending.path())); assertTrue(Files.exists(sent.path()));
        }
    }
}
