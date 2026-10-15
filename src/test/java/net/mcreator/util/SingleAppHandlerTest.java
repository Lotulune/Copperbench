/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.util;

import in.pratanumandal.unique4j.Unique4jLock;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class SingleAppHandlerTest {

	@Test void acquiredLockIsReleasedByItsOwner() {
		TestLock lock = new TestLock(new LockOwner());
		SingleAppHandler handler = new SingleAppHandler(lock);

		assertTrue(handler.tryAcquireLock());
		assertTrue(lock.isHeldByCurrentAppInstance());
		handler.close();

		assertEquals(1, lock.acquireCalls);
		assertEquals(1, lock.releaseCalls);
		assertFalse(lock.isHeldByCurrentAppInstance());
	}

	@Test void secondaryInstanceCannotReleaseThePrimaryInstancesLock() throws IOException {
		LockOwner owner = new LockOwner();
		TestLock primary = new TestLock(owner);
		TestLock secondary = new TestLock(owner);
		assertTrue(primary.tryLock());
		SingleAppHandler handler = new SingleAppHandler(secondary);

		assertFalse(handler.tryAcquireLock());
		handler.close();
		handler.close();

		assertEquals(0, secondary.releaseCalls);
		assertTrue(primary.isHeldByCurrentAppInstance());
	}

	@Test void closingWithoutAnAcquisitionNeverUnlocks() {
		TestLock lock = new TestLock(new LockOwner());
		SingleAppHandler handler = new SingleAppHandler(lock);

		handler.close();
		handler.close();

		assertEquals(0, lock.acquireCalls);
		assertEquals(0, lock.releaseCalls);
	}

	@Test void failedAcquisitionKeepsTheStartupFallbackAndCannotUnlock() {
		TestLock lock = new TestLock(new LockOwner());
		IOException failure = new IOException("Unix domain path too long");
		lock.acquireFailure = failure;
		SingleAppHandler handler = new SingleAppHandler(lock);

		try (CapturedLogs logs = new CapturedLogs()) {
			assertTrue(handler.tryAcquireLock(), "The existing IOException startup policy still continues");
			assertFalse(lock.isHeldByCurrentAppInstance());
			assertDoesNotThrow(handler::close);
			assertDoesNotThrow(handler::close);
			assertEquals(0, lock.releaseCalls);
			logs.assertEvent(Level.ERROR, "Failed to handle app lock", failure);
		}
	}

	@Test void repeatedCloseCannotReleaseASubsequentOwnersLock() throws IOException {
		LockOwner owner = new LockOwner();
		TestLock first = new TestLock(owner);
		SingleAppHandler handler = new SingleAppHandler(first);
		assertTrue(handler.tryAcquireLock());
		handler.close();
		TestLock replacement = new TestLock(owner);
		assertTrue(replacement.tryLock());

		handler.close();

		assertEquals(1, first.releaseCalls);
		assertTrue(replacement.isHeldByCurrentAppInstance());
		assertEquals(0, replacement.releaseCalls);
	}

	@Test void releaseIOExceptionRemainsVisibleAndIsNotRetriedByRepeatedClose() {
		TestLock lock = new TestLock(new LockOwner());
		IOException failure = new IOException("Could not close the IPC server");
		lock.releaseFailure = failure;
		SingleAppHandler handler = new SingleAppHandler(lock);
		assertTrue(handler.tryAcquireLock());

		try (CapturedLogs logs = new CapturedLogs()) {
			assertDoesNotThrow(handler::close);
			assertDoesNotThrow(handler::close);
			assertEquals(1, lock.releaseCalls);
			logs.assertEvent(Level.WARN, "Failed to release app lock", failure);
		}
	}

	@Test void unexpectedReleaseRuntimeFailureIsNotSwallowed() {
		TestLock lock = new TestLock(new LockOwner());
		UnsupportedOperationException failure = new UnsupportedOperationException("Unexpected owned-lock failure");
		lock.releaseRuntimeFailure = failure;
		SingleAppHandler handler = new SingleAppHandler(lock);
		assertTrue(handler.tryAcquireLock());

		assertSame(failure, assertThrows(UnsupportedOperationException.class, handler::close));
		assertDoesNotThrow(handler::close);
		assertEquals(1, lock.releaseCalls);
	}

	@Test void unexpectedAcquisitionRuntimeFailureIsNotConvertedToStartupSuccess() {
		TestLock lock = new TestLock(new LockOwner());
		IllegalStateException failure = new IllegalStateException("Unexpected lock failure");
		lock.acquireRuntimeFailure = failure;
		SingleAppHandler handler = new SingleAppHandler(lock);

		assertSame(failure, assertThrows(IllegalStateException.class, handler::tryAcquireLock));
		handler.close();
		assertEquals(0, lock.releaseCalls);
	}

	@Test void aClosedHandlerCannotAcquireANewLock() {
		TestLock lock = new TestLock(new LockOwner());
		SingleAppHandler handler = new SingleAppHandler(lock);
		handler.close();

		assertThrows(IllegalStateException.class, handler::tryAcquireLock);
		assertEquals(0, lock.acquireCalls);
		assertEquals(0, lock.releaseCalls);
	}

	private static final class LockOwner {
		private TestLock owner;
	}

	private static final class TestLock implements Unique4jLock {
		private final LockOwner state;
		private int acquireCalls;
		private int releaseCalls;
		private IOException acquireFailure;
		private IOException releaseFailure;
		private RuntimeException acquireRuntimeFailure;
		private RuntimeException releaseRuntimeFailure;

		private TestLock(LockOwner state) {
			this.state = state;
		}

		@Override public boolean tryLock() throws IOException {
			acquireCalls++;
			if (acquireFailure != null)
				throw acquireFailure;
			if (acquireRuntimeFailure != null)
				throw acquireRuntimeFailure;
			if (state.owner != null)
				return false;
			state.owner = this;
			return true;
		}

		@Override public void unlock() throws IOException {
			releaseCalls++;
			if (!isHeldByCurrentAppInstance())
				throw new UnsupportedOperationException("Lock wasn't acquired by this app instance");
			if (releaseFailure != null)
				throw releaseFailure;
			if (releaseRuntimeFailure != null)
				throw releaseRuntimeFailure;
			state.owner = null;
		}

		@Override public boolean isHeldByCurrentAppInstance() {
			return state.owner == this;
		}
	}

	private static final class CapturedLogs implements AutoCloseable {
		private final Logger logger = (Logger) LogManager.getLogger(SingleAppHandler.class);
		private final Level originalLevel = logger.getLevel();
		private final boolean originalAdditive = logger.isAdditive();
		private final List<LogEvent> events = new CopyOnWriteArrayList<>();
		private final AbstractAppender appender = new AbstractAppender("SingleAppHandlerTest-" + System.nanoTime(),
				null, null, true, Property.EMPTY_ARRAY) {
			@Override public void append(LogEvent event) {
				events.add(event.toImmutable());
			}
		};

		private CapturedLogs() {
			appender.start();
			logger.addAppender(appender);
			logger.setAdditive(false);
			logger.setLevel(Level.ALL);
		}

		private void assertEvent(Level level, String message, Throwable failure) {
			assertTrue(events.stream().anyMatch(event -> event.getLevel() == level
					&& event.getMessage().getFormattedMessage().equals(message) && event.getThrown() == failure),
					"Expected the original lock failure in the application log");
		}

		@Override public void close() {
			logger.removeAppender(appender);
			logger.setLevel(originalLevel);
			logger.setAdditive(originalAdditive);
			appender.stop();
		}
	}
}
