package jfocus.idle;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class IdleDetectorTest {

    public static void main(String[] args) {
        System.out.println("=== 啟動 IdleDetector 獨立測試 ===");
        System.out.println("提示：為了能快速看到結果，建議您暫時將 IdleDetector.java 內的");
        System.out.println("IDLE_THRESHOLD_MS 改為 5000 (5秒)，");
        System.out.println("SCREEN_CHECK_INTERVAL_MS 改為 3000 (3秒)。");
        System.out.println("===================================");

        IdleListener testListener = new IdleListener() {
            @Override
            public void onIdleStateChanged(boolean isIdle, long idleTimeMillis) {
                if (isIdle) {
                    System.out.println("\n[測試事件] ⚠️ 系統判定為【閒置中】 (扣除時間: " + (idleTimeMillis / 1000) + " 秒)");
                } else {
                    System.out.println("\n[測試事件] ✅ 系統判定為【活動中】 (使用者回來了，或螢幕正在播放影片)");
                }
            }
        };

        // 2. 啟動 IdleDetector
        IdleDetector detector = new IdleDetector(testListener);
        detector.start();
        System.out.println("IdleDetector 已在背景啟動...");

        // 3. 為了讓測試更直覺，我們額外開一個小任務，每秒印出目前的作業系統閒置時間
        ScheduledExecutorService logger = Executors.newSingleThreadScheduledExecutor();
        logger.scheduleAtFixedRate(() -> {
            long idleTime = OSIdleTracker.getIdleTimeMillis();
            System.out.println("[目前狀態] 滑鼠與鍵盤已閒置 " + (idleTime / 1000) + " 秒 (" + idleTime + " 毫秒)");
        }, 0, 1, TimeUnit.SECONDS);

        // 讓主執行緒保持活著，測試 60 秒後自動結束
        try {
            Thread.sleep(60 * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        detector.stop();
        logger.shutdown();
        System.out.println("=== 測試結束 ===");
    }
}
