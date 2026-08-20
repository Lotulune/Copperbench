/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.window;

/** Platform-neutral non-client hit testing used by the Windows controller. */
public final class WindowChromeHitTest {

	private WindowChromeHitTest() {
	}

	public static HitTarget hitTest(int screenX, int screenY, WindowBounds window, int resizeBorder,
			boolean maximized, WindowChromeSnapshot snapshot) {
		if (!maximized) {
			boolean left = screenX >= window.left() && screenX < window.left() + resizeBorder;
			boolean right = screenX < window.right() && screenX >= window.right() - resizeBorder;
			boolean top = screenY >= window.top() && screenY < window.top() + resizeBorder;
			boolean bottom = screenY < window.bottom() && screenY >= window.bottom() - resizeBorder;
			if (top && left)
				return HitTarget.TOP_LEFT;
			if (top && right)
				return HitTarget.TOP_RIGHT;
			if (bottom && left)
				return HitTarget.BOTTOM_LEFT;
			if (bottom && right)
				return HitTarget.BOTTOM_RIGHT;
			if (left)
				return HitTarget.LEFT;
			if (right)
				return HitTarget.RIGHT;
			if (top)
				return HitTarget.TOP;
			if (bottom)
				return HitTarget.BOTTOM;
		}

		if (snapshot == null)
			return HitTarget.CLIENT;
		double cssX = (screenX - window.left()) / snapshot.devicePixelRatio();
		double cssY = (screenY - window.top()) / snapshot.devicePixelRatio();
		return switch (snapshot.targetAt(cssX, cssY)) {
			case CAPTION -> HitTarget.CAPTION;
			case MINIMIZE -> HitTarget.MINIMIZE;
			case MAXIMIZE -> HitTarget.MAXIMIZE;
			case CLOSE -> HitTarget.CLOSE;
			case CLIENT -> HitTarget.CLIENT;
		};
	}

	public record WindowBounds(int left, int top, int right, int bottom) {
		public WindowBounds {
			if (right <= left || bottom <= top)
				throw new IllegalArgumentException("Invalid native window bounds");
		}
	}

	public enum HitTarget {
		CLIENT, CAPTION, MINIMIZE, MAXIMIZE, CLOSE,
		LEFT, RIGHT, TOP, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
	}
}
