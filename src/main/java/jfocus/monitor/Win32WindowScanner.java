package jfocus.monitor;

import java.util.HashMap;
import java.util.Map;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.W32APIOptions;

public class Win32WindowScanner implements WindowScanner {
    
    // 取得視窗一般屬性的常數
    private static final int GWL_STYLE = -16;
    // 代表「視窗已最小化」的常數碼
    private static final int WS_MINIMIZE = 0x20000000;

    private static final int GWL_EXSTYLE = -20;
    private static final int WS_EX_TOOLWINDOW = 0x00000080;
    private static final int DWMWA_CLOAKED = 14;

    // ==========================================
    // 🚀 JNA 魔法：自己手刻 Dwmapi 橋樑
    // ==========================================
    public interface Dwmapi extends Library {
        // 載入系統底層的 dwmapi.dll
        Dwmapi INSTANCE = Native.load("dwmapi", Dwmapi.class, W32APIOptions.DEFAULT_OPTIONS);
        
        // 宣告我們要用的 DwmGetWindowAttribute 函數
        int DwmGetWindowAttribute(HWND hwnd, int dwAttribute, IntByReference pvAttribute, int cbAttribute);
    }

    @Override
    public Map<String, WindowSession> scanWindows() {
        Map<String, WindowSession> currentScan = new HashMap<>();

        User32.INSTANCE.EnumWindows((hWnd, arg1) -> {
            if (isValidWindow(hWnd)) {
                String hwndKey = hWnd.getPointer().toString();
                String title = getWindowTitle(hWnd);
                String processName = getProcessName(hWnd);
                
                currentScan.put(hwndKey, new WindowSession(hwndKey, processName, title));
            }
            return true;
        }, null);

        return currentScan;
    }

    // ==========================================
    // 🛡️ 專業級視窗過濾器 (終極嚴格版)
    // ==========================================
    private boolean isValidWindow(HWND hWnd) {
        // 1. 必須有 Visible 標籤
        if (!User32.INSTANCE.IsWindowVisible(hWnd)) {
            return false;
        }

        // 🚀 新增防線：如果視窗被「最小化 (縮在工作列)」，不算使用者在看！
        int style = User32.INSTANCE.GetWindowLong(hWnd, GWL_STYLE);
        if ((style & WS_MINIMIZE) != 0) {
            return false;
        }

        // 2. 必須有名字 (排除無名元件)
        String title = getWindowTitle(hWnd);
        if (title.isEmpty()) {
            return false;
        }

        // 3. 排除 Tool Window (背景常駐程式的隱藏工具視窗)
        int exStyle = User32.INSTANCE.GetWindowLong(hWnd, GWL_EXSTYLE);
        if ((exStyle & WS_EX_TOOLWINDOW) != 0) {
            return false;
        }

        // 4. 排除長寬為 0 的畸形視窗
        RECT rect = new RECT();
        User32.INSTANCE.GetWindowRect(hWnd, rect);
        if (rect.right - rect.left <= 0 || rect.bottom - rect.top <= 0) {
            return false;
        }

        // 5. 呼叫我們自己寫的 Dwmapi 橋樑，揪出 Windows 10/11 的隱形斗篷！
        IntByReference cloaked = new IntByReference();
        Dwmapi.INSTANCE.DwmGetWindowAttribute(hWnd, DWMWA_CLOAKED, cloaked, 4);
        if (cloaked.getValue() != 0) {
            return false;
        }

        // 6. 排除系統底層桌面
        if (title.equals("Program Manager")) {
            return false;
        }

        return true;
    }

    private String getWindowTitle(HWND hWnd) {
        char[] buffer = new char[512];
        User32.INSTANCE.GetWindowText(hWnd, buffer, 512);
        return Native.toString(buffer).trim();
    }

    private String getProcessName(HWND hWnd) {
        IntByReference pid = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(hWnd, pid);
        return "PID:" + pid.getValue(); 
    }
}
