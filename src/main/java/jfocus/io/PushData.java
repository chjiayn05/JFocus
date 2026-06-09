package jfocus.io;

import java.time.LocalDateTime;
import java.util.Objects;

import jfocus.activity.ActivityRecord;
import jfocus.activity.ActivityRepository;
import jfocus.activity.JdbcActivityRepository;
import jfocus.db.DatabaseCore;
import jfocus.engine.FocusSessionRecord;
import jfocus.engine.FocusSessionRepository;
import jfocus.engine.JdbcFocusSessionRepository;
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
    private final FocusSessionRepository focusSessionRepository;

    /**
     * 使用預設 JDBC repository 建立寫入服務。
     */
    public PushData() {
        DatabaseCore databaseCore = new DatabaseCore();
        this.activityRepository = new JdbcActivityRepository(databaseCore);
        this.todoRepository = new JdbcTodoRepository(databaseCore);
        this.focusSessionRepository = new JdbcFocusSessionRepository(databaseCore);
    }

    /**
     * 使用指定 repository 建立寫入 facade。
     *
     * @param activityRepository     活動資料 repository
     * @param todoRepository         待辦事項 repository
     * @param focusSessionRepository 專注 Session repository
     */
    public PushData(ActivityRepository activityRepository, TodoRepository todoRepository,
            FocusSessionRepository focusSessionRepository) {
        this.activityRepository = Objects.requireNonNull(activityRepository, "activityRepository cannot be null");
        this.todoRepository = Objects.requireNonNull(todoRepository, "todoRepository cannot be null");
        this.focusSessionRepository = Objects.requireNonNull(focusSessionRepository, "focusSessionRepository cannot be null");
    }

    /**
     * 新增一筆活動紀錄。
     *
     * @param app       應用程式名稱
     * @param title     視窗標題，可為 null
     * @param startTime 開始時間
     * @param endTime   結束時間
     * @param isFocus   是否為專注活動
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
        activityRepository.saveActivity(activity);
        System.out.println("成功存入紀錄: [" + app + "] " + title);
    }

    /**
     * 新增一筆專注 Session 紀錄。
     *
     * @param record 專注 Session 數據
     */
    public void insertFocusSession(FocusSessionRecord record) {
        focusSessionRepository.saveFocusSession(record);
    }

    /**
     * 新增一筆待辦事項。
     *
     * @param task     待辦事情
     * @param deadline 時限，可為 null
     * @param isDone   是否完成
     * @param notes    備註，可為 null
     */
    public void insertTodo(String task, LocalDateTime deadline, boolean isDone, String notes) {
        insertTodo(task, deadline, isDone, notes, "未分類");
    }

    /**
     * 新增一筆待辦事項（含科目）。
     *
     * @param task     待辦事情
     * @param deadline 時限，可為 null
     * @param isDone   是否完成
     * @param notes    備註，可為 null
     * @param subject  科目，可為 null
     */
    public void insertTodo(String task, LocalDateTime deadline, boolean isDone, String notes, String subject) {
        TodoRecord todo = new TodoRecord(0, task, deadline, isDone, notes, subject);
        todoRepository.saveTodo(todo);
        System.out.println("新增待辦: " + task);
    }

    /**
     * 更新一筆待辦事項的所有欄位。
     *
     * @param id       待辦事項的 id
     * @param task     待辦事情
     * @param deadline 時限，可為 null
     * @param isDone   是否完成
     * @param notes    備註，可為 null
     */
    public void updateTodo(int id, String task, LocalDateTime deadline, boolean isDone, String notes) {
        updateTodo(id, task, deadline, isDone, notes, "未分類");
    }

    /**
     * 更新一筆待辦事項的所有欄位（含科目）。
     *
     * @param id       待辦事項的 id
     * @param task     待辦事情
     * @param deadline 時限，可為 null
     * @param isDone   是否完成
     * @param notes    備註，可為 null
     * @param subject  科目，可為 null
     */
    public void updateTodo(int id, String task, LocalDateTime deadline, boolean isDone, String notes, String subject) {
        TodoRecord todo = new TodoRecord(id, task, deadline, isDone, notes, subject);
        todoRepository.updateTodo(todo);
        System.out.println("更新待辦 id=" + id + ": " + task);
    }

    /**
     * 刪除一筆待辦事項。
     *
     * @param id 待辦事項的 id
     */
    public void deleteTodo(int id) {
        todoRepository.deleteTodo(id);
        System.out.println("刪除待辦 id=" + id);
    }
}
