package dev.copperbench.core.workspace;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.function.UnaryOperator;

/** Updates product metadata without deserializing away unknown upstream or plugin fields. */
public final class UnknownFieldPreservingJsonStore {

	public static final String PRODUCT_NAMESPACE = "dev.copperbench";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public JsonObject read(Path workspaceFile) throws IOException {
		return JsonParser.parseString(Files.readString(workspaceFile, StandardCharsets.UTF_8)).getAsJsonObject();
	}

	public JsonObject updateProductMetadata(Path workspaceFile, UnaryOperator<JsonObject> update) throws IOException {
		Objects.requireNonNull(update);
		JsonObject document = read(workspaceFile);
		JsonObject current = document.has(PRODUCT_NAMESPACE) && document.get(PRODUCT_NAMESPACE).isJsonObject()
				? document.getAsJsonObject(PRODUCT_NAMESPACE).deepCopy()
				: new JsonObject();
		JsonObject replacement = Objects.requireNonNull(update.apply(current.deepCopy())).deepCopy();
		document.add(PRODUCT_NAMESPACE, replacement);
		writeAtomically(workspaceFile, document);
		return document.deepCopy();
	}

	private void writeAtomically(Path workspaceFile, JsonObject document) throws IOException {
		Path absolute = workspaceFile.toAbsolutePath().normalize();
		Path parent = Objects.requireNonNull(absolute.getParent(), "Workspace file must have a parent directory");
		Path temporary = Files.createTempFile(parent, absolute.getFileName().toString(), ".tmp");
		try {
			Files.writeString(temporary, GSON.toJson(document), StandardCharsets.UTF_8);
			try {
				Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException ignored) {
				Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
	}
}
