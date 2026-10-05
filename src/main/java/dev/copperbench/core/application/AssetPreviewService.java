package dev.copperbench.core.application;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.assets.AssetDescriptor;
import dev.copperbench.assets.AssetReferenceGraph;
import dev.copperbench.assets.AssetWorkspaceService;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Bounded bytes for an indexed asset; relationships come exclusively from the existing Core graph. */
public final class AssetPreviewService {
    static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;
    static final int MAX_DOCUMENT_BYTES = 256 * 1024;
    static final int MAX_IMAGE_DIMENSION = 2048;
    static final int MAX_TEXTURES = 4;

    public static JsonObject preview(Path root, AssetReferenceGraph graph, String assetId, String expectedSha256) throws IOException {
        if (assetId == null || !assetId.matches("asset:[0-9a-f]{64}") || expectedSha256 == null
                || !expectedSha256.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("A valid asset ID and expected SHA-256 are required");
        AssetDescriptor asset = graph.assets().stream().filter(candidate -> candidate.id().equals(assetId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The asset is no longer indexed in this workspace"));
        if (!asset.sha256().equals(expectedSha256))
            throw new IllegalStateException("The asset changed; refresh the asset list before previewing it");
        AssetWorkspaceService workspace = new AssetWorkspaceService(root);
        JsonObject result = metadata(asset);
        result.addProperty("schemaVersion", "1.0");
        result.add("textures", new JsonArray());
        if (isImage(asset)) {
            JsonObject image = image(workspace, asset);
            result.addProperty("kind", image.has("reason") ? "unsupported" : "image");
            if (image.has("reason")) result.add("reason", image.get("reason"));
            else result.add("image", image);
        } else if (asset.category() == dev.copperbench.assets.AssetCategory.MODEL
                && asset.relativePath().toLowerCase(Locale.ROOT).endsWith(".json")) {
            if (asset.size() > MAX_DOCUMENT_BYTES) return unsupported(result, "document_too_large");
            result.addProperty("kind", "model_json");
            result.addProperty("document", new String(readVerified(workspace, asset, MAX_DOCUMENT_BYTES), StandardCharsets.UTF_8));
            JsonArray textures = result.getAsJsonArray("textures");
            Set<String> selected = new LinkedHashSet<>();
            int linkedTextures = 0;
            for (var reference : graph.outgoing(asset.relativePath())) {
                if (!"workspace_resolved".equals(reference.resolution()) || reference.targetAssetId() == null) continue;
                AssetDescriptor target = graph.assets().stream().filter(candidate -> candidate.id().equals(reference.targetAssetId()))
                        .filter(AssetPreviewService::isImage).findFirst().orElse(null);
                if (target == null || !selected.add(target.id())) continue;
                linkedTextures++;
                if (textures.size() >= MAX_TEXTURES) continue;
                JsonObject image = image(workspace, target);
                image.addProperty("sourcePointer", reference.sourcePointer());
                image.addProperty("rawValue", reference.rawValue());
                textures.add(image);
            }
            result.addProperty("linkedTextureCount", linkedTextures);
        } else return unsupported(result, "format_not_supported");
        return result;
    }

    private static JsonObject unsupported(JsonObject result, String reason) {
        result.addProperty("kind", "unsupported"); result.addProperty("reason", reason); return result;
    }

    private static boolean isImage(AssetDescriptor asset) {
        return asset.mediaType().equals("image/png") || asset.mediaType().equals("image/jpeg");
    }

    private static JsonObject image(AssetWorkspaceService workspace, AssetDescriptor asset) throws IOException {
        JsonObject image = metadata(asset);
        if (asset.size() > MAX_IMAGE_BYTES) { image.addProperty("reason", "image_too_large"); return image; }
        byte[] bytes = readVerified(workspace, asset, MAX_IMAGE_BYTES);
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) { image.addProperty("reason", "invalid_image"); return image; }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!Set.of("png", "jpeg", "jpg").contains(format)) {
                    image.addProperty("reason", "invalid_image"); return image;
                }
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION) {
                    image.addProperty("reason", "image_dimensions_exceeded"); return image;
                }
                image.addProperty("width", width); image.addProperty("height", height);
                image.addProperty("mediaType", format.equals("png") ? "image/png" : "image/jpeg");
                image.addProperty("base64", Base64.getEncoder().encodeToString(bytes));
            } catch (IOException | RuntimeException failure) {
                image.addProperty("reason", "invalid_image");
            } finally { reader.dispose(); }
        }
        return image;
    }

    private static byte[] readVerified(AssetWorkspaceService workspace, AssetDescriptor asset, int limit) throws IOException {
        Path file = workspace.resolveAuthorizedPath(asset.relativePath());
        byte[] bytes;
        try (var input = Files.newInputStream(file)) { bytes = input.readNBytes(limit + 1); }
        if (bytes.length > limit) throw new IOException("Asset exceeds the preview size limit");
        if (bytes.length != asset.size() || !sha256(bytes).equals(asset.sha256()))
            throw new IllegalStateException("The asset changed while loading; refresh the asset list before previewing it");
        return bytes;
    }

    private static JsonObject metadata(AssetDescriptor asset) {
        JsonObject result = new JsonObject();
        result.addProperty("assetId", asset.id()); result.addProperty("relativePath", asset.relativePath());
        result.addProperty("sha256", asset.sha256()); result.addProperty("mediaType", asset.mediaType());
        result.addProperty("size", asset.size());
        return result;
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
