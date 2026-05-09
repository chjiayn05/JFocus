package jfocus.engine;

import java.time.LocalDateTime;

/**
 * 記錄單次專注/計時 session 的數據，準備傳送給資料庫。
 */
public class FocusSessionRecord {
    public String sessionId;
    public String subject;
    public LocalDateTime startTime;
    public LocalDateTime endTime;
    public int expectedDurationSeconds;
    public int actualDurationSeconds;
    
    // 如果未來需要紀錄這段期間內被扣除了多少閒置時間，也可以存在這裡
    public int totalIdleSecondsDeducted = 0; 
}
