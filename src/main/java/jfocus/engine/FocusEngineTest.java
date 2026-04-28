package jfocus.engine;

public class FocusEngineTest {

    public static void main(String[] args) {
        System.out.println("=== 啟動 FocusEngine 測試 ===");

        FocusListener listener = new FocusListener() {
            @Override
            public void onTick(int secondsRemaining) {
                // 將秒數轉換為 分:秒 的格式方便觀看
                int minutes = secondsRemaining / 60;
                int seconds = secondsRemaining % 60;
                System.out.printf("⏱️ [計時更新] %02d:%02d\n", minutes, seconds);
            }

            @Override
            public void onFinished() {
                System.out.println("🎉 [時間到] 恭喜完成這段時間的專注！");
            }
        };

        // 建立引擎並掛載 Listener
        FocusEngine engine = new FocusEngine(listener);

        // 測試 1: 倒數計時模式 (設定 5 秒鐘做測試)
        System.out.println("\n[測試 1] 開始 5 秒鐘的倒數計時...");
        engine.start(5);

        // 讓主執行緒等待 7 秒，觀察倒數結束後的行為
        try {
            Thread.sleep(7000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 測試 2: 正向碼表模式
        System.out.println("\n[測試 2] 開始正向碼表計時...");
        engine.startStopwatch();

        // 讓主執行緒等待 5 秒觀察碼表
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 手動停止碼表並關閉引擎
        System.out.println("\n🛑 停止引擎並關閉程式...");
        engine.shutdown();
        System.out.println("=== 測試結束 ===");
    }
}
