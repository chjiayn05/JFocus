package jfocus.monitor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class SessionMonitor {
    private Map<String, WindowSession> activeSessions = new HashMap<>();
    private SessionListener listener;
    private WindowScanner scanner;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scanTask;

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

    public void start() {
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor();
        }

        // 每 1 秒獨立執行一次掃描
        if (scanTask == null || scanTask.isCancelled()) {
            scanTask = scheduler.scheduleAtFixedRate(this::scan, 0, 1, TimeUnit.SECONDS);
        }
    }

    public void stop() {
        if (scanTask != null && !scanTask.isCancelled()) {
            scanTask.cancel(false);
        }
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
        clearActiveSessions(0);
    }

    public void pause() {
        pause(0);
    }

    public void pause(long deductMillis) {
        if (scanTask != null && !scanTask.isCancelled()) {
            scanTask.cancel(false);
        }
        if (deductMillis > 0) {
            System.out.println("監控暫停: 進入閒置狀態，停止記錄視窗 (已扣除閒置時間)");
        } else {
            System.out.println("監控暫停: 使用者手動暫停，停止記錄視窗");
        }
        clearActiveSessions(deductMillis);
    }

    public void resume() {
        System.out.println("監控恢復: 使用者恢復活動");
        // 重新啟動掃描任務
        if (scanTask == null || scanTask.isCancelled()) {
            scanTask = scheduler.scheduleAtFixedRate(this::scan, 0, 1, TimeUnit.SECONDS);
        }
    }

    private void clearActiveSessions(long deductMillis) {
        for (WindowSession session : activeSessions.values()) {
            // 將結束時間往前扣掉發呆的這段時間
            session.endTime = java.time.LocalDateTime.now().minusNanos(deductMillis * 1_000_000);
            if (listener != null) {
                listener.onSessionEnded(session);
            }
        }
        activeSessions.clear();
    }

    private void scan() {
        if (scanner == null)
            return;

        Map<String, WindowSession> currentScan = scanner.scanWindows();

        // 核心過濾：排除專案本身的視窗（如主畫面、分心提醒、冒險結算、放棄冒險等）
        currentScan.entrySet().removeIf(entry -> isJFocusWindow(entry.getValue()));

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

                // 當發現新視窗，立刻拋給處理器去判斷是否分心
                if (listener != null) {
                    listener.onSessionStarted(session);
                }
            } else {
                // 如果已經存在該 HWND，但標題變更了（例如新分頁 -> 搜尋結果）
                WindowSession existingSession = activeSessions.get(hwnd);
                if (!existingSession.title.equals(session.title)) {
                    // 1. 結束舊標題的 Session
                    existingSession.endTime = java.time.LocalDateTime.now();
                    if (listener != null) {
                        listener.onSessionEnded(existingSession);
                    }

                    // 2. 替換為新標題的 Session 並啟動判定
                    activeSessions.put(hwnd, session);
                    if (listener != null) {
                        listener.onSessionStarted(session);
                    }
                    System.out.println("監控更新: 標題變更 " + existingSession.title + " -> " + session.title);
                }
            }
        });
    }

    private boolean isJFocusWindow(WindowSession session) {
        if (session == null) {
            return false;
        }

        // 1. 優先比對 Process ID：如果是當前 JFocus JVM 進程產生的所有視窗，一律直接過濾排除
        try {
            long currentPid = ProcessHandle.current().pid();
            if (session.pid == currentPid) {
                return true;
            }
        } catch (Exception e) {
            // 忽略取得 PID 的例外，繼續使用下方的特徵名稱 Fallback 判斷
        }

        // 2. Fallback 機制：比對進程名稱與 JFocus 專屬視窗標題特徵
        if (session.processName == null || session.title == null) {
            return false;
        }
        String proc = session.processName.toLowerCase();
        String title = session.title.toLowerCase();

        boolean isJava = proc.contains("java") || proc.contains("jfocus");
        boolean isJFocusTitle = title.contains("jfocus")
                || title.contains("pokemon")
                || title.contains("sentinel")
                || title.contains("分心")
                || title.contains("冒險")
                || title.contains("結束")
                || title.contains("提醒")
                || title.contains("結算")
                || title.contains("放棄");

        return isJava && isJFocusTitle;
    }
}
