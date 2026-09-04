/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.window;

import com.sun.jna.CallbackReference;
import com.sun.jna.CallbackThreadInitializer;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;

import static dev.copperbench.window.WindowChromeHitTest.HitTarget;

/** Owns the Win32 non-client contract for the React product-shell title bar. */
public final class WindowsWindowChromeController implements AutoCloseable {

	private static final Logger LOG = LogManager.getLogger(WindowsWindowChromeController.class);

	private static final int GWL_WNDPROC = -4;
	private static final int GWL_STYLE = -16;
	private static final int WS_THICKFRAME = 0x00040000;
	private static final int WS_MINIMIZEBOX = 0x00020000;
	private static final int WS_MAXIMIZEBOX = 0x00010000;
	private static final int WS_SYSMENU = 0x00080000;

	private static final int WM_GETMINMAXINFO = 0x0024;
	private static final int WM_CANCELMODE = 0x001F;
	private static final int WM_SETCURSOR = 0x0020;
	private static final int WM_MOUSEMOVE = 0x0200;
	private static final int WM_NCCALCSIZE = 0x0083;
	private static final int WM_NCHITTEST = 0x0084;
	private static final int WM_NCLBUTTONDOWN = 0x00A1;
	private static final int WM_NCLBUTTONDBLCLK = 0x00A3;
	private static final int WM_NCRBUTTONUP = 0x00A5;
	private static final int WM_SYSKEYDOWN = 0x0104;
	private static final int WM_SYSCOMMAND = 0x0112;
	private static final int WM_DPICHANGED = 0x02E0;
	private static final int VK_SPACE = 0x20;
	private static final int SC_SIZE = 0xF000;
	private static final int IDC_SIZENWSE = 32642;
	private static final int IDC_SIZENESW = 32643;
	private static final int IDC_SIZEWE = 32644;
	private static final int IDC_SIZENS = 32645;
	private static final int IDC_ARROW = 32512;

	private static final int SM_CXSIZEFRAME = 32;
	private static final int SM_CYSIZEFRAME = 33;
	private static final int SM_CXPADDEDBORDER = 92;
	private static final int MONITOR_DEFAULTTONEAREST = 2;
	private static final int TPM_RIGHTBUTTON = 0x0002;
	private static final int TPM_RETURNCMD = 0x0100;
	private static final int SWP_NOSIZE = 0x0001;
	private static final int SWP_NOMOVE = 0x0002;
	private static final int SWP_NOZORDER = 0x0004;
	private static final int SWP_FRAMECHANGED = 0x0020;
	private static final int MINIMUM_WIDTH_CSS = 500;
	private static final int MINIMUM_HEIGHT_CSS = 600;
	private static final int RESIZE_HIT_TARGET_CSS = 10;
	private static final int RESIZE_CURSOR_HINT_WIDTH_CSS = 50;
	private static final int RESIZE_CURSOR_EDGE_OUTSET_CSS = 40;

	private final JFrame window;
	private final AtomicReference<WindowChromeSnapshot> chromeSnapshot = new AtomicReference<>();
	private final AtomicBoolean customFrame = new AtomicBoolean(true);
	private final AtomicBoolean fallbackScheduled = new AtomicBoolean(false);
	private final WindowProc windowProc = this::windowProc;
	private final WindowProc childWindowProc = this::childWindowProc;
	private final Map<Long, Pointer> previousChildWindowProcs = new ConcurrentHashMap<>();
	private final AtomicBoolean resizeCursorHintActive = new AtomicBoolean(false);

	private Pointer hwnd;
	private Pointer previousWindowProc;
	private boolean installed;
	private volatile int dpi = 96;

	private WindowsWindowChromeController(JFrame window) {
		this.window = window;
		Native.setCallbackThreadInitializer(childWindowProc,
				new CallbackThreadInitializer(true, false, "Copperbench-JCEF-window-chrome"));
	}

	static int resizeHitTargetForDpi(int currentDpi) {
		int systemBorder = Math.max(systemMetricForDpi(SM_CXSIZEFRAME, currentDpi),
				systemMetricForDpi(SM_CYSIZEFRAME, currentDpi)) + systemMetricForDpi(SM_CXPADDEDBORDER, currentDpi);
		return Math.max(Math.max(1, systemBorder), scaleForDpi(RESIZE_HIT_TARGET_CSS, currentDpi));
	}

