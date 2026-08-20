/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.history;

import java.util.Objects;
import java.util.concurrent.Callable;

public final class RecoveryPointCoordinator {

	private final LocalHistoryService history;

	public RecoveryPointCoordinator(LocalHistoryService history) {
		this.history = Objects.requireNonNull(history);
	}

	public <T> T execute(RecoveryPointRequest request, Callable<T> operation)
			throws RecoverableOperationException {
		RecoveryPoint point;
		try {
			point = history.createRecoveryPoint(request);
		} catch (LocalHistoryException exception) {
			throw new RecoverableOperationException("Recovery point creation failed; operation was not started", "",
					exception);
		}

		try {
			return operation.call();
		} catch (Throwable failure) {
			try {
				history.restore(point.id());
			} catch (LocalHistoryException restoreFailure) {
				failure.addSuppressed(restoreFailure);
			}
			throw new RecoverableOperationException("Automated operation failed and was restored", point.id(), failure);
		}
	}
}
