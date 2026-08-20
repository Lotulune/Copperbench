/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.window;

import org.junit.jupiter.api.Test;

import static dev.copperbench.window.WindowChromeHitTest.HitTarget;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WindowChromeHitTestTest {

	private static final WindowChromeHitTest.WindowBounds WINDOW =
			new WindowChromeHitTest.WindowBounds(100, 200, 1100, 900);

	@Test void interactiveRegionsOverrideTheContainingCaptionAndExposeSnapMaximize() {
		WindowChromeSnapshot snapshot = WindowChromeSnapshot.parse("""
				{"schemaVersion":"1.0","sequence":7,"coordinateSpace":"css_viewport","devicePixelRatio":1.0,
				 "viewport":{"width":1000,"height":700},"regions":[
				 {"id":"title","kind":"caption","bounds":{"x":0,"y":0,"width":1000,"height":48}},
				 {"id":"build","kind":"client","bounds":{"x":400,"y":8,"width":80,"height":32}},
				 {"id":"max","kind":"maximize","bounds":{"x":900,"y":0,"width":46,"height":48}},
				 {"id":"close","kind":"close","bounds":{"x":946,"y":0,"width":54,"height":48}}
				 ]}
				""");

		assertEquals(HitTarget.CAPTION, WindowChromeHitTest.hitTest(250, 220, WINDOW, 8, false, snapshot));
		assertEquals(HitTarget.CLIENT, WindowChromeHitTest.hitTest(520, 220, WINDOW, 8, false, snapshot));
		assertEquals(HitTarget.MAXIMIZE, WindowChromeHitTest.hitTest(1020, 220, WINDOW, 8, false, snapshot));
		assertEquals(HitTarget.CLOSE, WindowChromeHitTest.hitTest(1070, 220, WINDOW, 8, false, snapshot));
	}

	@Test void edgesAndCornersWinBeforeCaptionButAreDisabledWhenMaximized() {
		WindowChromeSnapshot snapshot = WindowChromeSnapshot.parse("""
				{"schemaVersion":"1.0","sequence":1,"coordinateSpace":"css_viewport","devicePixelRatio":1.0,
				 "viewport":{"width":1000,"height":700},"regions":[
				 {"id":"title","kind":"caption","bounds":{"x":0,"y":0,"width":1000,"height":48}}]}
				""");

		assertEquals(HitTarget.TOP_LEFT, WindowChromeHitTest.hitTest(102, 203, WINDOW, 8, false, snapshot));
		assertEquals(HitTarget.RIGHT, WindowChromeHitTest.hitTest(1098, 500, WINDOW, 8, false, snapshot));
		assertEquals(HitTarget.BOTTOM_RIGHT, WindowChromeHitTest.hitTest(1099, 899, WINDOW, 8, false, snapshot));
		assertEquals(HitTarget.CAPTION, WindowChromeHitTest.hitTest(102, 203, WINDOW, 8, true, snapshot));
	}

	@Test void cssCoordinatesScaleWithTheRendererDevicePixelRatio() {
		WindowChromeSnapshot snapshot = WindowChromeSnapshot.parse("""
				{"schemaVersion":"1.0","sequence":2,"coordinateSpace":"css_viewport","devicePixelRatio":2.0,
				 "viewport":{"width":500,"height":350},"regions":[
				 {"id":"max","kind":"maximize","bounds":{"x":400,"y":0,"width":50,"height":48}}]}
				""");

		assertEquals(HitTarget.MAXIMIZE, WindowChromeHitTest.hitTest(920, 240, WINDOW, 8, true, snapshot));
		assertEquals(HitTarget.CLIENT, WindowChromeHitTest.hitTest(700, 240, WINDOW, 8, true, snapshot));
	}

	@Test void rejectsMalformedOrDuplicateRegionSnapshots() {
		assertThrows(IllegalArgumentException.class, () -> WindowChromeSnapshot.parse("""
				{"schemaVersion":"2.0","sequence":0,"coordinateSpace":"css_viewport","devicePixelRatio":1,
				 "viewport":{"width":500,"height":400},"regions":[]}
				"""));
		assertThrows(IllegalArgumentException.class, () -> WindowChromeSnapshot.parse("""
				{"schemaVersion":"1.0","sequence":0,"coordinateSpace":"css_viewport","devicePixelRatio":1,
				 "viewport":{"width":500,"height":400},"regions":[
				 {"id":"same","kind":"caption","bounds":{"x":0,"y":0,"width":100,"height":40}},
				 {"id":"same","kind":"client","bounds":{"x":0,"y":0,"width":10,"height":10}}]}
				"""));
	}
}
