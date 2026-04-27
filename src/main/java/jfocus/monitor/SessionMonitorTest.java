package jfocus.monitor;

public class SessionMonitorTest {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== 啟動 SessionMonitor 獨立測試 ===");
        System.out.println("請試著隨意開啟、切換或關閉幾個視窗，觀察 Console 輸出的變化...");
        System.out.println("--------------------------------------------------");

        // 1. 建立一個專門用來測試的「接聽器」
        SessionListener testListener = new SessionListener() {
            
            // 🚀 補上這段：測試程式也要能接到「新視窗開啟」的通知
            @Override
            public void onSessionStarted(WindowSession session) {
                System.out.println("🟢 [測試結果] 新視窗開啟: " + session.title);
            }

            // 原本就有的：測試程式接到「視窗關閉」的通知
            @Override
            public void onSessionEnded(WindowSession session) {
                System.out.println("\n[測試結果] 🛑 收到一個結束的視窗 Session！");
                System.out.println("  ▶ 視窗標題: " + session.title);
                System.out.println("  ▶ 程式識別: " + session.processName);
                System.out.println("  ▶ 開始時間: " + session.startTime);
                System.out.println("  ▶ 結束時間: " + session.endTime);
                System.out.println("--------------------------------------------------");
            }
        };

        // 2. 實例化你要測試的 SessionMonitor，並把測試用接聽器裝上去
        SessionMonitor monitor = new SessionMonitor(testListener);

        // 3. 寫一個無窮迴圈，模擬 FocusEngine 裡面的「每秒定期掃描」
        while (true) {
            monitor.scan();
            Thread.sleep(1000);
        }
    }
}