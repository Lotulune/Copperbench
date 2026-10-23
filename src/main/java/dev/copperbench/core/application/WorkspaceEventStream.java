package dev.copperbench.core.application;

import dev.copperbench.core.contract.UiCore.Event;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

/** Ordered retention and delivery. Publishers enqueue under the workspace lock; callbacks run without it. */
final class WorkspaceEventStream {
	private static final Logger LOG = LogManager.getLogger(WorkspaceEventStream.class);
	private final int historyLimit;
	private final Deque<Event> history = new ArrayDeque<>();
	private final List<Subscription> listeners = new ArrayList<>();
	private final Deque<Delivery> pending = new ArrayDeque<>();
	private boolean delivering;

	WorkspaceEventStream(int historyLimit) { this.historyLimit = historyLimit; }

	synchronized void publish(Event event) {
		history.addLast(event);
		while (history.size() > historyLimit) history.removeFirst();
		if (!listeners.isEmpty()) {
			pending.addLast(new Delivery(event, List.copyOf(listeners)));
			startDelivery();
		}
	}

	synchronized AutoCloseable subscribe(long afterSequence, Consumer<Event> listener) {
		Subscription subscription = new Subscription(listener);
		listeners.add(subscription);
		for (Event event : history)
			if (event.sequence() > afterSequence)
				pending.addLast(new Delivery(event, List.of(subscription)));
		startDelivery();
		return () -> {
			synchronized (WorkspaceEventStream.this) {
				subscription.active = false;
				listeners.remove(subscription);
			}
		};
	}

	synchronized List<Event> history() { return List.copyOf(history); }

	private void startDelivery() {
		if (delivering || pending.isEmpty()) return;
		delivering = true;
		Thread.ofVirtual().name("Copperbench-workspace-events").start(this::deliver);
	}

	private void deliver() {
		while (true) {
			Delivery delivery;
			synchronized (this) {
				delivery = pending.pollFirst();
				if (delivery == null) { delivering = false; return; }
			}
			for (Subscription subscription : delivery.recipients()) {
				if (!subscription.active) continue;
				try { subscription.listener.accept(delivery.event()); }
				catch (RuntimeException failure) { LOG.debug("Workspace event listener disconnected", failure); }
			}
		}
	}

	private static final class Subscription {
		private final Consumer<Event> listener;
		private volatile boolean active = true;
		private Subscription(Consumer<Event> listener) { this.listener = listener; }
	}
	private record Delivery(Event event, List<Subscription> recipients) {}
}
