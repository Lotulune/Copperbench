/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.shell;

/** Product-shell default for packaged and gradle runs. Tests leave the property unset. */
public final class ProductShellSettings {

	public static final String PROPERTY = "copperbench.productShell";

	private ProductShellSettings() {
	}

	public static boolean enabled() {
		return Boolean.parseBoolean(System.getProperty(PROPERTY, "false"));
	}
}
