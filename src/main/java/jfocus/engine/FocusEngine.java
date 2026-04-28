package jfocus.engine;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import jfocus.monitor.SessionListener;
import jfocus.monitor.SessionMonitor;
import jfocus.monitor.WindowSession;

public class FocusEngine {
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> currentTask;
    
    // 💡 變數改名為 currentSeconds，因為它現在可能是「剩餘秒數」，也可能是「已過秒數」
    private int currentSeconds; 
    private FocusListener listener;

    private final SessionMonitor sessionMonitor;
    
    public FocusEngine(FocusListener listener) {
        this.listener = listener;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        
        this.sessionMonitor = new SessionMonitor(new SessionListener() {
            
            // ==========================================
            // 🚀 新增：當新視窗一出現時觸發
            // ==========================================
            @Override
            public void onSessionStarted(WindowSession session) {
                // TODO: (交給隊友寫) 偵測是否分心的邏輯
                // 隊友可以在這裡呼叫他寫好的判斷方法，例如：
                // session.isDistracted = DistractionChecker.check(session.title);
                
                System.out.println("🟢 [引擎接獲通報] 新視窗開啟: " + session.title);
            }

            // ==========================================
            // 原本的：當視窗關閉時觸發
            // ==========================================
            @Override
            public void onSessionEnded(WindowSession session) {
                // TODO: (交給隊友寫) 將結束的 session 寫入資料庫的邏輯
                // 例如：DatabaseCore.insertActivity(session);
                
                System.out.println("🚩 [引擎後台收到報告] 視窗關閉了: " + session.title);
            }
        });
    }

    // ==========================================
    // 🔽 模式一：倒數計時模式 (番茄鐘/倒數)
    // ==========================================
    public void start(int hours, int minutes, int seconds) {
        int totalSecond = (hours * 3600) + (minutes * 60) + seconds;
        start(totalSecond);
    }
    
    public void start(int seconds) {
        stop();
        this.currentSeconds = seconds;
        sessionMonitor.start();

        currentTask = scheduler.scheduleAtFixedRate(() -> {
            currentSeconds--; // 倒數遞減

            if (currentSeconds <= 0) {
                stop(); 
                if (listener != null) {
                    listener.onFinished(); // 時間到，通知 UI
                }
            } else {
                if (listener != null) {
                    listener.onTick(currentSeconds); 
                }
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    // ==========================================
    // 🔼 模式二：正向計時模式 (碼表)
    // ==========================================
    public void startStopwatch() {
        stop();
        this.currentSeconds = 0; // 碼表永遠從 0 開始
        sessionMonitor.start();

        currentTask = scheduler.scheduleAtFixedRate(() -> {
            currentSeconds++; // 上數遞增

            if (listener != null) {
                listener.onTick(currentSeconds); // 不斷更新 UI
            }
            // 碼表不會自動結束，必須等使用者自己按 stop()
        }, 0, 1, TimeUnit.SECONDS);
    }

    // ==========================================
    // 🛑 停止與關閉功能
    // ==========================================
    public void stop() {
        if (currentTask != null && !currentTask.isCancelled()) {
            currentTask.cancel(true);
        }
        sessionMonitor.stop();
    }

    // 確保程式關閉時，資源能正確釋放
    public void shutdown() {
        stop();
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }
}