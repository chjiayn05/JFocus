package jfocus.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import jfocus.db.DatabaseCore;
import jfocus.todo.JdbcTodoRepository;
import jfocus.todo.TodoRecord;
import jfocus.todo.TodoRepository;

/**
 * 待辦事項分頁視圖。
 */
public class TodoView extends VBox {

    private final TodoRepository todoRepo;
    private final jfocus.subjects.JdbcSubjectRepository subjectRepo;
    private final FocusUI mainApp;

    // UI 元件
    private final VBox listContainer;
    private TextField taskInput;
    private ChoiceBox<String> subjectSelector;
    private DatePicker deadlinePicker;
    private TextField notesInput;

    // 切換與篩選元件
    private boolean showCompleted = false; // false = 進行中, true = 已完成
    private String currentSortOrder = "依截止日"; // "依截止日" 或 "依科目"
    private Button btnActive;
    private Button btnCompleted;
    private ChoiceBox<String> sortSelector;

    private final Map<String, String> subjectColorMap = new HashMap<>();
    private int colorIndex = 0;

    public TodoView(FocusUI mainApp) {
        this.mainApp = mainApp;
        DatabaseCore db = new DatabaseCore();
        this.todoRepo = new JdbcTodoRepository(db);
        this.subjectRepo = new jfocus.subjects.JdbcSubjectRepository(db);

        this.setSpacing(15);
        this.setPadding(new Insets(15));
        this.setAlignment(Pos.TOP_CENTER);
        this.getStyleClass().add("stats-container"); 

        // 1. 新增事項表單卡片
        VBox formCard = createFormCard();
        this.getChildren().add(formCard);

        // 2. 控制面板
        HBox controlPanel = createControlPanel();
        this.getChildren().add(controlPanel);

        // 3. 待辦清單滾動容器
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(false);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        listContainer = new VBox(10);
        listContainer.setAlignment(Pos.TOP_CENTER);
        listContainer.setPadding(new Insets(5, 2, 5, 2));
        scrollPane.setContent(listContainer);

        this.getChildren().add(scrollPane);

        // 4. 初始載入列表
        refreshTodoList();
    }

    private VBox createFormCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("stats-card");
        card.setPadding(new Insets(12));

        Label titleLabel = new Label("新增待辦事項");
        titleLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; ");

        // 任務名稱輸入
        taskInput = new TextField();
        taskInput.setPromptText("輸入代辦事項...");
        taskInput.getStyleClass().add("text-field");

        // 科目與截止日 HBox
        HBox row2 = new HBox(10);
        row2.setAlignment(Pos.CENTER_LEFT);