	static int resizeCursorHintTargetForDpi(int currentDpi) {
		return Math.max(resizeHitTargetForDpi(currentDpi), scaleForDpi(RESIZE_CURSOR_HINT_WIDTH_CSS, currentDpi));
	}

	int childHookCountForTesting() {
		return previousChildWindowProcs.size();
	}

	private void refreshChildWindowHooks() {
		Pointer parent = hwnd;
		if (parent == null || !installed || !customFrame.get())
			return;
		long currentPid = ProcessHandle.current().pid();
		User32.INSTANCE.EnumChildWindows(parent, (child, data) -> {
			if (child == null)
				return true;
			IntByReference processId = new IntByReference();
			User32.INSTANCE.GetWindowThreadProcessId(child, processId);
			if (Integer.toUnsignedLong(processId.getValue()) != currentPid)
				return true;
			long key = Pointer.nativeValue(child);
			if (previousChildWindowProcs.containsKey(key))
				return true;
			Native.setLastError(0);
			Pointer previous = User32.INSTANCE.SetWindowLongPtrW(child, GWL_WNDPROC,
					CallbackReference.getFunctionPointer(childWindowProc));
			if (previous == null && Native.getLastError() != 0) {
				LOG.debug("Could not subclass child HWND 0x{}: error {}", Long.toHexString(key), Native.getLastError());
				return true;
			}
			previousChildWindowProcs.put(key, previous);
			LOG.debug("Subclassed product-shell child HWND 0x{} for native chrome input", Long.toHexString(key));
			return true;
		}, null);
	}

	private long childWindowProc(Pointer callbackHwnd, int message, long wParam, long lParam) {
		try {
			if (message == WM_NCHITTEST) {
				int parentHit = childHitTest(lParam);
				return isChildNativeChromeHit(parentHit) ? parentHit : NativeHit.HTCLIENT.value;
			}
			if (message == WM_SETCURSOR) {
				if (applyResizeCursorHint())
					return 1;
			}
			if (message == WM_MOUSEMOVE) {
				long result = callPreviousChild(callbackHwnd, message, wParam, lParam);
				applyResizeCursorHint();
				return result;
			}
			if (message == WM_NCLBUTTONDOWN && isResizeHit((int) wParam) && hwnd != null) {
				User32.INSTANCE.ReleaseCapture();
				User32.INSTANCE.PostMessageW(hwnd, WM_SYSCOMMAND, systemSizeCommandForHit((int) wParam), lParam);
				return 0;
			}
			if ((message == WM_NCLBUTTONDOWN || message == WM_NCLBUTTONDBLCLK)
					&& wParam == NativeHit.HTCAPTION.value && hwnd != null) {
				User32.INSTANCE.ReleaseCapture();
				User32.INSTANCE.PostMessageW(hwnd, message, wParam, lParam);
				return 0;
			}
			if (message == WM_NCRBUTTONUP && wParam == NativeHit.HTCAPTION.value && hwnd != null) {
				User32.INSTANCE.PostMessageW(hwnd, message, wParam, lParam);
				return 0;
			}
			return callPreviousChild(callbackHwnd, message, wParam, lParam);
		} catch (Throwable exception) {
			LOG.error("Windows product-shell child chrome failed while processing message 0x{}",
					Integer.toHexString(message), exception);
			return callPreviousChild(callbackHwnd, message, wParam, lParam);
		}
	}

	private int childHitTest(long lParam) {
		Pointer parent = hwnd;
		if (parent == null || !installed || !customFrame.get())
			return NativeHit.HTCLIENT.value;
		return childHitTestAt(screenPoint(lParam), resizeHitTargetForDpi(getDpi(parent)));
	}

	private int childCursorHintHit() {
		Pointer parent = hwnd;
		if (parent == null || !installed || !customFrame.get())
			return NativeHit.HTCLIENT.value;
		Point point = new Point();
		if (!User32.INSTANCE.GetCursorPos(point))
			return NativeHit.HTCLIENT.value;
		return childCursorHintHitAt(point, getDpi(parent));
	}

