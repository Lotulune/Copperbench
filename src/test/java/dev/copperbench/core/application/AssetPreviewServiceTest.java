package dev.copperbench.core.application;

import dev.copperbench.assets.AssetDescriptor;
import dev.copperbench.assets.AssetReferenceGraph;
import dev.copperbench.assets.AssetWorkspaceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class AssetPreviewServiceTest {
    @TempDir Path root;

    private Path image(String relativePath, String format, int width, int height) throws Exception {
        Path file = root.resolve(relativePath); Files.createDirectories(file.getParent());
        BufferedImage image = new BufferedImage(width, height, format.equals("jpeg") ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xff55aa77); assertTrue(ImageIO.write(image, format, file.toFile()));
        return file;
    }
    private AssetReferenceGraph graph() { return new AssetWorkspaceService(root).referenceGraph(); }
    private AssetDescriptor descriptor(AssetReferenceGraph graph, Path file) {
        return graph.assets().stream().filter(asset -> asset.relativePath().equals(root.relativize(file).toString().replace('\\', '/'))).findFirst().orElseThrow();
    }

    @Test void pngAndJpegReturnTheirActualBoundedBytesAndDimensions() throws Exception {
        for (String format : new String[] { "png", "jpeg" }) {
            Path file = image("assets/test/textures/item/icon." + format, format, 16, 8);
            var graph = graph(); var asset = descriptor(graph, file);
            var result = AssetPreviewService.preview(root, graph, asset.id(), asset.sha256());
            assertEquals("image", result.get("kind").getAsString());
            var image = result.getAsJsonObject("image");
            assertEquals(16, image.get("width").getAsInt()); assertEquals(8, image.get("height").getAsInt());
            assertArrayEquals(Files.readAllBytes(file), Base64.getDecoder().decode(image.get("base64").getAsString()));
            assertEquals(asset.sha256(), image.get("sha256").getAsString());
        }
    }

    @Test void modelTexturesUseExistingGraphResolutionWithoutInventingMissingReferences() throws Exception {
        Path texture = image("src/main/resources/assets/test/textures/item/wand.png", "png", 16, 16);
        Path model = root.resolve("src/main/resources/assets/test/models/item/wand.json");
        Files.createDirectories(model.getParent());
        String document = "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"test:item/wand\",\"layer1\":\"test:item/missing\"}}";
        Files.writeString(model, document);
        var graph = graph(); var asset = descriptor(graph, model);
        var result = AssetPreviewService.preview(root, graph, asset.id(), asset.sha256());
        assertEquals("model_json", result.get("kind").getAsString());
        assertEquals(document, result.get("document").getAsString());
        assertEquals(1, result.getAsJsonArray("textures").size());
        var resolved = result.getAsJsonArray("textures").get(0).getAsJsonObject();
        assertEquals(descriptor(graph, texture).id(), resolved.get("assetId").getAsString());
        assertEquals("/textures/layer0", resolved.get("sourcePointer").getAsString());
        assertEquals("test:item/wand", resolved.get("rawValue").getAsString());
    }

    @Test void staleSelectionAndFileChangesCannotReturnDifferentBytes() throws Exception {
        Path file = image("assets/test/textures/item/icon.png", "png", 16, 16);
        var graph = graph(); var asset = descriptor(graph, file);
        assertThrows(IllegalStateException.class, () -> AssetPreviewService.preview(root, graph, asset.id(), "0".repeat(64)));
        Files.writeString(file, "changed externally");
        assertThrows(IllegalStateException.class, () -> AssetPreviewService.preview(root, graph, asset.id(), asset.sha256()));
        assertThrows(IllegalArgumentException.class, () -> AssetPreviewService.preview(root, graph, "../../secret", asset.sha256()));
        assertThrows(IllegalArgumentException.class, () -> AssetPreviewService.preview(root, graph, "asset:" + "0".repeat(64), asset.sha256()));
    }

    @Test void imageLimitsAndInvalidImagesHaveExplicitNonPreviewOutcomes() throws Exception {
        Path oversized = root.resolve("assets/test/textures/large.png"); Files.createDirectories(oversized.getParent());
        Files.write(oversized, new byte[AssetPreviewService.MAX_IMAGE_BYTES + 1]);
        Path dimensions = image("assets/test/textures/wide.png", "png", AssetPreviewService.MAX_IMAGE_DIMENSION + 1, 1);
        Path invalid = root.resolve("assets/test/textures/invalid.png"); Files.writeString(invalid, "not an image");
        var graph = graph();
        for (var pair : java.util.Map.of(oversized, "image_too_large", dimensions, "image_dimensions_exceeded", invalid, "invalid_image").entrySet()) {
            var asset = descriptor(graph, pair.getKey());
            var result = AssetPreviewService.preview(root, graph, asset.id(), asset.sha256());
            assertEquals("unsupported", result.get("kind").getAsString());
            assertEquals(pair.getValue(), result.get("reason").getAsString());
            assertFalse(result.has("image"));
        }
    }

    @Test void bbmodelDoesNotReceiveSyntheticGeometry() throws Exception {
        Path file = root.resolve("assets/test/models/custom.bbmodel"); Files.createDirectories(file.getParent()); Files.writeString(file, "{}");
        var graph = graph(); var asset = descriptor(graph, file);
        var result = AssetPreviewService.preview(root, graph, asset.id(), asset.sha256());
        assertEquals("unsupported", result.get("kind").getAsString());
        assertEquals("format_not_supported", result.get("reason").getAsString());
        assertFalse(result.has("image")); assertFalse(result.has("document"));
    }
}
