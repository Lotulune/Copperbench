/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.platform;

/**
 * Explicit opt-in for graphical product-shell CI runs.
 *
 * <p>Normal GitHub Actions jobs keep Chromium headless. A graphical runner
 * such as the Stage 15 Xvfb compatibility smoke can set the property to keep
 * Chromium windowed while retaining the rest of the CI software-rendering and
 * sandbox flags.</p>
 */
public final class GraphicalCiMode {

	public static final String PROPERTY = "copperbench.graphicalCi";

	private GraphicalCiMode() {
	}

	public static boolean chromiumHeadlessRequired() {
		return chromiumHeadlessRequired("true".equals(System.getenv("GITHUB_ACTIONS")),
				Boolean.getBoolean(PROPERTY));
	}

	static boolean chromiumHeadlessRequired(boolean githubActions, boolean graphicalCi) {
		return githubActions && !graphicalCi;
	}
}
