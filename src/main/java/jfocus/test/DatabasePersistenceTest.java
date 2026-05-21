package jfocus.test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;

import jfocus.db.DatabaseCore;
import jfocus.engine.FocusEngine;
import jfocus.engine.FocusListener;
import jfocus.monitor.SessionListener;
import jfocus.monitor.WindowSession;
import jfocus.main.FocusApp;

public class DatabasePersistenceTest {
    public static void main(String[] args) throws Exception {
        System.out.println("====== 開始資料庫持久化測試 ======");

        // 1. 初始化資料庫
        DatabaseCore.initializeDatabase();

        // 2. 清空舊的測試資料 (避免干擾)
        try (Connection conn = new DatabaseCore().getConnection()) {
            conn.createStatement().executeUpdate("DELETE FROM focus_sessions");
            conn.createStatement().executeUpdate("DELETE FROM activities");
            System.out.println("🧹 測試資料表已清空");
        }

        // 3. 初始化 FocusEngine
        // 我們直接模擬視窗的開啟與關閉
        FocusEngine engine = new FocusEngine(new FocusListener() {
            @Override public void onTick(int seconds) {}
            @Override public void onFinished() {}
        });

        // 這裡需要用反射或直接調用，但因為 SessionListener 是匿名內部類，我們可以透過反射取得它，或是直接開啟監控。
        // 但我們可以直接在 engine.start(5) 啟動後，手動觸發事件。
        // 不過更好的做法是直接存取 engine 的 SessionMonitor 或是直接啟動引擎。
        // 為了測試方便，我們可以利用反射取得 engine 內建的 SessionListener，
        // 或是我們可以直接透過反射找到 FocusEngine 內的匿名類別實例。
        // 其實不用，既然 sessionMonitor 已經啟動，我們可以手動將一個 WindowSession 塞入監控器。
        // 為了最精準且不寫複雜反射，我們可以直接在 engine 中觸發 window scan。
        // 但為了直接測試，我們可以直接使用反射來獲取 engine 的 sessionMonitor 並發送事件。
        
        System.out.println("🚀 啟動計時器 (倒數 10 秒) 並自動啟動監控");
        engine.start(10);

        // 取得由引擎內部生成的 Session ID
        String expectedSessionId = FocusApp.getSessionId();
        System.out.println("🔑 生成的 Session ID: " + expectedSessionId);

        // 模擬視窗活動
        WindowSession mockSession = new WindowSession("test_hwnd_123", "chrome.exe", "Google Chrome - Test Title");
        mockSession.startTime = LocalDateTime.now().minusSeconds(5);
        mockSession.endTime = LocalDateTime.now();
        mockSession.isDistracted = false;

        // 我們可以直接模擬調用 PushData 來寫入，但為了測試 FocusEngine 內部的 onSessionEnded 監聽器：
        // 由於 sessionMonitor 是 FocusEngine 的私有屬性，我們透過反射取得它並調用 listener。
        java.lang.reflect.Field monitorField = FocusEngine.class.getDeclaredField("sessionMonitor");
        monitorField.setAccessible(true);
        jfocus.monitor.SessionMonitor monitor = (jfocus.monitor.SessionMonitor) monitorField.get(engine);

        java.lang.reflect.Field listenerField = jfocus.monitor.SessionMonitor.class.getDeclaredField("listener");
        listenerField.setAccessible(true);
        jfocus.monitor.SessionListener listener = (jfocus.monitor.SessionListener) listenerField.get(monitor);

        System.out.println("📥 模擬發送 onSessionStarted 與 onSessionEnded 事件...");
        listener.onSessionStarted(mockSession);
        listener.onSessionEnded(mockSession);

        // 5. 停止引擎，觸發 FocusSessionRecord 的儲存
        System.out.println("⏹️ 停止計時器...");
        engine.shutdown();

        // 6. 查詢資料庫驗證
        System.out.println("🔍 查詢資料庫驗證資料...");
        try (Connection conn = new DatabaseCore().getConnection()) {
            // 驗證 focus_sessions
            String selectSession = "SELECT * FROM focus_sessions WHERE session_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(selectSession)) {
                pstmt.setString(1, expectedSessionId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        System.out.println("✅ 成功在 focus_sessions 找到紀錄！");
                        System.out.println("   - Session ID: " + rs.getString("session_id"));
                        System.out.println("   - 科目: " + rs.getString("subject"));
                        System.out.println("   - 預期時間: " + rs.getInt("expected_duration_seconds") + "s");
                        System.out.println("   - 實際時間: " + rs.getInt("actual_duration_seconds") + "s");
                    } else {
                        System.err.println("❌ 錯誤：未在 focus_sessions 找到紀錄！");
                    }
                }
            }

            // 驗證 activities
            String selectActivity = "SELECT * FROM activities WHERE session_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(selectActivity)) {
                pstmt.setString(1, expectedSessionId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        System.out.println("✅ 成功在 activities 找到對應活動紀錄！");
                        System.out.println("   - 應用名稱: " + rs.getString("app_name"));
                        System.out.println("   - 視窗標題: " + rs.getString("window_title"));
                        System.out.println("   - 專注狀態 (is_focus): " + rs.getInt("is_focus"));
                        System.out.println("   - 所屬 Session ID: " + rs.getString("session_id"));
                    } else {
                        System.err.println("❌ 錯誤：未在 activities 找到對應的活動紀錄！");
                    }
                }
            }
        }

        System.out.println("====== 資料庫持久化測試結束 ======");
    }
}
