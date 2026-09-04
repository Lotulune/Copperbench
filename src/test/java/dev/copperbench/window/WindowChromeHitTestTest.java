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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

	@Test void resizeInteractionAndCursorHintUseSeparateDpiScaledTargets() {
		assertTrue(WindowsWindowChromeController.resizeHitTargetForDpi(96) >= 10);
		assertTrue(WindowsWindowChromeController.resizeHitTargetForDpi(120) >= 13);
		assertTrue(WindowsWindowChromeController.resizeHitTargetForDpi(144) >= 15);
		assertTrue(WindowsWindowChromeController.resizeCursorHintTargetForDpi(96) >= 50);
		assertTrue(WindowsWindowChromeController.resizeCursorHintTargetForDpi(120) >= 63);
		assertTrue(WindowsWindowChromeController.resizeCursorHintTargetForDpi(144) >= 75);
		assertTrue(WindowsWindowChromeController.resizeCursorHintTargetForDpi(120)
				> WindowsWindowChromeController.resizeHitTargetForDpi(120));
	}

	@Test void cursorHintTracksTheVisibleEdgeSymmetrically() {
		assertEquals(10, WindowsWindowChromeController.cursorHintHitForBounds(105, 500, WINDOW, 12, 12));
		assertEquals(11, WindowsWindowChromeController.cursorHintHitForBounds(1094, 500, WINDOW, 12, 12));
		assertEquals(12, WindowsWindowChromeController.cursorHintHitForBounds(500, 205, WINDOW, 12, 12));
		assertEquals(15, WindowsWindowChromeController.cursorHintHitForBounds(500, 894, WINDOW, 12, 12));
		assertEquals(1, WindowsWindowChromeController.cursorHintHitForBounds(500, 220, WINDOW, 12, 12));
	}

	@Test void cursorHintVisibleBoundsCanBeShiftedTowardOuterFrame() {
		WindowChromeHitTest.WindowBounds shifted = new WindowChromeHitTest.WindowBounds(60, 160, 1140, 940);
		assertEquals(10, WindowsWindowChromeController.cursorHintHitForBounds(109, 500, shifted, 50, 50));
		assertEquals(11, WindowsWindowChromeController.cursorHintHitForBounds(1090, 500, shifted, 50, 50));
		assertEquals(15, WindowsWindowChromeController.cursorHintHitForBounds(500, 890, shifted, 50, 50));
		assertEquals(12, WindowsWindowChromeController.cursorHintHitForBounds(500, 209, shifted, 50, 50));
		assertEquals(1, WindowsWindowChromeController.cursorHintHitForBounds(110, 500, shifted, 50, 50));
		assertEquals(1, WindowsWindowChromeController.cursorHintHitForBounds(500, 210, shifted, 50, 50));
	}

	@Test void widenedCursorHintHasSubstantialInteriorReachFromTheAlignedEdge() {
		assertEquals(10, WindowsWindowChromeController.cursorHintHitForBounds(114, 500, WINDOW, 20, 20));
		assertEquals(11, WindowsWindowChromeController.cursorHintHitForBounds(1085, 500, WINDOW, 20, 20));
		assertEquals(12, WindowsWindowChromeController.cursorHintHitForBounds(500, 214, WINDOW, 20, 20));
		assertEquals(15, WindowsWindowChromeController.cursorHintHitForBounds(500, 885, WINDOW, 20, 20));
		assertEquals(1, WindowsWindowChromeController.cursorHintHitForBounds(500, 221, WINDOW, 20, 20));
	}

	@Test void resizeHitsMapToStandardWindowsCursors() {
		assertEquals(32644, WindowsWindowChromeController.resizeCursorResourceForHit(10));
		assertEquals(32644, WindowsWindowChromeController.resizeCursorResourceForHit(11));
		assertEquals(32645, WindowsWindowChromeController.resizeCursorResourceForHit(12));
		assertEquals(32645, WindowsWindowChromeController.resizeCursorResourceForHit(15));
		assertEquals(32642, WindowsWindowChromeController.resizeCursorResourceForHit(13));
		assertEquals(32642, WindowsWindowChromeController.resizeCursorResourceForHit(17));
		assertEquals(32643, WindowsWindowChromeController.resizeCursorResourceForHit(14));
		assertEquals(32643, WindowsWindowChromeController.resizeCursorResourceForHit(16));
		assertEquals(0, WindowsWindowChromeController.resizeCursorResourceForHit(2));
	}

	@Test void childNativeChromeProxyOnlyClaimsCaptionAndResizeHits() {
		assertTrue(WindowsWindowChromeController.isChildNativeChromeHit(2));
		for (int hit = 10; hit <= 17; hit++)
			assertTrue(WindowsWindowChromeController.isChildNativeChromeHit(hit));
		assertFalse(WindowsWindowChromeController.isChildNativeChromeHit(1));
		assertFalse(WindowsWindowChromeController.isChildNativeChromeHit(8));
		assertFalse(WindowsWindowChromeController.isChildNativeChromeHit(9));
		assertFalse(WindowsWindowChromeController.isChildNativeChromeHit(20));
	}

	@Test void resizeHitsMapToWin32SystemSizeCommands() {
		for (int hit = 10; hit <= 17; hit++) {
			assertTrue(WindowsWindowChromeController.isResizeHit(hit));
			assertEquals(0xF001 + (hit - 10), WindowsWindowChromeController.systemSizeCommandForHit(hit));
		}
		assertFalse(WindowsWindowChromeController.isResizeHit(2));
		assertThrows(IllegalArgumentException.class,
				() -> WindowsWindowChromeController.systemSizeCommandForHit(2));
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
