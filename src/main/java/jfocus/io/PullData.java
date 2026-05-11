package jfocus.io;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import jfocus.activity.ActivityRepository;
import jfocus.activity.JdbcActivityRepository;
import jfocus.db.DatabaseCore;
import jfocus.todo.JdbcTodoRepository;
import jfocus.todo.TodoRecord;
import jfocus.todo.TodoRepository;

/**
 * 提供活動資料與待辦事項的查詢。
 */
public class PullData {
    private final ActivityRepository activityRepository;
    private final TodoRepository todoRepository;

    /**
     * 使用預設 JDBC repository 建立查詢服務。
     */
    public PullData() {
        DatabaseCore databaseCore = new DatabaseCore();
        this.activityRepository = new JdbcActivityRepository(databaseCore);
        this.todoRepository = new JdbcTodoRepository(databaseCore);
    }

    /**
     * 僅使用活動資料 repository 建立查詢服務。
     *
     * @param activityRepository 活動資料 repository
     */
    public PullData(ActivityRepository activityRepository) {
        this.activityRepository = Objects.requireNonNull(activityRepository, "activityRepository cannot be null");
        this.todoRepository = null;
    }

    /**
     * 僅使用待辦事項 repository 建立查詢服務。
     *
     * @param todoRepository 待辦事項 repository
     */
    public PullData(TodoRepository todoRepository) {
        this.activityRepository = null;
        this.todoRepository = Objects.requireNonNull(todoRepository, "todoRepository cannot be null");
    }

    /**
     * 使用指定 repository 建立查詢服務。
     *
     * @param activityRepository 活動資料 repository
     * @param todoRepository 待辦事項 repository
     */
    public PullData(ActivityRepository activityRepository, TodoRepository todoRepository) {
        this.activityRepository = Objects.requireNonNull(activityRepository, "activityRepository cannot be null");
        this.todoRepository = Objects.requireNonNull(todoRepository, "todoRepository cannot be null");
    }

    /**
     * 依日期統計各應用程式的使用總時長。
     *
     * @param date 目標日期，格式為 yyyy-MM-dd
     * @return 以應用程式名稱為鍵、總秒數為值的統計結果
     */
    public Map<String, Integer> getAppUsageByDate(String date) {
        return requireActivityRepository().getAppUsageByDate(parseDate(date, "date"));
    }

    /**
     * 依 session ID 統計各應用程式的使用總時長。
     *
     * @param sessionId 目標 session ID
     * @return 以應用程式名稱為鍵、總秒數為值的統計結果
     */
    public Map<String, Integer> getAppUsageBySession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId cannot be null or blank");
        }
        return requireActivityRepository().getAppUsageBySession(sessionId);
    }

    /**
     * 依日期區間統計各應用程式的使用總時長。
     *
     * @param dateStart 起始日期，格式為 yyyy-MM-dd
     * @param dateEnd 結束日期，格式為 yyyy-MM-dd
     * @return 以應用程式名稱為鍵、總秒數為值的統計結果
     */
    public Map<String, Integer> getAppUsageByRange(String dateStart, String dateEnd) {
        LocalDate startDate = parseDate(dateStart, "dateStart");
        LocalDate endDate = parseDate(dateEnd, "dateEnd");
        return requireActivityRepository().getAppUsageByRange(startDate, endDate);
    }

    /**
     * 取得所有待辦事項，依時限升冪排列（無時限的排在最後）。
     *
     * @return 所有待辦事項的清單
     */
    public List<TodoRecord> getAllTodos() {
        return requireTodoRepository().getAllTodos();
    }

    /**
     * 依 id 查詢單筆待辦事項。
     *
     * @param id 目標待辦事項的 id
     * @return 若存在則回傳 {@link Optional} 包裹的紀錄，否則回傳 {@link Optional#empty()}
     */
    public Optional<TodoRecord> getTodoById(int id) {
        return requireTodoRepository().getTodoById(id);
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

    private LocalDate parseDate(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }

        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(fieldName + " must use yyyy-MM-dd format", e);
        }
    }
}
