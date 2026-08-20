package dev.copperbench.core.workspace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** In-memory transactional boundary used by every application entry point. */
public final class RevisionedWorkspaceStore {

	private final Map<UUID, Entry> workspaces = new LinkedHashMap<>();

	public synchronized void register(WorkspaceState state) {
		if (workspaces.putIfAbsent(state.id(), new Entry(state.copy())) != null)
			throw new IllegalArgumentException("Workspace already registered: " + state.id());
	}

	public Optional<WorkspaceState> read(UUID workspaceId) {
		Entry entry;
		synchronized (this) {
			entry = workspaces.get(workspaceId);
		}
		if (entry == null)
			return Optional.empty();
		synchronized (entry) {
			return Optional.of(entry.state.copy());
		}
	}

	public <T> TransactionResult<T> transact(UUID workspaceId, long expectedRevision,
			Function<WorkspaceState, Decision<T>> operation) {
		Entry entry;
		synchronized (this) {
			entry = workspaces.get(workspaceId);
		}
		if (entry == null)
			return TransactionResult.notFound();

		synchronized (entry) {
			long actualRevision = entry.state.revision();
			if (expectedRevision != actualRevision) {
				LinkedHashSet<String> changed = new LinkedHashSet<>();
				entry.changedPaths.entrySet().stream().filter(item -> item.getKey() > expectedRevision)
						.forEach(item -> changed.addAll(item.getValue()));
				return TransactionResult.conflict(actualRevision, List.copyOf(changed));
			}

			WorkspaceState candidate = entry.state.copy();
			Decision<T> decision = operation.apply(candidate);
			if (!decision.commit())
				return TransactionResult.aborted(actualRevision, decision.value());

			long newRevision = actualRevision + 1;
			candidate.committed(newRevision);
			entry.state = candidate;
			entry.changedPaths.put(newRevision, decision.changedPaths());
			return TransactionResult.committed(newRevision, decision.value());
		}
	}

	/** Coordinates runtime state with a content revision without advancing that revision. */
	public <T> TransactionResult<T> coordinate(UUID workspaceId, long expectedRevision,
			Function<WorkspaceState, T> operation) {
		Entry entry;
		synchronized (this) {
			entry = workspaces.get(workspaceId);
		}
		if (entry == null)
			return TransactionResult.notFound();
		synchronized (entry) {
			long actualRevision = entry.state.revision();
			if (expectedRevision != actualRevision) {
				LinkedHashSet<String> changed = new LinkedHashSet<>();
				entry.changedPaths.entrySet().stream().filter(item -> item.getKey() > expectedRevision)
						.forEach(item -> changed.addAll(item.getValue()));
				return TransactionResult.conflict(actualRevision, List.copyOf(changed));
			}
			return TransactionResult.coordinated(actualRevision, operation.apply(entry.state));
		}
	}

	/** Outcome of a state replacement: committed revision plus the event sequence to use. */
	public record Replacement(long revision, long sequence) {
	}

	/**
	 * Atomically replaces the registered workspace state after an out-of-band restore
	 * (local history rollback). Validates the expected revision first so concurrent
	 * writers still get a structured conflict, then advances the revision monotonically
	 * and carries the event sequence forward from the previous state.
	 */
	public TransactionResult<Replacement> replace(UUID workspaceId, long expectedRevision, WorkspaceState restored,
			java.util.Set<String> changedPaths) {
		Entry entry;
			synchronized (this) {
				entry = workspaces.get(workspaceId);
			}
		if (entry == null)
			return TransactionResult.notFound();
		synchronized (entry) {
			long actualRevision = entry.state.revision();
			if (expectedRevision != actualRevision) {
				LinkedHashSet<String> changed = new LinkedHashSet<>();
				entry.changedPaths.entrySet().stream().filter(item -> item.getKey() > expectedRevision)
						.forEach(item -> changed.addAll(item.getValue()));
				return TransactionResult.conflict(actualRevision, List.copyOf(changed));
				}
			long sequence = entry.state.nextEventSequence();
			WorkspaceState next = restored.copy();
			next.committed(actualRevision + 1);
			entry.state = next;
			entry.changedPaths.put(actualRevision + 1, List.copyOf(changedPaths));
			return TransactionResult.committed(actualRevision + 1, new Replacement(actualRevision + 1, sequence));
			}
	}

	public record Decision<T>(boolean commit, T value, List<String> changedPaths) {
		public Decision {
			changedPaths = changedPaths == null ? List.of() : List.copyOf(changedPaths);
		}

		public static <T> Decision<T> commit(T value, List<String> changedPaths) {
			return new Decision<>(true, value, changedPaths);
		}

		public static <T> Decision<T> abort(T value) {
			return new Decision<>(false, value, List.of());
		}
	}

	public record TransactionResult<T>(Status status, long revision, T value, List<String> changedPaths) {
		public enum Status { COMMITTED, COORDINATED, ABORTED, CONFLICT, NOT_FOUND }

		private static <T> TransactionResult<T> committed(long revision, T value) {
			return new TransactionResult<>(Status.COMMITTED, revision, value, List.of());
		}

		private static <T> TransactionResult<T> aborted(long revision, T value) {
			return new TransactionResult<>(Status.ABORTED, revision, value, List.of());
		}

		private static <T> TransactionResult<T> coordinated(long revision, T value) {
			return new TransactionResult<>(Status.COORDINATED, revision, value, List.of());
		}

		private static <T> TransactionResult<T> conflict(long revision, List<String> changedPaths) {
			return new TransactionResult<>(Status.CONFLICT, revision, null, changedPaths);
		}

		private static <T> TransactionResult<T> notFound() {
			return new TransactionResult<>(Status.NOT_FOUND, 0, null, List.of());
		}
	}

	private static final class Entry {
		private WorkspaceState state;
		private final Map<Long, List<String>> changedPaths = new LinkedHashMap<>();

		private Entry(WorkspaceState state) {
			this.state = state;
		}
	}
}
