/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.core.workspace;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * MCreator user code blocks: the named regions of a generated Java file that regeneration preserves.
 * Generation fingerprints and source edits share this definition, so an edit that only changes block contents
 * never looks like a changed generated file.
 */
public final class UserCodeRegions {
	public static final String START = "// Start of user code block ";
	public static final String END = "// End of user code block ";

	private UserCodeRegions() {}

	/**
	 * @param skeleton the file text outside user code blocks, with the marker lines kept
	 * @param names    the block names in file order
	 */
	public record Parsed(String skeleton, List<String> names) {}

	/** @throws InvalidRegions when a block is nested, unnamed, repeated, mismatched or unclosed */
	public static Parsed parse(String content) {
		StringBuilder skeleton = new StringBuilder();
		String active = null;
		Set<String> names = new LinkedHashSet<>();
		for (String line : content.split("(?<=\\n)", -1)) {
			String trimmed = line.strip();
			if (trimmed.startsWith(START)) {
				String name = trimmed.substring(START.length()).strip();
				if (active != null || name.isEmpty() || !names.add(name))
					throw new InvalidRegions("ambiguous user-code region");
				active = name;
				skeleton.append(line);
			} else if (trimmed.startsWith(END)) {
				if (active == null || !trimmed.equals(END + active))
					throw new InvalidRegions("mismatched user-code region");
				active = null;
				skeleton.append(line);
			} else if (active == null) {
				skeleton.append(line);
			}
		}
		if (active != null)
			throw new InvalidRegions("unclosed user-code region");
		return new Parsed(skeleton.toString(), List.copyOf(names));
	}

	public static final class InvalidRegions extends IllegalArgumentException {
		InvalidRegions(String message) {
			super(message);
		}
	}
}
