package jfocus.idle;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class IdleDetector {
    // 閒置條件：5 分鐘沒有動滑鼠/鍵盤
    // private static final long IDLE_THRESHOLD_MS = 5 * 60 * 1000;
    private static final long IDLE_THRESHOLD_MS = 10000;
    // 檢查螢幕是否變化的頻率：5 秒
    // private static final long SCREEN_CHECK_INTERVAL_MS = 5 * 1000;
    private static final long SCREEN_CHECK_INTERVAL_MS = 3000;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> task;
    private ScreenChangeDetector screenDetector;
    private IdleListener listener;

    private boolean isCurrentlyIdle = false;
    private long lastScreenCheckTime = 0;

    public IdleDetector(IdleListener listener) {
        this.listener = listener;
        this.screenDetector = new ScreenChangeDetector();
    }

    public void start() {
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor();
        }

        // 每 1 秒檢查一次，這樣使用者一動滑鼠我們能立刻恢復計時
        if (task == null || task.isCancelled()) {
            task = scheduler.scheduleAtFixedRate(this::checkIdleState, 0, 1, TimeUnit.SECONDS);
        }
    }

    public void stop() {
        if (task != null && !task.isCancelled()) {
            task.cancel(false);
        }
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }

    private void checkIdleState() {
        long idleTime = OSIdleTracker.getIdleTimeMillis();
        long now = System.currentTimeMillis();

        if (idleTime >= IDLE_THRESHOLD_MS) {
            // 滑鼠/鍵盤已閒置超過 5 分鐘，接著每 5 秒檢查一次螢幕是否變化
            if (now - lastScreenCheckTime >= SCREEN_CHECK_INTERVAL_MS) {
                boolean screenChanged = screenDetector.hasScreenChanged();
                lastScreenCheckTime = now;

                if (!screenChanged) {
                    // 條件達成：沒動滑鼠且螢幕沒變 -> 判定為真正閒置
                    if (!isCurrentlyIdle) {
                        isCurrentlyIdle = true;
                        if (listener != null) {
                            listener.onIdleStateChanged(true, idleTime);
                        }
                    }
                } else {
                    // 螢幕有變化 (可能在看影片)，不算是閒置
                    if (isCurrentlyIdle) {
                        isCurrentlyIdle = false;
                        if (listener != null) {
                            listener.onIdleStateChanged(false, 0);
                        }
                    }
                }
            }
        } else {
            // 使用者近期有動滑鼠或鍵盤
            if (isCurrentlyIdle) {
                isCurrentlyIdle = false;
                if (listener != null) {
                    listener.onIdleStateChanged(false, 0);
                }
            }
        }
    }
}
