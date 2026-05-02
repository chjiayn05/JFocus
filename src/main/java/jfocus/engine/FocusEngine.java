package jfocus.engine;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import jfocus.ai.DistractionClassifier;
import jfocus.ai.distraction.DistractingTargetCloser;
import jfocus.ai.distraction.DistractionHandlingMode;
import jfocus.ai.distraction.DistractionModeRepository;
import jfocus.ai.distraction.DistractionUserNotifier;
import jfocus.ai.distraction.JdbcDistractionModeRepository;
import jfocus.ai.distraction.SystemAwareDistractingTargetCloser;
import jfocus.db.DatabaseCore;

import jfocus.monitor.SessionListener;
import jfocus.monitor.SessionMonitor;
import jfocus.monitor.WindowSession;
import jfocus.idle.IdleDetector;
import jfocus.idle.IdleListener;

public class FocusEngine {
    private static final int SECONDS_PER_HOUR = 3600;
    private static final int SECONDS_PER_MINUTE = 60;
    private static final int SCHEDULER_INITIAL_DELAY_SECONDS = 0;
    private static final int SCHEDULER_PERIOD_SECONDS = 1;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> currentTask;

    // 💡 變數改名為 currentSeconds，因為它現在可能是「剩餘秒數」，也可能是「已過秒數」
    private int currentSeconds;
    private FocusListener listener;
    private boolean isPaused = false;
    private boolean isStopwatch = false;
    private int originalSeconds;
    private final DistractionClassifier distractionClassifier;
    private volatile DistractionUserNotifier distractionUserNotifier;
    private final DistractingTargetCloser distractingTargetCloser;
    private final DistractionModeRepository distractionModeRepository;
    private volatile DistractionHandlingMode distractionHandlingMode;

    private final SessionMonitor sessionMonitor;
    private final IdleDetector idleDetector;

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

        this.idleDetector = new IdleDetector(new IdleListener() {
            @Override
            public void onIdleStateChanged(boolean isIdle, long idleTimeMillis) {
                if (isIdle) {
                    pause(idleTimeMillis);
                } else {
                    resume();
                }
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
        this.isPaused = false;
        this.isStopwatch = false;
        this.originalSeconds = seconds;
        this.currentSeconds = seconds;
        sessionMonitor.start();
        idleDetector.start();

        currentTask = scheduler.scheduleAtFixedRate(() -> {
            if (isPaused)
                return;

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
        this.isPaused = false;
        this.isStopwatch = true;
        this.currentSeconds = 0; // 碼表永遠從 0 開始
        sessionMonitor.start();
        idleDetector.start();

        currentTask = scheduler.scheduleAtFixedRate(() -> {
            if (isPaused)
                return;

            currentSeconds++; // 上數遞增

            if (listener != null) {
                listener.onTick(currentSeconds); // 不斷更新 UI
            }
            // 碼表不會自動結束，必須等使用者自己按 stop()
        }, 0, 1, TimeUnit.SECONDS);
    }

    // ==========================================
    // 🛑 暫停與恢復功能
    // ==========================================
    public void pause() {
        pause(0);
    }

    public void pause(long deductMillis) {
        System.out.println("⏸️ [計時暫停] 使用者閒置，中斷計時與紀錄");
        isPaused = true;

        int deductSeconds = (int) (deductMillis / 1000);
        if (deductSeconds > 0) {
            if (isStopwatch) {
                currentSeconds -= deductSeconds;
                if (currentSeconds < 0)
                    currentSeconds = 0;
            } else {
                currentSeconds += deductSeconds;
                if (currentSeconds > originalSeconds)
                    currentSeconds = originalSeconds;
            }
            // 強制觸發一次 listener 更新 UI
            if (listener != null) {
                listener.onTick(currentSeconds);
            }
        }

        sessionMonitor.pause(deductMillis);
    }

    public void resume() {
        if (!isPaused)
            return;
        System.out.println("▶️ [計時恢復] 使用者回來了，恢復計時與紀錄");
        isPaused = false;
        sessionMonitor.resume();
    }

    // ==========================================
    // 🛑 停止與關閉功能
    // ==========================================
    public void stop() {
        if (currentTask != null && !currentTask.isCancelled()) {
            currentTask.cancel(true);
        }
        sessionMonitor.stop();
        if (idleDetector != null) {
            idleDetector.stop();
        }
    }

    // 確保程式關閉時，資源能正確釋放
    public void shutdown() {
        stop();
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }
}