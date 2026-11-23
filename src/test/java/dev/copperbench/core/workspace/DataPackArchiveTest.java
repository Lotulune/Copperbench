package dev.copperbench.core.workspace;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class DataPackArchiveTest {
    @TempDir Path root;

    @Test void inspectionReportsWhatTheImportKeepsAndWhatItDrops() throws Exception {
        Path zip = zip("pack.zip", Map.of(
                "pack.mcmeta", "{\"pack\":{\"pack_format\":48,\"supported_formats\":[41,48],\"description\":\"Ember tweaks\"},\"filter\":{\"block\":[]}}",
                "pack.png", "png",
                "data/ember/function/start.mcfunction", "say start\n",
                "data/ember/recipe/a.json", "{}",
                "data/minecraft/tags/function/load.json", "{\"values\":[\"ember:start\"]}",
                "data/c/tags/item/ingots.json", "{\"values\":[]}",
                "data/ember/.DS_Store", "junk",
                "README.md", "readme",
                "overlay_1_20/data/ember/functions/start.mcfunction", "say old\n"));
        DataPackArchive.Inspection inspection = DataPackArchive.inspect(zip);
        assertEquals("", inspection.root());
        assertEquals("Ember tweaks", inspection.description());
        assertEquals("datapack-1.21.1", inspection.suggestedGeneratorId());
        assertEquals(List.of("c", "ember", "minecraft"), inspection.namespaces());
        assertEquals("ember", inspection.suggestedModId());
        assertEquals(4, inspection.dataFiles());
        assertTrue(inspection.packIcon());
        assertEquals(List.of("README.md", "overlay_1_20/"), inspection.skipped().stream().sorted().toList());
        assertEquals(List.of("filter", "pack.supported_formats"), inspection.droppedMetadata());
        assertTrue(inspection.supports("datapack-1.21.1"));
        assertFalse(inspection.supports("datapack-1.20.1"));
        var json = inspection.toJson();
        assertEquals(48, json.get("packFormat").getAsInt());
        assertEquals(JsonParser.parseString("[41,48]"), json.get("supportedFormats"));
        assertEquals(JsonParser.parseString("[\"datapack-1.21.1\"]"), json.get("supportedGeneratorIds"));
        assertEquals(JsonParser.parseString("[\"datapack-1.21.1\",\"datapack-1.20.1\"]"), DataPackArchive.inspect(zip("range.zip", Map.of(
                "pack.mcmeta", "{\"pack\":{\"pack_format\":15,\"supported_formats\":[15,48],\"description\":\"\"}}",
                "data/a/function/x.mcfunction", ""))).toJson().get("supportedGeneratorIds"));
    }

    @Test void aSingleWrappingFolderIsThePackRoot() throws Exception {
        Path zip = zip("wrapped.zip", Map.of(
                "Ember Pack/pack.mcmeta", "{\"pack\":{\"pack_format\":15,\"description\":{\"text\":\"Rich\"}}}",
                "Ember Pack/data/ember/functions/start.mcfunction", "say start\n",
                "__MACOSX/Ember Pack/._pack.mcmeta", "junk"));
        DataPackArchive.Inspection inspection = DataPackArchive.inspect(zip);
        assertEquals("Ember Pack/", inspection.root());
        assertEquals("datapack-1.20.1", inspection.suggestedGeneratorId());
        assertNull(inspection.description(), "a text component is not plain text");
        assertEquals(List.of("pack.description"), inspection.droppedMetadata());
        assertEquals(List.of("__MACOSX/"), inspection.skipped());

        Path target = root.resolve("workspace/src/main");
        assertEquals(1, DataPackArchive.extract(zip, inspection, target));
        assertEquals("say start\n", Files.readString(target.resolve("data/ember/functions/start.mcfunction")));
    }

    @Test void theSuggestedGeneratorIsTheNewestThePackDeclares() throws Exception {
        assertEquals("datapack-26.1.x", suggested("{\"min_format\":101.1,\"max_format\":101.1,\"description\":\"\"}"));
        assertEquals("datapack-26.1.x", suggested("{\"min_format\":[101,1],\"description\":\"\"}"));
        assertEquals("datapack-1.21.1", suggested("{\"pack_format\":15,\"supported_formats\":{\"min_inclusive\":15,\"max_inclusive\":57},\"description\":\"\"}"));
        assertEquals("datapack-1.20.1", suggested("{\"pack_format\":15,\"description\":\"\"}"));
        assertNull(suggested("{\"pack_format\":41,\"description\":\"\"}"));
        assertNull(suggested("{\"pack_format\":\"48\",\"supported_formats\":[\"a\",\"b\"],\"description\":\"\"}"));
    }

    @Test void extractionCopiesOnlyDataAndTheIconAndNeverReplacesFiles() throws Exception {
        Path zip = zip("pack.zip", Map.of(
                "pack.mcmeta", "{\"pack\":{\"pack_format\":48,\"description\":\"x\"}}",
                "pack.png", "png",
                "data/ember/function/start.mcfunction", "say start\n",
                "data/ember/.hidden/skip.json", "{}",
                "data/loose.json", "{}",
                "assets/ember/lang/en_us.json", "{}"));
        DataPackArchive.Inspection inspection = DataPackArchive.inspect(zip);
        assertEquals(1, inspection.dataFiles());
        Path target = root.resolve("src/main");
        assertEquals(2, DataPackArchive.extract(zip, inspection, target));
        assertTrue(Files.isRegularFile(target.resolve("pack.png")));
        assertTrue(Files.isRegularFile(target.resolve("data/ember/function/start.mcfunction")));
        assertFalse(Files.exists(target.resolve("data/ember/.hidden")));
        assertFalse(Files.exists(target.resolve("data/loose.json")));
        assertFalse(Files.exists(target.resolve("assets")));
        assertFalse(Files.exists(target.resolve("pack.mcmeta")), "the generator owns pack.mcmeta");

        Path again = root.resolve("again/src/main");
        Files.createDirectories(again.resolve("data/ember/function"));
        Files.writeString(again.resolve("data/ember/function/start.mcfunction"), "keep me\n");
        var conflict = assertThrows(DataPackArchive.ArchiveException.class, () -> DataPackArchive.extract(zip, inspection, again));
        assertEquals("IMPORT_ARCHIVE_PATH_INVALID", conflict.code());
        assertEquals("keep me\n", Files.readString(again.resolve("data/ember/function/start.mcfunction")));
    }

    @Test void archivesThatAreNotSafeDataPacksAreRejectedBeforeAnythingIsWritten() throws Exception {
        String meta = "{\"pack\":{\"pack_format\":48,\"description\":\"x\"}}";
        assertCode("IMPORT_ARCHIVE_NOT_DATA_PACK", zip("none.zip", Map.of("data/a/function/x.mcfunction", "say\n")));
        assertCode("IMPORT_ARCHIVE_NOT_DATA_PACK", zip("bad-meta.zip", Map.of("pack.mcmeta", "{\"format\":1}", "data/a/function/x.mcfunction", "")));
        var resourcePack = assertThrows(DataPackArchive.ArchiveException.class, () -> DataPackArchive.inspect(
                zip("resources.zip", Map.of("pack.mcmeta", meta, "assets/a/lang/en_us.json", "{}"))));
        assertEquals("IMPORT_ARCHIVE_NOT_DATA_PACK", resourcePack.code());
        assertTrue(resourcePack.getMessage().contains("resource pack"), resourcePack.getMessage());
        for (String unsafe : List.of("../escape.json", "data/a/../../escape.json", "/abs/data.json", "C:/data/a/x.json",
                "data\\a\\x.json", "data/./a/x.json"))
            assertCode("IMPORT_ARCHIVE_PATH_INVALID", zip("unsafe.zip", Map.of("pack.mcmeta", meta, unsafe, "{}")));
        assertCode("IMPORT_ARCHIVE_PATH_INVALID", zip("case.zip", Map.of("pack.mcmeta", meta,
                "data/a/function/x.mcfunction", "", "data/a/function/X.mcfunction", "")));
        Path notZip = root.resolve("text.zip");
        Files.writeString(notZip, "not a zip");
        assertCode("IMPORT_ARCHIVE_UNREADABLE", notZip);
        assertCode("IMPORT_ARCHIVE_UNREADABLE", root.resolve("missing.zip"));
        Path wrongExtension = root.resolve("pack.jar");
        Files.copy(zip("for-jar.zip", Map.of("pack.mcmeta", meta, "data/a/function/x.mcfunction", "")), wrongExtension);
        assertCode("IMPORT_ARCHIVE_UNREADABLE", wrongExtension);
    }

    @Test void oversizedEntriesAreRejected() throws Exception {
        Path zip = root.resolve("large.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("pack.mcmeta"));
            output.write("{\"pack\":{\"pack_format\":48,\"description\":\"x\"}}".getBytes(StandardCharsets.UTF_8));
            output.putNextEntry(new ZipEntry("data/a/structure/huge.nbt"));
            byte[] zeros = new byte[1024 * 1024];
            for (long written = 0; written <= DataPackArchive.MAX_ENTRY_BYTES; written += zeros.length) output.write(zeros);
        }
        assertCode("IMPORT_ARCHIVE_TOO_LARGE", zip);
    }

    private String suggested(String pack) throws Exception {
        return DataPackArchive.inspect(zip("format.zip", Map.of("pack.mcmeta", "{\"pack\":" + pack + "}",
                "data/a/function/x.mcfunction", "say\n"))).suggestedGeneratorId();
    }

    private static void assertCode(String code, Path archive) {
        var failure = assertThrows(DataPackArchive.ArchiveException.class, () -> DataPackArchive.inspect(archive));
        assertEquals(code, failure.code(), failure.getMessage());
    }

    private Path zip(String name, Map<String, String> entries) throws Exception {
        Path file = root.resolve(name);
        Files.deleteIfExists(file);
        try (OutputStream stream = Files.newOutputStream(file); ZipOutputStream output = new ZipOutputStream(stream)) {
            for (var entry : new LinkedHashMap<>(entries).entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return file;
    }
}
