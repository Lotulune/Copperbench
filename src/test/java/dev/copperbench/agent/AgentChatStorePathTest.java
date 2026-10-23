package dev.copperbench.agent;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class AgentChatStorePathTest {
    @TempDir Path root;

    @Test void dotSegmentAliasKeepsOneHistoryIdentityAcrossStoreAndServiceReopen() throws Exception {
        Path child = Files.createDirectory(root.resolve("child"));
        assertHistoryReopens(child.resolve(".."), root.toRealPath());
    }

    @Test @EnabledOnOs(OS.WINDOWS)
    void windowsShortPathKeepsOneHistoryIdentityAcrossStoreAndServiceReopen() throws Exception {
        Path canonical = Files.createDirectory(root.resolve("Agent project with a long directory name")).toRealPath();
        char[] buffer = new char[32768];
        var kernel = com.sun.jna.Native.load("kernel32", ShortPaths.class);
        int length = kernel.GetShortPathNameW(new com.sun.jna.WString(canonical.toString()), buffer, buffer.length);
        assertTrue(length > 0 && length < buffer.length, "Windows could not resolve the short path");
        Path alias = Path.of(new String(buffer, 0, length));
        assumeFalse(alias.equals(canonical), "This volume does not provide short names");
        assertHistoryReopens(alias, canonical);
    }

    private void assertHistoryReopens(Path alias, Path canonical) throws Exception {
        AgentChatStore original = new AgentChatStore(alias);
        String conversation = original.create("codex").id;
        original.save();
        Path history = canonical.resolve(".copperbench/agent-chat/history.json");
        byte[] saved = Files.readAllBytes(history);
        JsonObject data = JsonParser.parseString(Files.readString(history)).getAsJsonObject();
        assertEquals(canonical.toString(), AgentJson.string(data, "canonicalRoot"));
        for (Path path : new Path[]{alias, canonical}) {
            AgentChatStore reopened = new AgentChatStore(path);
            reopened.load();
            assertEquals(conversation, reopened.selected);
            assertEquals(1, reopened.sessions.size());
            try (AgentChatService service = new AgentChatService(path)) {
                JsonObject snapshot = service.handle(new JsonObject());
                assertEquals(conversation, AgentJson.string(snapshot, "conversationId"));
                assertEquals("ok", AgentJson.string(AgentJson.child(snapshot, "history"), "level"));
            }
            assertArrayEquals(saved, Files.readAllBytes(history), "Reopening must not rewrite the history");
        }
    }

    interface ShortPaths extends com.sun.jna.win32.StdCallLibrary {
        int GetShortPathNameW(com.sun.jna.WString longPath, char[] shortPath, int length);
    }
}
