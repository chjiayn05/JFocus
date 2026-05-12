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

    private int currentSeconds;
    private FocusListener listener;

    private boolean isStopwatch = false;
    private int originalSeconds;
    private final DistractionClassifier distractionClassifier;
    private volatile DistractionUserNotifier distractionUserNotifier;
    private final DistractingTargetCloser distractingTargetCloser;
    private final DistractionModeRepository distractionModeRepository;
    private volatile DistractionHandlingMode distractionHandlingMode;

    private String currentSubject = "未分類";
    private FocusSessionRecord currentSessionRecord;

    private final SessionMonitor sessionMonitor;
    private final IdleDetector idleDetector;
    // 兩個核心控制開關 (使用 volatile 確保跨執行緒讀取安全)
    private volatile boolean isRunning = false; 
    private volatile boolean isPaused = false;

    public FocusEngine(FocusListener listener) {
        this(listener,
                new DistractionClassifier(),
                FocusEngine::notifyUserToStayFocused,
                new SystemAwareDistractingTargetCloser(),
                new JdbcDistractionModeRepository(new DatabaseCore()));
    }

    FocusEngine(FocusListener listener,
            DistractionClassifier distractionClassifier,
            DistractionUserNotifier distractionUserNotifier,
            DistractingTargetCloser distractingTargetCloser,
            DistractionModeRepository distractionModeRepository) {
        this.listener = listener;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.distractionClassifier = Objects.requireNonNull(distractionClassifier,
                "distractionClassifier cannot be null");
        this.distractionUserNotifier = Objects.requireNonNull(distractionUserNotifier,
                "distractionUserNotifier cannot be null");
        this.distractingTargetCloser = Objects.requireNonNull(distractingTargetCloser,
                "distractingTargetCloser cannot be null");
        this.distractionModeRepository = Objects.requireNonNull(distractionModeRepository,
                "distractionModeRepository cannot be null");
        this.distractionHandlingMode = this.distractionModeRepository.loadMode(DistractionHandlingMode.WARN_USER);

        this.sessionMonitor = new SessionMonitor(new SessionListener() {

            // 新視窗一出現時觸發
            @Override
            public void onSessionStarted(WindowSession session) {

                System.out.println("新視窗開啟: " + session.title);
                session.isDistracted = FocusEngine.this.distractionClassifier.isDistracting(session.processName,
                        session.title);
                if (!session.isDistracted) {
                    return;
                }

                if (FocusEngine.this.distractionHandlingMode == DistractionHandlingMode.WARN_USER) {
                    FocusEngine.this.distractionUserNotifier.notifyDontBeDistracted(session);
                    return;
                }

                boolean closeTabOnly = FocusEngine.this.distractionClassifier.isWebsiteActivity(session.processName,
                        session.title);
                boolean closed = FocusEngine.this.distractingTargetCloser.closeDistractingTarget(session, closeTabOnly);
                if (!closed) {
                    System.err.println("無法關閉分心視窗或分頁: " + session.title);
                }
            }

            // 當視窗關閉時觸發
            @Override
            public void onSessionEnded(WindowSession session) {
                // TODO: 將結束的 session 寫入資料庫的邏輯
                // 過濾"新分頁", "要翻譯這個網頁嗎？"
                // 例如：DatabaseCore.insertActivity(session);
                System.out.println("視窗關閉: " + session.title);
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

    private static void notifyUserToStayFocused(WindowSession session) {
        // TODO Placeholder: 保留給未來的訊息框函式，現在先透過回呼呼叫點串好。
        if (session != null) {
            System.out.println("請勿分心: " + session.title);
        }
    }

    public DistractionHandlingMode getDistractionHandlingMode() {
        return distractionHandlingMode;
    }

    public void setDistractionHandlingMode(DistractionHandlingMode distractionHandlingMode) {
        DistractionHandlingMode validatedMode = Objects.requireNonNull(distractionHandlingMode,
                "distractionHandlingMode cannot be null");
        this.distractionHandlingMode = validatedMode;
        this.distractionModeRepository.saveMode(validatedMode);
    }

    public void setDistractionUserNotifier(DistractionUserNotifier distractionUserNotifier) {
        this.distractionUserNotifier = Objects.requireNonNull(distractionUserNotifier,
                "distractionUserNotifier cannot be null");
    }

    public void setCurrentSubject(String subject) {
        this.currentSubject = subject;
    }

    public String getCurrentSubject() {
        return currentSubject;
    }

    // 倒數計時模式 (番茄鐘/倒數)
    public void start(int hours, int minutes, int seconds) {
        int totalSecond = (hours * SECONDS_PER_HOUR) + (minutes * SECONDS_PER_MINUTE) + seconds;
        start(totalSecond);
        isRunning = true;
        isPaused = false; // 每次開始時重置暫停狀態
    }



    public void start(int seconds) {
        stop();
        this.isPaused = false;
        this.isStopwatch = false;
        this.originalSeconds = seconds;
        this.currentSeconds = seconds;

        this.currentSessionRecord = new FocusSessionRecord();
        this.currentSessionRecord.sessionId = jfocus.main.FocusApp.getSessionId();
        this.currentSessionRecord.subject = this.currentSubject;
        this.currentSessionRecord.startTime = java.time.LocalDateTime.now();
        this.currentSessionRecord.expectedDurationSeconds = seconds;
        this.currentSessionRecord.totalIdleSecondsDeducted = 0;

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
        }, SCHEDULER_INITIAL_DELAY_SECONDS, SCHEDULER_PERIOD_SECONDS, TimeUnit.SECONDS);
    }

    // 正向計時模式 (碼表)
    public void startStopwatch() {
        stop();
        this.isPaused = false;
        this.isStopwatch = true;
        this.currentSeconds = 0; // 碼表永遠從 0 開始

        this.currentSessionRecord = new FocusSessionRecord();
        this.currentSessionRecord.sessionId = jfocus.main.FocusApp.getSessionId();
        this.currentSessionRecord.subject = this.currentSubject;
        this.currentSessionRecord.startTime = java.time.LocalDateTime.now();
        this.currentSessionRecord.expectedDurationSeconds = 0;
        this.currentSessionRecord.totalIdleSecondsDeducted = 0;

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
        }, SCHEDULER_INITIAL_DELAY_SECONDS, SCHEDULER_PERIOD_SECONDS, TimeUnit.SECONDS);
    }

    // 暫停與恢復功能
    public void pause() {
        isPaused = true;
        pause(0);
    }

    public void pause(long deductMillis) {
        if (deductMillis > 0) {
            System.out.println("計時暫停: 使用者閒置，中斷計時與紀錄");
        } else {
            System.out.println("計時暫停: 使用者手動暫停計時");
        }
        isPaused = true;

        int deductSeconds = (int) (deductMillis / 1000);
        if (deductSeconds > 0) {
            if (currentSessionRecord != null) {
                currentSessionRecord.totalIdleSecondsDeducted += deductSeconds;
            }
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

// ✅ 正確的恢復計時邏輯
    public void resume() {
        if (!isPaused) {
            return; // 如果根本沒有暫停，就什麼都不做
        }
        
        System.out.println("計時恢復: 使用者回來了，恢復計時與紀錄");
        isPaused = false; // 解除暫停狀態
        
        if (sessionMonitor != null) {
            sessionMonitor.resume(); // 恢復視窗監控
        }
    }

    // 停止與關閉功能
    public void stop() {
        if (currentTask != null && !currentTask.isCancelled()) {
            currentTask.cancel(true);
        }

        if (this.currentSessionRecord != null && this.currentSessionRecord.endTime == null) {
            this.currentSessionRecord.endTime = java.time.LocalDateTime.now();
            if (isStopwatch) {
                this.currentSessionRecord.actualDurationSeconds = currentSeconds;
            } else {
                this.currentSessionRecord.actualDurationSeconds = originalSeconds - currentSeconds;
            }

            // TODO: 將計時 session 數據寫入資料庫
            // 例如：DatabaseCore.insertFocusSession(this.currentSessionRecord);
            System.out.println("計時結束準備存入資料庫: Session " + currentSessionRecord.sessionId +
                    " | 科目: " + currentSessionRecord.subject +
                    " | 預期: " + currentSessionRecord.expectedDurationSeconds + "s" +
                    " | 實際執行: " + currentSessionRecord.actualDurationSeconds + "s" +
                    " | 扣除閒置: " + currentSessionRecord.totalIdleSecondsDeducted + "s");
        }

        sessionMonitor.stop();
        if (idleDetector != null) {
            idleDetector.stop();
        }
    }


// 關閉引擎
// ✅ 正確的徹底關閉引擎方法 (放在 FocusEngine.java 最下面)
    public void shutdown() {
        // 先呼叫組員寫好的 stop() 來結算資料和停止監控
        stop(); 
        
        // 徹底關閉背景排程器，防止記憶體外洩
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow(); 
        }
    }
}