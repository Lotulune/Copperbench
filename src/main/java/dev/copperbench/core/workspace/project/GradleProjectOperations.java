package dev.copperbench.core.workspace.project;

import dev.copperbench.core.contract.UiCore.Operation;

import java.util.EnumSet;
import java.util.Set;

/**
 * What an adopted Gradle project supports (ADR-0020 decision 1). Structured elements, procedures, registries,
 * generation, migration and version copies need the MCreator workspace model, so they are refused with a reason
 * rather than offered in a reduced form.
 */
public final class GradleProjectOperations {

	/** The workspace kind Core reports for an adopted project. */
	public static final String KIND = "gradle_project";
	public static final String UNAVAILABLE = "GRADLE_PROJECT_OPERATION_UNAVAILABLE";
	public static final String TASKS_UNAVAILABLE = "GRADLE_PROJECT_TASKS_UNAVAILABLE";

	private static final Set<Operation> STRUCTURED = EnumSet.of(
			Operation.CREATE_MOD_ELEMENT, Operation.UPDATE_MOD_ELEMENT, Operation.DELETE_MOD_ELEMENT,
			Operation.SET_MOD_ELEMENT_SOURCE_MANAGEMENT, Operation.UPDATE_PROCEDURE,
			Operation.CREATE_REGISTRY_ENTRY, Operation.UPDATE_REGISTRY_ENTRY, Operation.DELETE_REGISTRY_ENTRY,
			Operation.RENAME_REGISTRY_ENTRY, Operation.GENERATE_WORKSPACE, Operation.EXECUTE_LOADER_MIGRATION,
			Operation.IMPORT_UPSTREAM_WORKSPACE, Operation.PREPARE_RESOURCE_PACK_CLIENT, Operation.CREATE_LOCAL_TEMPLATE,
			Operation.APPLY_WORKSPACE_PLAN, Operation.BIND_BLOCKBENCH_MODEL, Operation.PUBLISH_DATAGEN_OUTPUT,
			Operation.PREVIEW_GENERATION, Operation.GET_MOD_ELEMENT_FIELD_CONTRACT, Operation.GET_FIELD_REFERENCE_OPTIONS,
			Operation.GET_MOD_ELEMENT_EDITOR, Operation.GET_ELEMENT_VISUAL_PREVIEW, Operation.PREVIEW_MOD_ELEMENT_CHANGE,
			Operation.GET_PROCEDURE_EDITOR, Operation.PREVIEW_PROCEDURE_CHANGE, Operation.PLAN_PROCEDURE_REFACTOR,
			Operation.PLAN_WORKSPACE_CHANGES, Operation.PREVIEW_WORKSPACE_PLAN, Operation.PREVIEW_REGISTRY_RENAME,
			Operation.PREVIEW_LOCAL_TEMPLATE_INSTANTIATION, Operation.PREVIEW_DATAGEN_OUTPUT,
			Operation.PREVIEW_LOADER_MIGRATION, Operation.PREVIEW_UPSTREAM_IMPORT);

	/** Builds, runs and acceptance follow in the next stage of ADR-0020. */
	private static final Set<Operation> TASKS = EnumSet.of(Operation.VALIDATE_WORKSPACE, Operation.BUILD_WORKSPACE,
			Operation.EXPORT_WORKSPACE, Operation.RUN_CLIENT, Operation.RUN_SERVER, Operation.RUN_DATAGEN,
			Operation.RUN_GAMETEST, Operation.PREPARE_GAME_TESTS, Operation.CREATE_PUBLISH_BATCH);

	private GradleProjectOperations() {
	}

	/** Diagnostic code when the operation cannot run on an adopted project, or {@code null} when it can. */
	public static String refusal(String kind, Operation operation) {
		if (!KIND.equals(kind)) return null;
		if (STRUCTURED.contains(operation)) return UNAVAILABLE;
		return TASKS.contains(operation) ? TASKS_UNAVAILABLE : null;
	}
}
