/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.history;

import java.util.List;

public interface LocalHistoryService extends AutoCloseable {

	RecoveryPoint createRecoveryPoint(RecoveryPointRequest request) throws LocalHistoryException;

	List<RecoveryPoint> listRecoveryPoints() throws LocalHistoryException;

	String currentRecoveryPointId() throws LocalHistoryException;

	List<WorkspaceChange> compare(String fromRecoveryPointId, String toRecoveryPointId)
			throws LocalHistoryException;

	List<WorkspaceChange> previewRestore(String recoveryPointId) throws LocalHistoryException;

	RestoreResult restore(String recoveryPointId) throws LocalHistoryException;

	@Override void close();
}
