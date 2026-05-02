package jfocus.idle;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinUser.LASTINPUTINFO;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class OSIdleTracker {
    private static final String OS = System.getProperty("os.name").toLowerCase();

    /**
     * 取得作業系統閒置時間 (毫秒)
     * @return 距離上次滑鼠或鍵盤操作經過的毫秒數
     */
    public static long getIdleTimeMillis() {
        if (OS.contains("win")) {
            return getWindowsIdleTime();
        } else if (OS.contains("mac")) {
            return getMacIdleTime();
        }
        return 0; // 暫不支援其他 OS
    }

    private static long getWindowsIdleTime() {
        try {
            LASTINPUTINFO lastInputInfo = new LASTINPUTINFO();
            if (User32.INSTANCE.GetLastInputInfo(lastInputInfo)) {
                int tickCount = Kernel32.INSTANCE.GetTickCount();
                return tickCount - lastInputInfo.dwTime;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    private static long getMacIdleTime() {
        try {
            ProcessBuilder pb = new ProcessBuilder("ioreg", "-c", "IOHIDSystem");
            Process p = pb.start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.contains("HIDIdleTime")) {
                        String[] parts = line.split("=");
                        if (parts.length > 1) {
                            long nanoTime = Long.parseLong(parts[1].trim());
                            return nanoTime / 1_000_000; // 轉換奈秒至毫秒
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }
}