	private boolean applyResizeCursorHint() {
		int cursorResource = resizeCursorResourceForHit(childCursorHintHit());
		if (cursorResource != 0) {
			Pointer cursor = User32.INSTANCE.LoadCursorW(null, new Pointer(cursorResource));
			if (cursor != null) {
				resizeCursorHintActive.set(true);
				User32.INSTANCE.SetCursor(cursor);
				return true;
			}
		}
		if (resizeCursorHintActive.getAndSet(false)) {
			Pointer arrow = User32.INSTANCE.LoadCursorW(null, new Pointer(IDC_ARROW));
			if (arrow != null)
				User32.INSTANCE.SetCursor(arrow);
		}
		return false;
	}

	private int childCursorHintHitAt(Point point, int currentDpi) {
		Pointer parent = hwnd;
		if (parent == null || !installed || !customFrame.get() || User32.INSTANCE.IsZoomed(parent))
			return NativeHit.HTCLIENT.value;
		Rect clientRect = new Rect();
		if (!User32.INSTANCE.GetClientRect(parent, clientRect))
			return NativeHit.HTCLIENT.value;
		Point clientOrigin = new Point();
		if (!User32.INSTANCE.ClientToScreen(parent, clientOrigin))
			return NativeHit.HTCLIENT.value;
		int edgeOutset = scaleForDpi(RESIZE_CURSOR_EDGE_OUTSET_CSS, currentDpi);
		WindowChromeHitTest.WindowBounds visibleBounds = new WindowChromeHitTest.WindowBounds(
				clientOrigin.x - edgeOutset,
				clientOrigin.y - edgeOutset,
				clientOrigin.x + clientRect.right + edgeOutset,
				clientOrigin.y + clientRect.bottom + edgeOutset);
		return cursorHintHitForBounds(point.x, point.y,
				visibleBounds,
				resizeCursorHintTargetForDpi(currentDpi), resizeCursorHintTargetForDpi(currentDpi));
	}

	static int cursorHintHitForBounds(int screenX, int screenY, WindowChromeHitTest.WindowBounds bounds,
			int sideAndBottomHint, int topHint) {
		int leftDistance = screenX - bounds.left();
		int rightDistance = bounds.right() - 1 - screenX;
		int topDistance = screenY - bounds.top();
		int bottomDistance = bounds.bottom() - 1 - screenY;
		if (leftDistance < 0 || rightDistance < 0 || topDistance < 0 || bottomDistance < 0)
			return NativeHit.HTCLIENT.value;

		boolean left = leftDistance < sideAndBottomHint;
		boolean right = rightDistance < sideAndBottomHint;
		boolean top = topDistance < topHint;
		boolean bottom = bottomDistance < sideAndBottomHint;
		if (top && left)
			return NativeHit.HTTOPLEFT.value;
		if (top && right)
			return NativeHit.HTTOPRIGHT.value;
		if (bottom && left)
			return NativeHit.HTBOTTOMLEFT.value;
		if (bottom && right)
			return NativeHit.HTBOTTOMRIGHT.value;
		if (left)
			return NativeHit.HTLEFT.value;
		if (right)
			return NativeHit.HTRIGHT.value;
		if (top)
			return NativeHit.HTTOP.value;
		if (bottom)
			return NativeHit.HTBOTTOM.value;
		return NativeHit.HTCLIENT.value;
	}

	private int childHitTestAt(Point point, int resizeBorder) {
		Pointer parent = hwnd;
		if (parent == null || !installed || !customFrame.get())
			return NativeHit.HTCLIENT.value;
		Rect rect = new Rect();
		if (!User32.INSTANCE.GetWindowRect(parent, rect))
			return NativeHit.HTCLIENT.value;
		HitTarget target = WindowChromeHitTest.hitTest(point.x, point.y,
				new WindowChromeHitTest.WindowBounds(rect.left, rect.top, rect.right, rect.bottom),
				Math.max(1, resizeBorder), User32.INSTANCE.IsZoomed(parent), chromeSnapshot.get());
		return NativeHit.from(target).value;
	}

	static boolean isChildNativeChromeHit(int hit) {
		return hit == NativeHit.HTCAPTION.value || isResizeHit(hit);
	}

