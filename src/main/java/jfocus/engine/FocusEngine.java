package jfocus.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import jfocus.ai.BrowserTitleCleaner;
import jfocus.ai.DistractionClassifier;
import jfocus.ai.distraction.DistractingTargetCloser;
import jfocus.ai.distraction.DistractionHandlingMode;
import jfocus.ai.distraction.DistractionModeRepository;
import jfocus.ai.distraction.DistractionUserNotifier;
import jfocus.ai.distraction.JdbcDistractionModeRepository;
import jfocus.ai.distraction.SystemAwareDistractingTargetCloser;
import jfocus.db.DatabaseCore;
import jfocus.io.PushData;
import jfocus.main.FocusApp;

import jfocus.monitor.SessionListener;
import jfocus.monitor.SessionMonitor;
import jfocus.monitor.WindowSession;
import jfocus.idle.IdleDetector;
import jfocus.idle.IdleListener;
import jfocus.notification.NotificationPayload;
import jfocus.notification.NotificationSeverity;

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
    private final PushData pushData;
    private final List<String> ignoredWindowKeys = new ArrayList<>();

    private final SessionMonitor sessionMonitor;
    private final IdleDetector idleDetector;
    // 兩個核心控制開關 (使用 volatile 確保跨執行緒讀取安全)
    private volatile boolean isRunning = false;
    private volatile boolean isPaused = false;

    public FocusEngine(FocusListener listener) {
        this(listener,
                new DistractionClassifier(),
                session -> {
                    if (session != null) {
                        System.out.println("請勿分心: " + session.title);
                    }
                },
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
        this.pushData = new PushData();

        this.sessionMonitor = new SessionMonitor(new SessionListener() {

            // 新視窗一出現時觸發
            @Override
            public void onSessionStarted(WindowSession session) {

                System.out.println("新視窗開啟: " + session.title);
                if (FocusEngine.this.isIgnoredWindow(session)) {
                    session.isDistracted = true;
                    return;
                }

                session.isDistracted = FocusEngine.this.distractionClassifier.isDistracting(session.processName,
                        session.title);
                if (!session.isDistracted) {
                    return;
                }

                if (FocusEngine.this.distractionHandlingMode == DistractionHandlingMode.WARN_USER) {
                    FocusEngine.this.distractionUserNotifier.notifyDontBeDistracted(session);
                    return;
                }

                boolean closed = FocusEngine.this.closeDistractingTarget(session);
                if (!closed) {
                    System.err.println("無法關閉分心視窗或分頁: " + session.title);
                }
            }

            // 當視窗關閉時觸發
            @Override
            public void onSessionEnded(WindowSession session) {
                System.out.println("視窗關閉: " + session.title);
                if (session.title == null || session.title.isBlank() ||
                        session.title.equals("新分頁") || session.title.equals("要翻譯這個網頁嗎？")) {
                    return;
                }
                try {
                    FocusEngine.this.pushData.insertActivity(
                            session.processName,
                            session.title,
                            session.startTime,
                            session.endTime,
                            !session.isDistracted);
                } catch (Exception e) {
                    System.err.println("儲存活動明細失敗: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });

        this.idleDetector = new IdleDetector(new IdleListener() {
            @Override
            public void onIdleStateChanged(boolean isIdle, long idleTimeMillis) {
                if (isIdle) {
                    pause(idleTimeMillis);
                    if (listener != null) {
                        listener.onIdleDetected(idleTimeMillis);
                    }
                }
                // 使用者回來的 event (!isIdle) 不再自動呼叫 resume()，而是等使用者關掉視窗後由 UI 呼叫 resume()
            }
        });
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

    public List<String> suggestWhitelistKeywordsForSession(WindowSession session) {
        WindowSession validatedSession = Objects.requireNonNull(session, "session cannot be null");
        return distractionClassifier.suggestRuleKeywords(validatedSession.processName, validatedSession.title);
    }

    public void addWhitelistRule(String keyword) {
        distractionClassifier.addWhitelistRule(keyword);
    }

    public void ignoreWindow(WindowSession session) {
        WindowSession validatedSession = Objects.requireNonNull(session, "session cannot be null");
        String ignoredWindowKey = createWindowKey(validatedSession);
        synchronized (ignoredWindowKeys) {
            if (!ignoredWindowKeys.contains(ignoredWindowKey)) {
                ignoredWindowKeys.add(ignoredWindowKey);
            }
        }
        validatedSession.isDistracted = true;
    }

    public boolean closeDistractingTarget(WindowSession session) {
        WindowSession validatedSession = Objects.requireNonNull(session, "session cannot be null");
        boolean closeTabOnly = distractionClassifier.isWebsiteActivity(validatedSession.processName, validatedSession.title);
        boolean closed = distractingTargetCloser.closeDistractingTarget(validatedSession, closeTabOnly);
        if (closed) {
            String title = BrowserTitleCleaner.extractImportantTitle(
                    validatedSession.processName,
                    validatedSession.title);
            if (title.isBlank()) {
                title = "分心視窗";
            }
            FocusApp.getNotificationService().notify(new NotificationPayload(
                    "已關閉分心視窗",
                    "應用程式: " + validatedSession.processName + "\n視窗名稱: " + title,
                    NotificationSeverity.URGENT,
                    "FocusEngine"));
        }
        return closed;
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
        clearIgnoredWindows();
        this.isPaused = false;
        this.isStopwatch = false;
        this.originalSeconds = seconds;
        this.currentSeconds = seconds;

        jfocus.main.FocusApp.startNewSession(); // 啟動時計時器自動生成新的 Session ID
        this.currentSessionRecord = new FocusSessionRecord();
        this.currentSessionRecord.sessionId = jfocus.main.FocusApp.getSessionId();
        this.currentSessionRecord.subject = this.currentSubject;
        this.currentSessionRecord.startTime = java.time.LocalDateTime.now();
        this.currentSessionRecord.expectedDurationSeconds = seconds;
        this.currentSessionRecord.totalIdleSecondsDeducted = 0;

        System.out.println("[DEBUG][Distraction] Starting session monitor. mode="
                + this.distractionHandlingMode.name());
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

    public void startBreak(int seconds) {
        stop();
        this.isPaused = false;
        this.isStopwatch = false;
        this.originalSeconds = seconds;
        this.currentSeconds = seconds;

        currentTask = scheduler.scheduleAtFixedRate(() -> {
            if (isPaused)
                return;

            currentSeconds--;

            if (currentSeconds <= 0) {
                stop();
                if (listener != null) {
                    listener.onFinished();
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
        clearIgnoredWindows();
        this.isPaused = false;
        this.isStopwatch = true;
        this.currentSeconds = 0; // 碼表永遠從 0 開始

        jfocus.main.FocusApp.startNewSession(); // 啟動時計時器自動生成新的 Session ID
        this.currentSessionRecord = new FocusSessionRecord();
        this.currentSessionRecord.sessionId = jfocus.main.FocusApp.getSessionId();
        this.currentSessionRecord.subject = this.currentSubject;
        this.currentSessionRecord.startTime = java.time.LocalDateTime.now();
        this.currentSessionRecord.expectedDurationSeconds = 0;
        this.currentSessionRecord.totalIdleSecondsDeducted = 0;

        System.out.println("[DEBUG][Distraction] Starting session monitor. mode="
                + this.distractionHandlingMode.name());
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

        if (listener != null) {
            listener.onPaused();
        }
    }

    // 正確的恢復計時邏輯
    public void resume() {
        if (!isPaused) {
            return; // 如果根本沒有暫停，就什麼都不做
        }

        System.out.println("計時恢復: 使用者回來了，恢復計時與紀錄");
        isPaused = false; // 解除暫停狀態

        if (sessionMonitor != null) {
            sessionMonitor.resume(); // 恢復視窗監控
        }

        if (listener != null) {
            listener.onResumed();
        }
    }

    // Debug: 快進 N 秒（碼表往前加，倒數往前減，不觸發 onFinished）
    public void debugForward(int seconds) {
        if (isStopwatch) {
            currentSeconds += seconds;
        } else {
            currentSeconds -= seconds;
            if (currentSeconds <= 1) currentSeconds = 1;
        }
        if (listener != null) {
            listener.onTick(currentSeconds);
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

            // 已將計時 session 數據寫入資料庫
            try {
                FocusEngine.this.pushData.insertFocusSession(this.currentSessionRecord);
            } catch (Exception e) {
                System.err.println("儲存專注 Session 失敗: " + e.getMessage());
                e.printStackTrace();
            }
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
    public void shutdown() {
        stop();

        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }

    private boolean isIgnoredWindow(WindowSession session) {
        String windowKey = createWindowKey(session);
        synchronized (ignoredWindowKeys) {
            return ignoredWindowKeys.contains(windowKey);
        }
    }

    private void clearIgnoredWindows() {
        synchronized (ignoredWindowKeys) {
            ignoredWindowKeys.clear();
        }
    }

    private String createWindowKey(WindowSession session) {
        if (session == null) {
            return "";
        }
        return normalizeWindowKeyPart(session.processName) + "\n" + normalizeWindowKeyPart(session.title);
    }

    private String normalizeWindowKeyPart(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase();
    }
}
