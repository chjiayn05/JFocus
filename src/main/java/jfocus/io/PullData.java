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
    private final ActivityPullService activityPullService;
    private final TodoPullService todoPullService;

    /**
     * 使用預設 JDBC repository 建立查詢服務。
     */
    public PullData() {
        this(new DefaultActivityPullService(), new DefaultTodoPullService());
    }

    /**
     * 以指定服務建立查詢 facade。
     *
     * @param activityPullService 活動資料查詢服務
     * @param todoPullService 待辦事項查詢服務
     */
    public PullData(ActivityPullService activityPullService, TodoPullService todoPullService) {
        this.activityPullService = Objects.requireNonNull(activityPullService, "activityPullService cannot be null");
        this.todoPullService = Objects.requireNonNull(todoPullService, "todoPullService cannot be null");
    }

    /**
     * 僅使用活動資料 repository 建立查詢服務。
     *
     * @param activityRepository 活動資料 repository
     */
    public PullData(ActivityRepository activityRepository) {
        this(new DefaultActivityPullService(activityRepository), new DisabledTodoPullService());
    }

    /**
     * 僅使用待辦事項 repository 建立查詢服務。
     *
     * @param todoRepository 待辦事項 repository
     */
    public PullData(TodoRepository todoRepository) {
        this(new DisabledActivityPullService(), new DefaultTodoPullService(todoRepository));
    }

    /**
     * 使用指定 repository 建立查詢服務。
     *
     * @param activityRepository 活動資料 repository
     * @param todoRepository 待辦事項 repository
     */
    public PullData(ActivityRepository activityRepository, TodoRepository todoRepository) {
        this(new DefaultActivityPullService(activityRepository), new DefaultTodoPullService(todoRepository));
    }

    /**
     * 依日期統計各應用程式的使用總時長。
     *
     * @param date 目標日期，格式為 yyyy-MM-dd
     * @return 以應用程式名稱為鍵、總秒數為值的統計結果
     */
    public Map<String, Integer> getAppUsageByDate(String date) {
        return activityPullService.getAppUsageByDate(parseDate(date, "date"));
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
        return activityPullService.getAppUsageBySession(sessionId);
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
        return activityPullService.getAppUsageByRange(startDate, endDate);
    }

    /**
     * 取得所有待辦事項，依時限升冪排列（無時限的排在最後）。
     *
     * @return 所有待辦事項的清單
     */
    public List<TodoRecord> getAllTodos() {
        return todoPullService.getAllTodos();
    }

    /**
     * 依 id 查詢單筆待辦事項。
     *
     * @param id 目標待辦事項的 id
     * @return 若存在則回傳 {@link Optional} 包裹的紀錄，否則回傳 {@link Optional#empty()}
     */
    public Optional<TodoRecord> getTodoById(int id) {
        return todoPullService.getTodoById(id);
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

    public interface ActivityPullService {
        Map<String, Integer> getAppUsageByDate(LocalDate date);

        Map<String, Integer> getAppUsageBySession(String sessionId);

        Map<String, Integer> getAppUsageByRange(LocalDate startDate, LocalDate endDate);
    }

    public interface TodoPullService {
        List<TodoRecord> getAllTodos();

        Optional<TodoRecord> getTodoById(int id);
    }

    private static class DefaultActivityPullService implements ActivityPullService {
        private final ActivityRepository activityRepository;

        private DefaultActivityPullService() {
            this(new JdbcActivityRepository(new DatabaseCore()));
        }

        private DefaultActivityPullService(ActivityRepository activityRepository) {
            this.activityRepository = Objects.requireNonNull(activityRepository, "activityRepository cannot be null");
        }

        @Override
        public Map<String, Integer> getAppUsageByDate(LocalDate date) {
            return activityRepository.getAppUsageByDate(date);
        }

        @Override
        public Map<String, Integer> getAppUsageBySession(String sessionId) {
            return activityRepository.getAppUsageBySession(sessionId);
        }

        @Override
        public Map<String, Integer> getAppUsageByRange(LocalDate startDate, LocalDate endDate) {
            return activityRepository.getAppUsageByRange(startDate, endDate);
        }
    }

    private static class DefaultTodoPullService implements TodoPullService {
        private final TodoRepository todoRepository;

        private DefaultTodoPullService() {
            this(new JdbcTodoRepository(new DatabaseCore()));
        }

        private DefaultTodoPullService(TodoRepository todoRepository) {
            this.todoRepository = Objects.requireNonNull(todoRepository, "todoRepository cannot be null");
        }

        @Override
        public List<TodoRecord> getAllTodos() {
            return todoRepository.getAllTodos();
        }

        @Override
        public Optional<TodoRecord> getTodoById(int id) {
            return todoRepository.getTodoById(id);
        }
    }

    private static class DisabledActivityPullService implements ActivityPullService {
        @Override
        public Map<String, Integer> getAppUsageByDate(LocalDate date) {
            throw new IllegalStateException("ActivityRepository is not configured");
        }

        @Override
        public Map<String, Integer> getAppUsageBySession(String sessionId) {
            throw new IllegalStateException("ActivityRepository is not configured");
        }

        @Override
        public Map<String, Integer> getAppUsageByRange(LocalDate startDate, LocalDate endDate) {
            throw new IllegalStateException("ActivityRepository is not configured");
        }
    }

    private static class DisabledTodoPullService implements TodoPullService {
        @Override
        public List<TodoRecord> getAllTodos() {
            throw new IllegalStateException("TodoRepository is not configured");
        }

        @Override
        public Optional<TodoRecord> getTodoById(int id) {
            throw new IllegalStateException("TodoRepository is not configured");
        }
    }
}
