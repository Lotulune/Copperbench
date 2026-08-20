package dev.copperbench.core.workspace;

import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Transaction-owned workspace state. Instances returned by stores are defensive copies. */
public final class WorkspaceState {

	private final UUID id;
	private final String name;
	private final String kind;
	private final JsonObject generator;
	private final JsonObject upstreamDocument;
	private final LinkedHashMap<UUID, Element> elements;
	private long revision;
	private boolean dirty;
	private long eventSequence;

	public WorkspaceState(UUID id, String name, String kind, long revision, boolean dirty, JsonObject generator,
			JsonObject upstreamDocument, List<Element> elements) {
		this(id, name, kind, revision, dirty, generator, upstreamDocument, elements, 0);
	}

	private WorkspaceState(UUID id, String name, String kind, long revision, boolean dirty, JsonObject generator,
			JsonObject upstreamDocument, List<Element> elements, long eventSequence) {
		this.id = Objects.requireNonNull(id);
		this.name = Objects.requireNonNull(name);
		this.kind = Objects.requireNonNull(kind);
		this.revision = revision;
		this.dirty = dirty;
		this.generator = Objects.requireNonNull(generator).deepCopy();
		this.upstreamDocument = upstreamDocument == null ? new JsonObject() : upstreamDocument.deepCopy();
		this.elements = new LinkedHashMap<>();
		for (Element element : elements)
			this.elements.put(element.id(), element.copy());
		this.eventSequence = eventSequence;
	}

	public WorkspaceState copy() {
		return new WorkspaceState(id, name, kind, revision, dirty, generator, upstreamDocument,
				new ArrayList<>(elements.values()), eventSequence);
	}

	public WorkspaceState withGenerator(JsonObject nextGenerator) {
		return new WorkspaceState(id, name, kind, revision, dirty, nextGenerator, upstreamDocument,
				new ArrayList<>(elements.values()), eventSequence);
	}

	public UUID id() { return id; }
	public String name() { return name; }
	public String kind() { return kind; }
	public long revision() { return revision; }
	public boolean dirty() { return dirty; }
	public JsonObject generator() { return generator.deepCopy(); }
	public JsonObject upstreamDocument() { return upstreamDocument.deepCopy(); }

	public List<Element> elements() {
		return elements.values().stream().map(Element::copy).toList();
	}

	public Element element(UUID elementId) {
		Element element = elements.get(elementId);
		return element == null ? null : element.copy();
	}

	public void addElement(Element element) {
		if (elements.containsKey(element.id()))
			throw new IllegalArgumentException("Element ID already exists");
		elements.put(element.id(), element.copy());
		dirty = true;
	}

	public void replaceElement(Element element) {
		if (!elements.containsKey(element.id()))
			throw new IllegalArgumentException("Element does not exist");
		elements.put(element.id(), element.copy());
		dirty = true;
	}

	public Element removeElement(UUID elementId) {
		Element removed = elements.remove(elementId);
		if (removed != null)
			dirty = true;
		return removed == null ? null : removed.copy();
	}

	public boolean hasElementName(String name) {
		return elements.values().stream().anyMatch(element -> element.name().equals(name));
	}

	public List<Element> recentElements(int limit) {
		return elements.values().stream().sorted(Comparator.comparing(Element::updatedAt).reversed()).limit(limit)
				.map(Element::copy).toList();
	}

	void committed(long newRevision) {
		revision = newRevision;
	}

	public long nextEventSequence() {
		return ++eventSequence;
	}

	public record Element(UUID id, String type, String name, String displayName, String state, String ownership,
			Instant updatedAt, JsonObject values) {
		public Element {
			Objects.requireNonNull(id);
			Objects.requireNonNull(type);
			Objects.requireNonNull(name);
			Objects.requireNonNull(displayName);
			Objects.requireNonNull(state);
			Objects.requireNonNull(ownership);
			Objects.requireNonNull(updatedAt);
			values = values == null ? new JsonObject() : values.deepCopy();
		}

		public Element copy() {
			return new Element(id, type, name, displayName, state, ownership, updatedAt, values);
		}
	}
}
