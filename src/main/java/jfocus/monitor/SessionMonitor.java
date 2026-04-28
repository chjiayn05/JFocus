package jfocus.monitor;

import java.util.HashMap;
import java.util.Map;

public class SessionMonitor {
    private Map<String, WindowSession> activeSessions = new HashMap<>();
    private SessionListener listener;
    private WindowScanner scanner;

    public SessionMonitor(SessionListener listener) {
        this.listener = listener;
        
        // 判斷目前的作業系統
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            this.scanner = new Win32WindowScanner();
        } else if (os.contains("mac")) {
            this.scanner = new MacWindowScanner();
        } else {
            System.err.println("尚未支援的作業系統: " + os);
            // 預設給一個空殼，避免 NullPointerException
            this.scanner = () -> new HashMap<>();
        }
    }

    public void scan() {
        if (scanner == null) return;
        
        Map<String, WindowSession> currentScan = scanner.scanWindows();

        activeSessions.entrySet().removeIf(entry -> {
            if (!currentScan.containsKey(entry.getKey())) {
                WindowSession endedSession = entry.getValue();
                endedSession.endTime = java.time.LocalDateTime.now();
                
                if (listener != null) {
                    listener.onSessionEnded(endedSession); 
                }
                return true; 
            }
            return false;
        });

        currentScan.forEach((hwnd, session) -> {
            if (!activeSessions.containsKey(hwnd)) {
                activeSessions.put(hwnd, session);
                
                // 🚀 就是這裡！當發現新視窗，立刻拋給處理器去判斷是否分心
                if (listener != null) {
                    listener.onSessionStarted(session);
                }
                
                System.out.println("🟢 [監控啟動] 偵測到新視窗: " + session.title);
            }
        });
    }
}