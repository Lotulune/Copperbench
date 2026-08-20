package dev.copperbench.core.workspace.mcreator;

import com.google.gson.JsonObject;
import dev.copperbench.core.application.WorkspaceMutationGateway;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.core.workspace.WorkspaceState.Element;
import net.mcreator.element.GeneratableElement;
import net.mcreator.element.ModElementType;
import net.mcreator.element.types.Procedure;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.WorkspaceFileManager;
import net.mcreator.workspace.elements.ModElement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

/** Transaction participant for the first upstream-backed vertical slice: procedure CRUD. */
public final class MCreatorWorkspaceMutationGateway implements WorkspaceMutationGateway {

	public static final String ELEMENT_ID_METADATA = "dev.copperbench.elementId";
	public static final String ELEMENT_VALUES_METADATA = "dev.copperbench.values";
	private static final String EMPTY_PROCEDURE_XML = "<xml xmlns=\"https://developers.google.com/blockly/xml\">"
			+ "<block type=\"event_trigger\" deletable=\"false\" x=\"40\" y=\"40\">"
			+ "<field name=\"trigger\">no_ext_trigger</field></block></xml>";

	private final Workspace workspace;
	private final UUID workspaceId;
	private final List<WorkspaceMutationObserver> observers;

	public MCreatorWorkspaceMutationGateway(Workspace workspace, UUID workspaceId) {
		this(workspace, workspaceId, List.of());
	}

	public MCreatorWorkspaceMutationGateway(Workspace workspace, UUID workspaceId,
			List<WorkspaceMutationObserver> observers) {
		this.workspace = workspace;
		this.workspaceId = workspaceId;
		this.observers = List.copyOf(observers);
	}

	@Override public void persist(WorkspaceState before, WorkspaceState after, Operation operation,
			Element affectedElement) throws Exception {
		ModElement existing = find(affectedElement.id());
		String storedName = existing == null ? affectedElement.name() : existing.getName();
		FileSnapshot snapshot = FileSnapshot.capture(workspace, storedName, existing);
		try {
			switch (operation) {
				case CREATE_MOD_ELEMENT -> create(affectedElement);
				case UPDATE_MOD_ELEMENT -> update(existing, affectedElement);
				case DELETE_MOD_ELEMENT -> delete(existing);
				default -> throw new IllegalArgumentException("Operation is not a content mutation: " + operation);
			}
			workspace.getFileManager().saveWorkspaceDirectlyAndWait();
			for (WorkspaceMutationObserver observer : observers)
				observer.afterMutation(workspace, before, after, operation, affectedElement);
			workspace.getFileManager().advanceProductRevision(before.id(), before.revision());
		} catch (Exception exception) {
			try {
				snapshot.restore();
				workspace.reloadFromFileSystem();
			} catch (Exception rollbackFailure) {
				exception.addSuppressed(rollbackFailure);
			}
			throw exception;
		}
	}

	private void create(Element element) {
		if (!element.type().equals("procedure"))
			throw new UnsupportedOperationException("The upstream-backed stage 1 slice only creates procedures");
		if (workspace.getModElementByName(element.name()) != null)
			throw new IllegalStateException("Element already exists in upstream workspace: " + element.name());
		if (ModElementType.PROCEDURE == null)
			throw new IllegalStateException("Procedure element type is not registered");

		ModElement modElement = new ModElement(workspace, element.name(), ModElementType.PROCEDURE);
		storeProductMetadata(modElement, element);
		Procedure procedure = new Procedure(modElement);
		procedure.procedurexml = procedureXml(element.values());
		workspace.addModElement(modElement);
		workspace.getModElementManager().storeModElement(procedure);
	}

	private void update(ModElement modElement, Element element) {
		if (modElement == null)
			throw new IllegalStateException("Element is missing from upstream workspace: " + element.id());
		GeneratableElement definition = modElement.getGeneratableElement();
		if (!(definition instanceof Procedure procedure))
			throw new UnsupportedOperationException("The upstream-backed stage 1 slice only updates procedures");
		storeProductMetadata(modElement, element);
		procedure.procedurexml = procedureXml(element.values());
		workspace.markDirty();
		workspace.getModElementManager().storeModElement(procedure);
	}

	private void delete(ModElement modElement) {
		if (modElement == null)
			throw new IllegalStateException("Element is missing from upstream workspace");
		workspace.getHistoryManager().importantCheckpoint("copperbench_before_delete", modElement.getName());
		workspace.removeModElement(modElement);
	}

	private void storeProductMetadata(ModElement modElement, Element element) {
		modElement.putMetadata(ELEMENT_ID_METADATA, element.id().toString());
		modElement.putMetadata(ELEMENT_VALUES_METADATA,
				WorkspaceFileManager.gson.fromJson(element.values(), Object.class));
	}

	private String procedureXml(JsonObject values) {
		return values.has("procedurexml") && values.get("procedurexml").isJsonPrimitive()
				? values.get("procedurexml").getAsString() : EMPTY_PROCEDURE_XML;
	}

	private ModElement find(UUID elementId) {
		for (ModElement element : workspace.getModElements()) {
			Object storedId = element.getMetadata(ELEMENT_ID_METADATA);
			if (storedId != null && elementId.toString().equals(String.valueOf(storedId)))
				return element;
			UUID derived = MCreatorWorkspaceStateMapper.elementId(workspaceId, element);
			if (derived.equals(elementId))
				return element;
		}
		return null;
	}

	private record FileSnapshot(Map<Path, byte[]> files) {

		private static FileSnapshot capture(Workspace workspace, String elementName, ModElement existing)
				throws IOException {
			Path root = workspace.getWorkspaceFolder().toPath().toAbsolutePath().normalize();
			Map<Path, byte[]> files = new LinkedHashMap<>();
			capture(files, root, workspace.getFileManager().getWorkspaceFile().toPath());
			capture(files, root, workspace.getFolderManager().getModElementsDir().toPath()
					.resolve(elementName + ".mod.json"));
			if (existing != null) {
				for (var associated : existing.getAssociatedFiles())
					capture(files, root, associated.toPath());
			}
			return new FileSnapshot(files);
		}

		private static void capture(Map<Path, byte[]> files, Path root, Path candidate) throws IOException {
			Path normalized = candidate.toAbsolutePath().normalize();
			if (!normalized.startsWith(root))
				throw new IOException("Workspace mutation referenced a file outside the workspace: " + normalized);
			files.putIfAbsent(normalized, Files.isRegularFile(normalized) ? Files.readAllBytes(normalized) : null);
		}

		private void restore() throws IOException {
			for (var entry : files.entrySet()) {
				if (entry.getValue() == null) {
					Files.deleteIfExists(entry.getKey());
				} else {
					Files.createDirectories(entry.getKey().getParent());
					Files.write(entry.getKey(), entry.getValue());
				}
			}
		}
	}
}
