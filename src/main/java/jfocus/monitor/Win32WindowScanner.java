package jfocus.monitor;

import java.awt.Rectangle;
import java.awt.geom.Area;
import java.util.HashMap;
import java.util.Map;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.W32APIOptions;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;

public class Win32WindowScanner implements WindowScanner {

    // 取得視窗一般屬性的常數
    private static final int GWL_STYLE = -16;
    // 代表「視窗已最小化」的常數碼
    private static final int WS_MINIMIZE = 0x20000000;

    private static final int GWL_EXSTYLE = -20;
    private static final int WS_EX_TOOLWINDOW = 0x00000080;
    private static final int DWMWA_CLOAKED = 14;

    // JNA 橋樑
    public interface Dwmapi extends Library {
        // 載入系統底層的 dwmapi.dll
        Dwmapi INSTANCE = Native.load("dwmapi", Dwmapi.class, W32APIOptions.DEFAULT_OPTIONS);

        // 宣告 DwmGetWindowAttribute 函數
        int DwmGetWindowAttribute(HWND hwnd, int dwAttribute, IntByReference pvAttribute, int cbAttribute);
    }

    @Override
    public Map<String, WindowSession> scanWindows() {
        Map<String, WindowSession> currentScan = new HashMap<>();
        final Area seenArea = new Area(); // 用來記錄已經被上層視窗蓋住的畫布區域

        User32.INSTANCE.EnumWindows((hWnd, arg1) -> {
            if (isValidWindow(hWnd)) {
                // 取得視窗的範圍
                RECT rect = new RECT();
                User32.INSTANCE.GetWindowRect(hWnd, rect);
                Rectangle windowRect = new Rectangle(rect.left, rect.top, rect.right - rect.left,
                        rect.bottom - rect.top);
                Area windowArea = new Area(windowRect);

                // 拿視窗的形狀，去扣除目前已經被上層視窗擋住的形狀
                windowArea.subtract(seenArea);

                // 如果扣除後面積為 0，代表它被前面的視窗完全擋住了
                if (windowArea.isEmpty()) {
                    return true; // 直接跳過，不記錄這個視窗
                }

                // 如果沒有被完全遮住，就記錄下來
                long handleValue = Pointer.nativeValue(hWnd.getPointer());
                String hwndKey = Long.toUnsignedString(handleValue);
                String title = getWindowTitle(hWnd);
                String processName = getProcessName(hWnd);

                currentScan.put(hwndKey, new WindowSession(hwndKey, processName, title));

                // 最後，把這個視窗的完整形狀疊加到「已遮蔽畫布」上，讓下一個底層視窗進行比對
                seenArea.add(new Area(windowRect));
            }
            return true;
        }, null);

        return currentScan;
    }

    // 嚴格視窗過濾器
    private boolean isValidWindow(HWND hWnd) {
        // 1. 必須有 Visible 標籤
        if (!User32.INSTANCE.IsWindowVisible(hWnd)) {
            return false;
        }

        // 2. 如果視窗被最小化，不算使用者在看
        int style = User32.INSTANCE.GetWindowLong(hWnd, GWL_STYLE);
        if ((style & WS_MINIMIZE) != 0) {
            return false;
        }

        // 3. 必須有名字 (排除無名元件)
        String title = getWindowTitle(hWnd);
        if (title.isEmpty()) {
            return false;
        }

        // 4. 排除 Tool Window
        int exStyle = User32.INSTANCE.GetWindowLong(hWnd, GWL_EXSTYLE);
        if ((exStyle & WS_EX_TOOLWINDOW) != 0) {
            return false;
        }

        // 5. 排除長寬為 0 的視窗
        RECT rect = new RECT();
        User32.INSTANCE.GetWindowRect(hWnd, rect);
        if (rect.right - rect.left <= 0 || rect.bottom - rect.top <= 0) {
            return false;
        }

        // 6. 呼叫我們自己寫的 Dwmapi 橋樑，揪出 Windows 10/11 的隱形斗篷
        IntByReference cloaked = new IntByReference();
        Dwmapi.INSTANCE.DwmGetWindowAttribute(hWnd, DWMWA_CLOAKED, cloaked, 4);
        if (cloaked.getValue() != 0) {
            return false;
        }

        // 7. 排除系統底層桌面
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
        int processId = pid.getValue();

        HANDLE hProcess = Kernel32.INSTANCE.OpenProcess(
                WinNT.PROCESS_QUERY_LIMITED_INFORMATION,
                false,
                processId);

        if (hProcess != null) {
            try {
                char[] buffer = new char[1024];
                IntByReference lpdwSize = new IntByReference(buffer.length);
                if (Kernel32.INSTANCE.QueryFullProcessImageName(hProcess, 0, buffer, lpdwSize)) {
                    String fullPath = Native.toString(buffer).trim();
                    int lastSlash = Math.max(fullPath.lastIndexOf('\\'), fullPath.lastIndexOf('/'));
                    if (lastSlash != -1) {
                        return fullPath.substring(lastSlash + 1);
                    }
                    return fullPath;
                }
            } catch (Exception e) {
                // 忽略錯誤，降級使用 PID
            } finally {
                Kernel32.INSTANCE.CloseHandle(hProcess);
            }
        }

        return "PID:" + processId;
    }
}
