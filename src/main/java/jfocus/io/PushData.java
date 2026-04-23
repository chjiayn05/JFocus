package jfocus.io;

import java.time.LocalDateTime;
import java.util.Objects;

import jfocus.activity.ActivityRecord;
import jfocus.activity.ActivityRepository;
import jfocus.activity.JdbcActivityRepository;
import jfocus.db.DatabaseCore;

/**
 * 提供活動資料寫入功能。
 */
public class PushData {
    private final ActivityRepository repository;

    /**
     * 使用預設 JDBC repository 建立寫入服務。
     */
    public PushData() {
        this(new JdbcActivityRepository(new DatabaseCore()));
    }

    /**
     * 使用指定 repository 建立寫入服務。
     *
     * @param repository 活動資料 repository
     */
    public PushData(ActivityRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
    }

    /**
     * 新增一筆活動紀錄。
     *
     * @param app 應用程式名稱
     * @param title 視窗標題
     * @param duration 使用秒數
     * @param isFocus 是否為專注活動
     */
    public void insertActivity(String app, String title, int duration, boolean isFocus) {
        insertActivity(app, title, duration, isFocus, null, LocalDateTime.now());
    }

    /**
     * 新增一筆帶有 session ID 的活動紀錄。
     *
     * @param app 應用程式名稱
     * @param title 視窗標題
     * @param duration 使用秒數
     * @param isFocus 是否為專注活動
     * @param sessionId 所屬 session ID
     */
    public void insertActivity(String app, String title, int duration, boolean isFocus, String sessionId) {
        insertActivity(app, title, duration, isFocus, sessionId, LocalDateTime.now());
    }

    /**
     * 新增一筆活動紀錄，並指定 session 與開始時間。
     *
     * @param app 應用程式名稱
     * @param title 視窗標題
     * @param duration 使用秒數
     * @param isFocus 是否為專注活動
     * @param sessionId 所屬 session ID
     * @param startTime 開始時間
     */
    public void insertActivity(String app, String title, int duration, boolean isFocus, String sessionId,
            LocalDateTime startTime) {
        ActivityRecord activity = new ActivityRecord(app, title, startTime, duration, isFocus, sessionId);
        repository.saveActivity(activity);
        System.out.println("💾 成功存入紀錄: [" + app + "] " + title);
    }
}