	static boolean isResizeHit(int hit) {
		return hit == NativeHit.HTLEFT.value || hit == NativeHit.HTRIGHT.value || hit == NativeHit.HTTOP.value
				|| hit == NativeHit.HTBOTTOM.value || hit == NativeHit.HTTOPLEFT.value
				|| hit == NativeHit.HTTOPRIGHT.value || hit == NativeHit.HTBOTTOMLEFT.value
				|| hit == NativeHit.HTBOTTOMRIGHT.value;
	}

	static int systemSizeCommandForHit(int hit) {
		return switch (hit) {
			case 10 -> SC_SIZE + 1; // WMSZ_LEFT
			case 11 -> SC_SIZE + 2; // WMSZ_RIGHT
			case 12 -> SC_SIZE + 3; // WMSZ_TOP
			case 13 -> SC_SIZE + 4; // WMSZ_TOPLEFT
			case 14 -> SC_SIZE + 5; // WMSZ_TOPRIGHT
			case 15 -> SC_SIZE + 6; // WMSZ_BOTTOM
			case 16 -> SC_SIZE + 7; // WMSZ_BOTTOMLEFT
			case 17 -> SC_SIZE + 8; // WMSZ_BOTTOMRIGHT
			default -> throw new IllegalArgumentException("Not a resize hit: " + hit);
		};
	}

	static int resizeCursorResourceForHit(int hit) {
		return switch (hit) {
			case 10, 11 -> IDC_SIZEWE;
			case 12, 15 -> IDC_SIZENS;
			case 13, 17 -> IDC_SIZENWSE;
			case 14, 16 -> IDC_SIZENESW;
			default -> 0;
		};
	}

	private long callPreviousChild(Pointer callbackHwnd, int message, long wParam, long lParam) {
		Pointer previous = previousChildWindowProcs.get(Pointer.nativeValue(callbackHwnd));
		return previous != null ? User32.INSTANCE.CallWindowProcW(previous, callbackHwnd, message, wParam, lParam)
				: User32.INSTANCE.DefWindowProcW(callbackHwnd, message, wParam, lParam);
	}

