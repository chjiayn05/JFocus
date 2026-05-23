package jfocus.io;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Objects;

import jfocus.activity.ActivityRecord;
import jfocus.activity.ActivityRepository;
import jfocus.activity.JdbcActivityRepository;
import jfocus.db.DatabaseCore;
import jfocus.db.StorageException;
import jfocus.engine.FocusSessionRecord;
import jfocus.main.FocusApp;
import jfocus.todo.JdbcTodoRepository;
import jfocus.todo.TodoRecord;
import jfocus.todo.TodoRepository;

/**
 * 提供活動資料與待辦事項的寫入功能。
 */
public class PushData {
    private final ActivityRepository activityRepository;
    private final TodoRepository todoRepository;

    /**
     * 使用預設 JDBC repository 建立寫入服務。
     */
    public PushData() {
        DatabaseCore databaseCore = new DatabaseCore();
        this.activityRepository = new JdbcActivityRepository(databaseCore);
        this.todoRepository = new JdbcTodoRepository(databaseCore);
    }

    /**
     * 使用指定活動資料 repository 建立寫入 facade。
     *
     * @param activityRepository 活動資料 repository
     */
    public PushData(ActivityRepository activityRepository) {
        this.activityRepository = Objects.requireNonNull(activityRepository, "activityRepository cannot be null");
        this.todoRepository = null;
    }

    /**
     * 使用指定待辦事項 repository 建立寫入 facade。
     *
     * @param todoRepository 待辦事項 repository
     */
    public PushData(TodoRepository todoRepository) {
        this.activityRepository = null;
        this.todoRepository = Objects.requireNonNull(todoRepository, "todoRepository cannot be null");
    }

    /**
     * 使用指定 repository 建立寫入 facade。
     *
     * @param activityRepository 活動資料 repository
     * @param todoRepository 待辦事項 repository
     */
    public PushData(ActivityRepository activityRepository, TodoRepository todoRepository) {
        this.activityRepository = Objects.requireNonNull(activityRepository, "activityRepository cannot be null");
        this.todoRepository = Objects.requireNonNull(todoRepository, "todoRepository cannot be null");
    }

    /**
     * 新增一筆活動紀錄。
     *
     * @param app 應用程式名稱
     * @param title 視窗標題，可為 null
     * @param startTime 開始時間
     * @param endTime 結束時間
     * @param isFocus 是否為專注活動
     */
    public void insertActivity(String app, String title, LocalDateTime startTime,
            LocalDateTime endTime, boolean isFocus) {
        ActivityRecord activity = new ActivityRecord(
            app,
            title,
            startTime,
            endTime,
            isFocus,
            FocusApp.getSessionId());
        requireActivityRepository().saveActivity(activity);
        System.out.println("成功存入紀錄: [" + app + "] " + title);
    }

    /**
     * 新增一筆專注 Session 紀錄。
     *
     * @param record 專注 Session 數據
     */
    public void insertFocusSession(FocusSessionRecord record) {
        Objects.requireNonNull(record, "record cannot be null");
        String sql = """
            INSERT INTO focus_sessions (
                session_id, subject, start_time, end_time,
                expected_duration_seconds, actual_duration_seconds,
                total_idle_seconds_deducted
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = new DatabaseCore().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, record.sessionId);
            pstmt.setString(2, record.subject != null ? record.subject : "未分類");
            pstmt.setString(3, record.startTime != null ? record.startTime.toString() : LocalDateTime.now().toString());
            pstmt.setString(4, record.endTime != null ? record.endTime.toString() : null);
            pstmt.setInt(5, record.expectedDurationSeconds);
            pstmt.setInt(6, record.actualDurationSeconds);
            pstmt.setInt(7, record.totalIdleSecondsDeducted);
            pstmt.executeUpdate();
            System.out.println("成功存入專注 Session: " + record.sessionId);
        } catch (SQLException e) {
            throw new StorageException("寫入專注 Session 失敗", e);
        }
    }

    /**
     * 新增一筆待辦事項。
     *
     * @param task 待辦事情
     * @param deadline 時限，可為 null
     * @param isDone 是否完成
     * @param notes 備註，可為 null
     */
    public void insertTodo(String task, LocalDateTime deadline, boolean isDone, String notes) {
        TodoRecord todo = new TodoRecord(0, task, deadline, isDone, notes);
        requireTodoRepository().saveTodo(todo);
        System.out.println("新增待辦: " + task);
    }

    /**
     * 更新一筆待辦事項的所有欄位。
     *
     * @param id 待辦事項的 id
     * @param task 待辦事情
     * @param deadline 時限，可為 null
     * @param isDone 是否完成
     * @param notes 備註，可為 null
     */
    public void updateTodo(int id, String task, LocalDateTime deadline, boolean isDone, String notes) {
        TodoRecord todo = new TodoRecord(id, task, deadline, isDone, notes);
        requireTodoRepository().updateTodo(todo);
        System.out.println("更新待辦 id=" + id + ": " + task);
    }

    /**
     * 刪除一筆待辦事項。
     *
     * @param id 待辦事項的 id
     */
    public void deleteTodo(int id) {
        requireTodoRepository().deleteTodo(id);
        System.out.println("刪除待辦 id=" + id);
    }

    private ActivityRepository requireActivityRepository() {
        if (activityRepository == null) {
            throw new IllegalStateException("ActivityRepository is not configured");
        }
        return activityRepository;
    }

    private TodoRepository requireTodoRepository() {
        if (todoRepository == null) {
            throw new IllegalStateException("TodoRepository is not configured");
        }
        return todoRepository;
    }
}