        subjectSelector = new ChoiceBox<>();
        subjectSelector.setPrefWidth(120);
        subjectSelector.getStyleClass().addAll("choice-box", "todo-subject-choice");
        subjectSelector.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || "未分類".equals(newVal)) {
                if (!subjectSelector.getStyleClass().contains("placeholder-active")) {
                    subjectSelector.getStyleClass().add("placeholder-active");
                }
            } else {
                subjectSelector.getStyleClass().remove("placeholder-active");
            }
        });
        subjectSelector.getStyleClass().add("placeholder-active");

        deadlinePicker = new DatePicker();
        deadlinePicker.setPromptText("選擇截止日期 (選填)");
        deadlinePicker.setPrefWidth(180);
        deadlinePicker.getStyleClass().add("date-picker");

        row2.getChildren().addAll(subjectSelector, deadlinePicker);

        // 備註輸入
        notesInput = new TextField();
        notesInput.setPromptText("備註 / 說明 (選填)...");
        notesInput.getStyleClass().add("text-field");

        // 新增按鈕
        Button addBtn = new Button("+ 新增事項");
        addBtn.setMaxWidth(Double.MAX_VALUE);
        addBtn.setStyle("-fx-background-color: -my-btn-primary-color; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8; -fx-background-radius: 8;");
        addBtn.setOnAction(e -> handleAddTodo());

        // Enter 直接新增
        taskInput.setOnAction(e -> handleAddTodo());
        notesInput.setOnAction(e -> handleAddTodo());

        card.getChildren().addAll(titleLabel, taskInput, row2, notesInput, addBtn);
        return card;
    }

    private HBox createControlPanel() {
        HBox panel = new HBox(10);
        panel.setAlignment(Pos.CENTER_LEFT);

        btnActive = new Button("進行中");
        btnCompleted = new Button("已完成");

        btnActive.setPrefWidth(85);
        btnCompleted.setPrefWidth(85);

        btnActive.setOnAction(e -> {
            showCompleted = false;
            updateFilterButtons();
            refreshTodoList();
        });

        btnCompleted.setOnAction(e -> {
            showCompleted = true;
            updateFilterButtons();
            refreshTodoList();
        });

        updateFilterButtons();

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label sortLabel = new Label("排序：");
        sortLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");

        sortSelector = new ChoiceBox<>();
        sortSelector.getItems().addAll("依截止日", "依科目");
        sortSelector.setValue("依截止日");
        sortSelector.setPrefWidth(90);
        sortSelector.getStyleClass().add("choice-box");
        sortSelector.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                currentSortOrder = newVal;
                refreshTodoList();
            }
        });

        panel.getChildren().addAll(btnActive, btnCompleted, spacer, sortLabel, sortSelector);
        return panel;
    }

    private void updateFilterButtons() {
        if (!showCompleted) {
            btnActive.setStyle("-fx-background-color: -my-btn-primary-color; -fx-text-fill: white; -fx-font-size: 12px; -fx-padding: 6 12;");
            btnCompleted.setStyle("-fx-background-color: -my-btn-secondary-color; -fx-text-fill: white; -fx-font-size: 12px; -fx-padding: 6 12;");
        } else {
            btnActive.setStyle("-fx-background-color: -my-btn-secondary-color; -fx-text-fill: white; -fx-font-size: 12px; -fx-padding: 6 12;");
            btnCompleted.setStyle("-fx-background-color: -my-btn-primary-color; -fx-text-fill: white; -fx-font-size: 12px; -fx-padding: 6 12;");
        }
    }

    private void handleAddTodo() {
        String task = taskInput.getText();
        if (task == null || task.isBlank()) {
            new MiniDialog.Builder(this.getScene().getWindow())
                .type(MiniDialog.Type.WARNING)
                .title("輸入提示")
                .message("請輸入待辦事項的內容！")
                .primaryBtn("確定", null)
                .show();
            return;
        }

        String subject = subjectSelector.getValue();
        if (subject == null || "未分類".equals(subject)) {
            subject = "未分類";
        }

        LocalDateTime deadline = null;
        if (deadlinePicker.getValue() != null) {
            deadline = deadlinePicker.getValue().atStartOfDay();
        }

        String notes = notesInput.getText();
        if (notes != null && notes.isBlank()) {
            notes = null;
        }

        TodoRecord todo = new TodoRecord(0, task, deadline, false, notes, subject);
        todoRepo.saveTodo(todo);

        // 清空欄位
        taskInput.clear();
        notesInput.clear();
        deadlinePicker.setValue(null);

        // 重新整理
        refreshTodoList();
    }

    public void refreshTodoList() {
        // 重設科目顏色快取，以便隨著主題切換動態讀取正確的配色
        subjectColorMap.clear();
        colorIndex = 0;

        // 1. 重新載入科目選單
        refreshSubjectSelector();

        // 2. 更新篩選按鈕狀態與樣式 (相容主題切換)
        updateFilterButtons();

        // 3. 清空列表容器
        listContainer.getChildren().clear();

        // 4. 取得所有待辦事項
        List<TodoRecord> allTodos = todoRepo.getAllTodos();

        // 5. 進行已完成/未完成過濾
        List<TodoRecord> filteredTodos = new ArrayList<>();
        for (TodoRecord todo : allTodos) {
            if (todo.isDone() == showCompleted) {
                filteredTodos.add(todo);
            }
        }

        if (filteredTodos.isEmpty()) {
            String msg = showCompleted ? "目前沒有已完成的事項！" : "目前沒有待辦事項，來新增一個吧！";
            Label placeholder = new Label(msg);
            placeholder.getStyleClass().add("stats-soft-text");
            placeholder.setStyle("-fx-font-size: 13px; -fx-padding: 40;");
            listContainer.getChildren().add(placeholder);
            return;
        }

        // 6. 依排序方式進行排序
        if ("依科目".equals(currentSortOrder)) {
            filteredTodos.sort((a, b) -> {
                String sa = a.subject() != null ? a.subject() : "未分類";
                String sb = b.subject() != null ? b.subject() : "未分類";
                if (sa.equals(sb)) {
                    // 同科目，依截止日排序
                    if (a.deadline() == null && b.deadline() == null) return 0;
                    if (a.deadline() == null) return 1;
                    if (b.deadline() == null) return -1;
                    return a.deadline().compareTo(b.deadline());
                }
                if (sa.equals("未分類")) return 1;
                if (sb.equals("未分類")) return -1;
                return sa.compareTo(sb);
            });
        } else {
            // 預設依截止日排序 (無截止日排最後)
            filteredTodos.sort((a, b) -> {
                if (a.deadline() == null && b.deadline() == null) return 0;
                if (a.deadline() == null) return 1;
                if (b.deadline() == null) return -1;
                return a.deadline().compareTo(b.deadline());
            });
        }

        // 7. 渲染事項
        for (TodoRecord todo : filteredTodos) {
            listContainer.getChildren().add(createTodoRow(todo));
        }
    }

    private void refreshSubjectSelector() {
        String currentSelection = subjectSelector.getValue();
        List<String> items = new ArrayList<>();
        items.add("未分類");
        items.addAll(subjectRepo.findAll());
        subjectSelector.getItems().setAll(items);

        if (items.contains(currentSelection)) {
            subjectSelector.setValue(currentSelection);
        } else {
            subjectSelector.setValue("未分類");
        }
    }

    private HBox createTodoRow(TodoRecord todo) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("stats-card");
        row.setPadding(new Insets(10, 12, 10, 12));

        // 勾選框
        CheckBox checkBox = new CheckBox();
        checkBox.setSelected(todo.isDone());
        checkBox.getStyleClass().add("check-box");
        checkBox.setOnAction(e -> {
            TodoRecord updated = new TodoRecord(
                    todo.id(),
                    todo.task(),
                    todo.deadline(),
                    checkBox.isSelected(),
                    todo.notes(),
                    todo.subject()
            );
            todoRepo.updateTodo(updated);
            javafx.animation.PauseTransition delay = new javafx.animation.PauseTransition(javafx.util.Duration.millis(180));
            delay.setOnFinished(ev -> refreshTodoList());
            delay.play();
        });

        // 任務主體
        VBox textVBox = new VBox(4);
        textVBox.setAlignment(Pos.CENTER_LEFT);

        Label taskLabel = new Label(todo.task());
        taskLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: -my-text-color;");
        if (todo.isDone()) {
            taskLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: -my-stats-soft-text-color;");
        }
        textVBox.getChildren().add(taskLabel);

        // 備註
        if (todo.notes() != null && !todo.notes().isBlank()) {
            Label notesLabel = new Label(todo.notes());
            notesLabel.getStyleClass().add("stats-soft-text");
            notesLabel.setStyle("-fx-font-size: 11px;");
            textVBox.getChildren().add(notesLabel);
        }

        // 標籤
        HBox badges = new HBox(6);
        badges.setAlignment(Pos.CENTER_LEFT);

        // 科目
        Label subjectBadge = new Label(todo.subject());
        String color = getSubjectColor(todo.subject());
        subjectBadge.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-background-radius: 4; -fx-padding: 2 6; -fx-font-size: 10px; -fx-font-weight: bold;");
        badges.getChildren().add(subjectBadge);

        // 截止日
        if (todo.deadline() != null) {
            String dateStr = todo.deadline().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            Label deadlineBadge = new Label("截止: " + dateStr);
            boolean overdue = !todo.isDone() && todo.deadline().toLocalDate().isBefore(LocalDate.now());
            if (overdue) {
                deadlineBadge.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-background-radius: 4; -fx-padding: 2 6; -fx-font-size: 10px; -fx-font-weight: bold;");
            } else {
                deadlineBadge.setStyle("-fx-background-color: rgba(255,255,255,0.08); -fx-text-fill: -my-text-color; -fx-background-radius: 4; -fx-padding: 2 6; -fx-font-size: 10px; -fx-border-color: rgba(255,255,255,0.15); -fx-border-radius: 4; -fx-border-width: 1;");
            }
            badges.getChildren().add(deadlineBadge);
        }

        textVBox.getChildren().add(badges);

        // 伸縮區塊
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 刪除按鈕
        Button deleteBtn = new Button("✕");
        deleteBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: -my-stats-soft-text-color; -fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 2 8; -fx-effect: none;");
        deleteBtn.setOnMouseEntered(e -> deleteBtn.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 2 8; -fx-background-radius: 6; -fx-effect: none;"));
        deleteBtn.setOnMouseExited(e -> deleteBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: -my-stats-soft-text-color; -fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 2 8; -fx-effect: none;"));
        deleteBtn.setOnAction(e -> {
            todoRepo.deleteTodo(todo.id());
            refreshTodoList();
        });

        row.getChildren().addAll(checkBox, textVBox, spacer, deleteBtn);
        return row;
    }

    private String[] getCurrentPalette() {
        String activeTheme = "";
        Scene scene = getScene();
        if (scene != null) {
            for (String sheet : scene.getStylesheets()) {
                if (sheet.contains("PokemonDark")) activeTheme = "Dark";
                else if (sheet.contains("PokemonLight")) activeTheme = "Light";
                else if (sheet.contains("PokemonPurple")) activeTheme = "Purple";
                else if (sheet.contains("PokemonRed")) activeTheme = "Red";
            }
        }

        switch (activeTheme) {
            case "Dark":
                return new String[]{"#e0e0e0", "#bebebe", "#9d9d9d", "#7b7b7b", "#5b5b5b"};
            case "Light":
                return new String[]{"#2c3e50", "#475569", "#64748b", "#94a3b8", "#a3bee3ff"};
            case "Purple":
                return new String[]{"#a44cfd", "#b973ff", "#c99cf9", "#d8b4fe", "#e8d2ff"};
            case "Red":
            default:
                return new String[]{"#7f1d1d", "#991b1b", "#b91c1c", "#dc2626", "#ff4747"};
        }
    }

    private String getSubjectColor(String subject) {
        if (subjectColorMap.containsKey(subject)) {
            return subjectColorMap.get(subject);
        }
        String[] palette = getCurrentPalette();
        String color = palette[colorIndex % palette.length];
        subjectColorMap.put(subject, color);
        colorIndex++;
        return color;
    }
}
