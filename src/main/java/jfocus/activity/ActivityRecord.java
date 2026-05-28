package jfocus.activity;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 表示一筆活動紀錄。
 */
public record ActivityRecord(
        int id,
        String appName,
        String windowTitle,
        LocalDateTime startTime,
        LocalDateTime endTime,
        boolean focus,
        String sessionId) {

    /**
     * 建立尚未寫入資料庫的活動紀錄。
     *
     * @param appName 應用程式名稱
     * @param windowTitle 視窗標題，可為 null
     * @param startTime 開始時間
     * @param endTime 結束時間
     * @param focus 是否為專注活動
     * @param sessionId 所屬 session ID
     */
    public ActivityRecord(String appName, String windowTitle, LocalDateTime startTime,
            LocalDateTime endTime, boolean focus, String sessionId) {
        this(0, appName, windowTitle, startTime, endTime, focus, sessionId);
    }

    public ActivityRecord {
        appName = requireText(appName, "appName");
        windowTitle = windowTitle == null ? "" : windowTitle;
        startTime = Objects.requireNonNull(startTime, "startTime cannot be null");
        endTime = Objects.requireNonNull(endTime, "endTime cannot be null");
        if (endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("endTime cannot be before startTime");
        }
        sessionId = normalize(sessionId);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
        return value;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
