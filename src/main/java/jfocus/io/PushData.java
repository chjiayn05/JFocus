package jfocus.io;

import java.time.LocalDateTime;
import java.util.Objects;

import jfocus.activity.ActivityRecord;
import jfocus.activity.ActivityRepository;
import jfocus.activity.JdbcActivityRepository;
import jfocus.db.DatabaseCore;
import jfocus.main.FocusApp;
import jfocus.todo.JdbcTodoRepository;
import jfocus.todo.TodoRecord;
import jfocus.todo.TodoRepository;

/**
 * 提供活動資料與待辦事項的寫入功能。
 */
public class PushData {
    private final ActivityPushService activityPushService;
    private final TodoPushService todoPushService;

    /**
     * 使用預設 JDBC repository 建立寫入服務。
     */
    public PushData() {
        this(new DefaultActivityPushService(), new DefaultTodoPushService());
    }

    /**
     * 以指定服務建立寫入 facade。
     *
     * @param activityPushService 活動資料寫入服務
     * @param todoPushService 待辦事項寫入服務
     */
    public PushData(ActivityPushService activityPushService, TodoPushService todoPushService) {
        this.activityPushService = Objects.requireNonNull(activityPushService, "activityPushService cannot be null");
        this.todoPushService = Objects.requireNonNull(todoPushService, "todoPushService cannot be null");
    }

    /**
     * 使用指定活動資料 repository 建立寫入 facade。
     *
     * @param activityRepository 活動資料 repository
     */
    public PushData(ActivityRepository activityRepository) {
        this(new DefaultActivityPushService(activityRepository), new DisabledTodoPushService());
    }

    /**
     * 使用指定待辦事項 repository 建立寫入 facade。
     *
     * @param todoRepository 待辦事項 repository
     */
    public PushData(TodoRepository todoRepository) {
        this(new DisabledActivityPushService(), new DefaultTodoPushService(todoRepository));
    }

    /**
     * 使用指定 repository 建立寫入 facade。
     *
     * @param activityRepository 活動資料 repository
     * @param todoRepository 待辦事項 repository
     */
    public PushData(ActivityRepository activityRepository, TodoRepository todoRepository) {
        this(new DefaultActivityPushService(activityRepository), new DefaultTodoPushService(todoRepository));
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
        activityPushService.insertActivity(app, title, startTime, endTime, isFocus);
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
        todoPushService.insertTodo(task, deadline, isDone, notes);
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
        todoPushService.updateTodo(id, task, deadline, isDone, notes);
    }

    /**
     * 刪除一筆待辦事項。
     *
     * @param id 待辦事項的 id
     */
    public void deleteTodo(int id) {
        todoPushService.deleteTodo(id);
    }

    public interface ActivityPushService {
        void insertActivity(String app, String title, LocalDateTime startTime,
                LocalDateTime endTime, boolean isFocus);
    }

    public interface TodoPushService {
        void insertTodo(String task, LocalDateTime deadline, boolean isDone, String notes);

        void updateTodo(int id, String task, LocalDateTime deadline, boolean isDone, String notes);

        void deleteTodo(int id);
    }

    private static class DefaultActivityPushService implements ActivityPushService {
        private final ActivityRepository activityRepository;

        private DefaultActivityPushService() {
            this(new JdbcActivityRepository(new DatabaseCore()));
        }

        private DefaultActivityPushService(ActivityRepository activityRepository) {
            this.activityRepository = Objects.requireNonNull(activityRepository, "activityRepository cannot be null");
        }

        @Override
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
            System.out.println("💾 成功存入紀錄: [" + app + "] " + title);
        }
    }

    private static class DefaultTodoPushService implements TodoPushService {
        private final TodoRepository todoRepository;

        private DefaultTodoPushService() {
            this(new JdbcTodoRepository(new DatabaseCore()));
        }

        private DefaultTodoPushService(TodoRepository todoRepository) {
            this.todoRepository = Objects.requireNonNull(todoRepository, "todoRepository cannot be null");
        }

        @Override
        public void insertTodo(String task, LocalDateTime deadline, boolean isDone, String notes) {
            TodoRecord todo = new TodoRecord(0, task, deadline, isDone, notes);
            todoRepository.saveTodo(todo);
            System.out.println("💾 新增待辦: " + task);
        }

        @Override
        public void updateTodo(int id, String task, LocalDateTime deadline, boolean isDone, String notes) {
            TodoRecord todo = new TodoRecord(id, task, deadline, isDone, notes);
            todoRepository.updateTodo(todo);
            System.out.println("✏️ 更新待辦 id=" + id + ": " + task);
        }

        @Override
        public void deleteTodo(int id) {
            todoRepository.deleteTodo(id);
            System.out.println("🗑️ 刪除待辦 id=" + id);
        }
    }

    private static class DisabledActivityPushService implements ActivityPushService {
        @Override
        public void insertActivity(String app, String title, LocalDateTime startTime,
                LocalDateTime endTime, boolean isFocus) {
            throw new IllegalStateException("ActivityRepository is not configured");
        }
    }

    private static class DisabledTodoPushService implements TodoPushService {
        @Override
        public void insertTodo(String task, LocalDateTime deadline, boolean isDone, String notes) {
            throw new IllegalStateException("TodoRepository is not configured");
        }

        @Override
        public void updateTodo(int id, String task, LocalDateTime deadline, boolean isDone, String notes) {
            throw new IllegalStateException("TodoRepository is not configured");
        }

        @Override
        public void deleteTodo(int id) {
            throw new IllegalStateException("TodoRepository is not configured");
        }
    }
}