	@Nullable public static WindowsWindowChromeController prepare(JFrame window) {
		if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")
				|| Boolean.getBoolean("copperbench.systemWindowFrame"))
			return null;
		if (window.isDisplayable())
			throw new IllegalStateException("Window chrome must be prepared before the JFrame becomes displayable");
		window.setUndecorated(true);
		window.setMinimumSize(new Dimension(MINIMUM_WIDTH_CSS, MINIMUM_HEIGHT_CSS));
		return new WindowsWindowChromeController(window);
	}

	public synchronized boolean install() {
		if (installed)
			return true;
		if (!customFrame.get())
			return false;
		try {
			if (!window.isDisplayable())
				throw new IllegalStateException("Window must be displayable before native chrome installation");
			hwnd = Native.getComponentPointer(window);
			if (hwnd == null)
				throw new IllegalStateException("AWT did not expose a native window handle");

			int style = User32.INSTANCE.GetWindowLongW(hwnd, GWL_STYLE);
			style |= WS_THICKFRAME | WS_MINIMIZEBOX | WS_MAXIMIZEBOX | WS_SYSMENU;
			User32.INSTANCE.SetWindowLongW(hwnd, GWL_STYLE, style);

			Native.setLastError(0);
			previousWindowProc = User32.INSTANCE.SetWindowLongPtrW(hwnd, GWL_WNDPROC,
					CallbackReference.getFunctionPointer(windowProc));
			if (previousWindowProc == null && Native.getLastError() != 0)
				throw new IllegalStateException("SetWindowLongPtrW failed with error " + Native.getLastError());

			dpi = getDpi(hwnd);
			if (!User32.INSTANCE.SetWindowPos(hwnd, null, 0, 0, 0, 0,
					SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_FRAMECHANGED))
				throw new IllegalStateException("SetWindowPos failed with error " + Native.getLastError());
			installed = true;
			LOG.info("Installed Windows product-shell chrome at {} DPI", dpi);
			return true;
		} catch (Throwable exception) {
			LOG.error("Failed to install Windows product-shell chrome; restoring the system frame", exception);
			fallbackToSystemFrame();
			return false;
		}
	}

	public void accept(WindowChromeSnapshot snapshot) {
		if (!customFrame.get())
			return;
		chromeSnapshot.accumulateAndGet(snapshot,
				(current, update) -> current == null || update.sequence() > current.sequence() ? update : current);
		refreshChildWindowHooks();
	}

	public boolean isUsingCustomFrame() {
		return customFrame.get();
	}

	@Nullable WindowChromeSnapshot chromeSnapshotForTesting() {
		return chromeSnapshot.get();
	}

	WindowChromeHitTest.WindowBounds nativeBoundsForTesting() {
		Rect rect = new Rect();
		if (hwnd == null || !User32.INSTANCE.GetWindowRect(hwnd, rect))
			throw new IllegalStateException("Native window bounds are unavailable");
		return new WindowChromeHitTest.WindowBounds(rect.left, rect.top, rect.right, rect.bottom);
	}

	double devicePixelRatioForTesting() {
		return dpi / 96.0;
	}

	int nativeHitTestForTesting(int screenX, int screenY) {
		if (hwnd == null || !installed)
			throw new IllegalStateException("Native window chrome is not installed");
		long packedPoint = ((long) (screenY & 0xffff) << 16) | (screenX & 0xffffL);
		return (int) User32.INSTANCE.SendMessageW(hwnd, WM_NCHITTEST, 0, packedPoint);
	}

	void openSystemMenuForTesting() {
		if (hwnd == null || !installed)
			throw new IllegalStateException("Native window chrome is not installed");
		User32.INSTANCE.SendMessageW(hwnd, WM_SYSKEYDOWN, VK_SPACE, 0);
	}

	void closeSystemMenuForTesting() {
		if (hwnd != null)
			User32.INSTANCE.PostMessageW(hwnd, WM_CANCELMODE, 0, 0);
	}

	void applyDpiChangeForTesting(int newDpi, WindowChromeHitTest.WindowBounds suggestedBounds) {
		if (hwnd == null || !installed)
			throw new IllegalStateException("Native window chrome is not installed");
		Rect suggested = new Rect();
		suggested.left = suggestedBounds.left();
		suggested.top = suggestedBounds.top();
		suggested.right = suggestedBounds.right();
		suggested.bottom = suggestedBounds.bottom();
		suggested.write();
		long packedDpi = ((long) (newDpi & 0xffff) << 16) | (newDpi & 0xffffL);
		User32.INSTANCE.SendMessageW(hwnd, WM_DPICHANGED, packedDpi, Pointer.nativeValue(suggested.getPointer()));
	}

	void doubleClickCaptionForTesting(int screenX, int screenY) {
		if (hwnd == null || !installed)
			throw new IllegalStateException("Native window chrome is not installed");
		long packedPoint = ((long) (screenY & 0xffff) << 16) | (screenX & 0xffffL);
		User32.INSTANCE.SendMessageW(hwnd, WM_NCLBUTTONDBLCLK, NativeHit.HTCAPTION.value, packedPoint);
	}

	boolean isMaximizedForTesting() {
		return hwnd != null && User32.INSTANCE.IsZoomed(hwnd);
	}

	public void beginDrag() {
		if (hwnd == null || !installed || !customFrame.get())
			return;
		Runnable drag = () -> {
			if (hwnd == null || !installed || !customFrame.get())
				return;
			User32.INSTANCE.ReleaseCapture();
			Point point = new Point();
			long packedPoint = 0;
			if (User32.INSTANCE.GetCursorPos(point)) {
				packedPoint = ((long) (point.y & 0xffff) << 16) | (point.x & 0xffffL);
			}
			User32.INSTANCE.SendMessageW(hwnd, WM_NCLBUTTONDOWN, NativeHit.HTCAPTION.value, packedPoint);
		};
		if (SwingUtilities.isEventDispatchThread())
			drag.run();
		else
			SwingUtilities.invokeLater(drag);
	}

	public void fallbackToSystemFrame() {
		if (!customFrame.compareAndSet(true, false))
			return;
		Runnable fallback = () -> {
			Rectangle bounds = window.getBounds();
			int extendedState = window.getExtendedState();
			boolean visible = window.isVisible();
			restoreWindowProc();
			window.dispose();
			window.setUndecorated(false);
			window.setMinimumSize(new Dimension(MINIMUM_WIDTH_CSS, MINIMUM_HEIGHT_CSS));
			window.setBounds(bounds);
			if (visible) {
				window.setVisible(true);
				window.setExtendedState(extendedState);
			}
			LOG.warn("Windows product-shell chrome fell back to the system window frame");
		};
		if (SwingUtilities.isEventDispatchThread())
			fallback.run();
		else
			SwingUtilities.invokeLater(fallback);
	}

	private long windowProc(Pointer callbackHwnd, int message, long wParam, long lParam) {
		try {
			return switch (message) {
				case WM_NCCALCSIZE -> wParam != 0 ? 0 : callPrevious(callbackHwnd, message, wParam, lParam);
				case WM_NCHITTEST -> hitTest(callbackHwnd, message, wParam, lParam);
				case WM_GETMINMAXINFO -> applyMinMaxInfo(callbackHwnd, lParam);
				case WM_DPICHANGED -> applyDpiChange(callbackHwnd, wParam, lParam);
				case WM_SYSKEYDOWN -> wParam == VK_SPACE ? showSystemMenu(callbackHwnd, null) :
						callPrevious(callbackHwnd, message, wParam, lParam);
				case WM_NCRBUTTONUP -> wParam == NativeHit.HTCAPTION.value
						? showSystemMenu(callbackHwnd, screenPoint(lParam))
						: callPrevious(callbackHwnd, message, wParam, lParam);
				default -> callPrevious(callbackHwnd, message, wParam, lParam);
			};
		} catch (Throwable exception) {
			LOG.error("Windows product-shell chrome failed while processing message 0x{}",
					Integer.toHexString(message), exception);
			scheduleFallback();
			return callPrevious(callbackHwnd, message, wParam, lParam);
		}
	}

	private long hitTest(Pointer callbackHwnd, int message, long wParam, long lParam) {
		LongByReference dwmResult = new LongByReference();
		try {
			if (DwmApi.INSTANCE.DwmDefWindowProc(callbackHwnd, message, wParam, lParam, dwmResult))
				return dwmResult.getValue();
		} catch (UnsatisfiedLinkError ignored) {
		}

		Rect rect = new Rect();
		if (!User32.INSTANCE.GetWindowRect(callbackHwnd, rect))
			return callPrevious(callbackHwnd, message, wParam, lParam);
		Point point = screenPoint(lParam);
		int currentDpi = getDpi(callbackHwnd);
		int resizeBorder = resizeHitTargetForDpi(currentDpi);
		HitTarget target = WindowChromeHitTest.hitTest(point.x, point.y,
				new WindowChromeHitTest.WindowBounds(rect.left, rect.top, rect.right, rect.bottom),
				Math.max(1, resizeBorder), User32.INSTANCE.IsZoomed(callbackHwnd), chromeSnapshot.get());
		return NativeHit.from(target).value;
	}

	private long applyMinMaxInfo(Pointer callbackHwnd, long lParam) {
		MinMaxInfo info = new MinMaxInfo(new Pointer(lParam));
		int currentDpi = getDpi(callbackHwnd);
		info.minTrackSize.x = scaleForDpi(MINIMUM_WIDTH_CSS, currentDpi);
		info.minTrackSize.y = scaleForDpi(MINIMUM_HEIGHT_CSS, currentDpi);

		Pointer monitor = User32.INSTANCE.MonitorFromWindow(callbackHwnd, MONITOR_DEFAULTTONEAREST);
		if (monitor != null) {
			MonitorInfo monitorInfo = new MonitorInfo();
			if (User32.INSTANCE.GetMonitorInfoW(monitor, monitorInfo)) {
				info.maxPosition.x = monitorInfo.work.left - monitorInfo.monitor.left;
				info.maxPosition.y = monitorInfo.work.top - monitorInfo.monitor.top;
				info.maxSize.x = monitorInfo.work.right - monitorInfo.work.left;
				info.maxSize.y = monitorInfo.work.bottom - monitorInfo.work.top;
			}
		}
		info.write();
		return 0;
	}

	private long applyDpiChange(Pointer callbackHwnd, long wParam, long lParam) {
		dpi = (int) (wParam & 0xffff);
		Rect suggested = new Rect(new Pointer(lParam));
		User32.INSTANCE.SetWindowPos(callbackHwnd, null, suggested.left, suggested.top,
				suggested.right - suggested.left, suggested.bottom - suggested.top, SWP_NOZORDER);
		return 0;
	}

	private long showSystemMenu(Pointer callbackHwnd, @Nullable Point point) {
		Pointer menu = User32.INSTANCE.GetSystemMenu(callbackHwnd, false);
		if (menu == null)
			return 0;
		if (point == null) {
			Rect rect = new Rect();
			User32.INSTANCE.GetWindowRect(callbackHwnd, rect);
			point = new Point(rect.left + scaleForDpi(12, dpi), rect.top + scaleForDpi(12, dpi));
		}
		int command = User32.INSTANCE.TrackPopupMenu(menu, TPM_RIGHTBUTTON | TPM_RETURNCMD,
				point.x, point.y, 0, callbackHwnd, null);
		if (command != 0)
			User32.INSTANCE.PostMessageW(callbackHwnd, WM_SYSCOMMAND, command, 0);
		return 0;
	}

	private long callPrevious(Pointer callbackHwnd, int message, long wParam, long lParam) {
		Pointer previous = previousWindowProc;
		return previous != null ? User32.INSTANCE.CallWindowProcW(previous, callbackHwnd, message, wParam, lParam)
				: User32.INSTANCE.DefWindowProcW(callbackHwnd, message, wParam, lParam);
	}

	private void scheduleFallback() {
		if (fallbackScheduled.compareAndSet(false, true))
			SwingUtilities.invokeLater(this::fallbackToSystemFrame);
	}

	private synchronized void restoreWindowProc() {
		for (Map.Entry<Long, Pointer> entry : previousChildWindowProcs.entrySet()) {
			Pointer child = new Pointer(entry.getKey());
			if (User32.INSTANCE.IsWindow(child)) {
				try {
					User32.INSTANCE.SetWindowLongPtrW(child, GWL_WNDPROC, entry.getValue());
				} catch (Throwable exception) {
					LOG.debug("Could not restore child Win32 window procedure for 0x{}",
							Long.toHexString(entry.getKey()), exception);
				}
			}
		}
		previousChildWindowProcs.clear();
		if (hwnd != null && previousWindowProc != null && window.isDisplayable()) {
			try {
				User32.INSTANCE.SetWindowLongPtrW(hwnd, GWL_WNDPROC, previousWindowProc);
			} catch (Throwable exception) {
				LOG.warn("Could not restore the original Win32 window procedure", exception);
			}
		}
		installed = false;
		previousWindowProc = null;
		hwnd = null;
	}

	@Override public void close() {
		customFrame.set(false);
		if (SwingUtilities.isEventDispatchThread())
			restoreWindowProc();
		else
			SwingUtilities.invokeLater(this::restoreWindowProc);
	}

	private static int getDpi(Pointer hwnd) {
		try {
			int value = User32.INSTANCE.GetDpiForWindow(hwnd);
			return value > 0 ? value : 96;
		} catch (UnsatisfiedLinkError ignored) {
			return 96;
		}
	}

	private static int systemMetricForDpi(int metric, int dpi) {
		try {
			return User32.INSTANCE.GetSystemMetricsForDpi(metric, dpi);
		} catch (UnsatisfiedLinkError ignored) {
			return User32.INSTANCE.GetSystemMetrics(metric);
		}
	}

	private static int scaleForDpi(int value, int dpi) {
		return Math.max(1, Math.round(value * dpi / 96f));
	}

	private static Point screenPoint(long lParam) {
		return new Point((short) (lParam & 0xffff), (short) ((lParam >>> 16) & 0xffff));
	}

	private enum NativeHit {
		HTCLIENT(1), HTCAPTION(2), HTMINBUTTON(8), HTMAXBUTTON(9), HTLEFT(10), HTRIGHT(11), HTTOP(12),
		HTTOPLEFT(13), HTTOPRIGHT(14), HTBOTTOM(15), HTBOTTOMLEFT(16), HTBOTTOMRIGHT(17), HTCLOSE(20);

		private final int value;

		NativeHit(int value) {
			this.value = value;
		}

		private static NativeHit from(HitTarget target) {
			return switch (target) {
				case CLIENT -> HTCLIENT;
				case CAPTION -> HTCAPTION;
				case MINIMIZE -> HTMINBUTTON;
				case MAXIMIZE -> HTMAXBUTTON;
				case CLOSE -> HTCLOSE;
				case LEFT -> HTLEFT;
				case RIGHT -> HTRIGHT;
				case TOP -> HTTOP;
				case BOTTOM -> HTBOTTOM;
				case TOP_LEFT -> HTTOPLEFT;
				case TOP_RIGHT -> HTTOPRIGHT;
				case BOTTOM_LEFT -> HTBOTTOMLEFT;
				case BOTTOM_RIGHT -> HTBOTTOMRIGHT;
			};
		}
	}

	private interface WindowProc extends StdCallLibrary.StdCallCallback {
		long invoke(Pointer hwnd, int message, long wParam, long lParam);
	}

	private interface User32 extends StdCallLibrary {
		User32 INSTANCE = Native.load("user32", User32.class, W32APIOptions.DEFAULT_OPTIONS);

		int GetWindowLongW(Pointer hwnd, int index);
		int SetWindowLongW(Pointer hwnd, int index, int value);
		Pointer SetWindowLongPtrW(Pointer hwnd, int index, Pointer value);
		boolean EnumChildWindows(Pointer parent, EnumWindowProc callback, Pointer data);
		int GetWindowThreadProcessId(Pointer hwnd, IntByReference processId);
		boolean IsWindow(Pointer hwnd);
		long CallWindowProcW(Pointer previous, Pointer hwnd, int message, long wParam, long lParam);
		long DefWindowProcW(Pointer hwnd, int message, long wParam, long lParam);
		boolean SetWindowPos(Pointer hwnd, Pointer insertAfter, int x, int y, int width, int height, int flags);
		boolean GetWindowRect(Pointer hwnd, Rect rect);
		boolean GetClientRect(Pointer hwnd, Rect rect);
		boolean ClientToScreen(Pointer hwnd, Point point);
		boolean IsZoomed(Pointer hwnd);
		int GetDpiForWindow(Pointer hwnd);
		int GetSystemMetricsForDpi(int index, int dpi);
		int GetSystemMetrics(int index);
		Pointer MonitorFromWindow(Pointer hwnd, int flags);
		boolean GetMonitorInfoW(Pointer monitor, MonitorInfo info);
		Pointer GetSystemMenu(Pointer hwnd, boolean revert);
		int TrackPopupMenu(Pointer menu, int flags, int x, int y, int reserved, Pointer hwnd, Pointer rect);
		boolean PostMessageW(Pointer hwnd, int message, long wParam, long lParam);
		long SendMessageW(Pointer hwnd, int message, long wParam, long lParam);
		boolean ReleaseCapture();
		Pointer LoadCursorW(Pointer instance, Pointer cursorName);
		Pointer SetCursor(Pointer cursor);
		boolean GetCursorPos(Point point);
	}

	private interface EnumWindowProc extends StdCallLibrary.StdCallCallback {
		boolean invoke(Pointer hwnd, Pointer data);
	}

	private interface DwmApi extends StdCallLibrary {
		DwmApi INSTANCE = Native.load("dwmapi", DwmApi.class, W32APIOptions.DEFAULT_OPTIONS);

		boolean DwmDefWindowProc(Pointer hwnd, int message, long wParam, long lParam, LongByReference result);
	}

	@Structure.FieldOrder({ "x", "y" })
	public static class Point extends Structure {
		public int x;
		public int y;

		public Point() {
		}

		public Point(int x, int y) {
			this.x = x;
			this.y = y;
		}
	}

	@Structure.FieldOrder({ "left", "top", "right", "bottom" })
	public static class Rect extends Structure {
		public int left;
		public int top;
		public int right;
		public int bottom;

		public Rect() {
		}

		public Rect(Pointer pointer) {
			super(pointer);
			read();
		}
	}

	@Structure.FieldOrder({ "reserved", "maxSize", "maxPosition", "minTrackSize", "maxTrackSize" })
	public static class MinMaxInfo extends Structure {
		public Point reserved = new Point();
		public Point maxSize = new Point();
		public Point maxPosition = new Point();
		public Point minTrackSize = new Point();
		public Point maxTrackSize = new Point();

		public MinMaxInfo(Pointer pointer) {
			super(pointer);
			read();
		}
	}

	@Structure.FieldOrder({ "size", "monitor", "work", "flags" })
	public static class MonitorInfo extends Structure {
		public int size = size();
		public Rect monitor = new Rect();
		public Rect work = new Rect();
		public int flags;
	}
}
