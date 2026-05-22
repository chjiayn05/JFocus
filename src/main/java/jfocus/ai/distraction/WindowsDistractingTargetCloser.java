package jfocus.ai.distraction;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;

import jfocus.monitor.WindowSession;

public class WindowsDistractingTargetCloser implements DistractingTargetCloser {
    private static final int HWND_RADIX = 10;
    private static final int WM_CLOSE = 0x0010;
    private static final int SW_RESTORE = 9;
    private static final int FOCUS_SWITCH_DELAY_MILLIS = 120;
    private static final int KEY_INPUT_DELAY_MILLIS = 40;

    private interface User32Ext extends Library {
        User32Ext INSTANCE = Native.load("user32", User32Ext.class);
        boolean IsIconic(HWND hWnd);
    }

    @Override
    public boolean closeDistractingTarget(WindowSession session, boolean closeTabOnly) {
        if (session == null || session.hwnd == null || session.hwnd.isBlank()) {
            return false;
        }

        HWND targetHwnd = parseWindowHandle(session.hwnd);
        if (targetHwnd == null || !User32.INSTANCE.IsWindow(targetHwnd)) {
            return false;
        }

        if (closeTabOnly) {
            return closeBrowserTab(targetHwnd);
        }

        User32.INSTANCE.PostMessage(targetHwnd, WM_CLOSE, null, null);
        return true;
    }

    private HWND parseWindowHandle(String hwndValue) {
        try {
            long pointerValue = Long.parseUnsignedLong(hwndValue, HWND_RADIX);
            HWND hwnd = new HWND();
            hwnd.setPointer(Pointer.createConstant(pointerValue));
            return hwnd;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean closeBrowserTab(HWND hwnd) {
        // 只有最小化時才 restore，保留最大化等其他狀態
        if (User32Ext.INSTANCE.IsIconic(hwnd)) {
            User32.INSTANCE.ShowWindow(hwnd, SW_RESTORE);
        }
        if (!User32.INSTANCE.SetForegroundWindow(hwnd)) {
            return false;
        }

        return sendControlWShortcut();
    }

    private boolean sendControlWShortcut() {
        try {
            Robot robot = new Robot();
            robot.delay(FOCUS_SWITCH_DELAY_MILLIS);
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(KeyEvent.VK_W);
            robot.delay(KEY_INPUT_DELAY_MILLIS);
            robot.keyRelease(KeyEvent.VK_W);
            robot.keyRelease(KeyEvent.VK_CONTROL);
            return true;
        } catch (AWTException ex) {
            return false;
        }
    }
}
