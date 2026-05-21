package jfocus.ui;

import java.io.IOException;
import java.util.List;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import jfocus.ai.TrainingDataResetter;
import jfocus.ai.distraction.DistractionHandlingMode;
import jfocus.ai.distraction.JdbcDistractionModeRepository;
import jfocus.ai.rules.KeywordRule;
import jfocus.ai.rules.RuleListType;
import jfocus.ai.rules.SqliteDistractionRuleRepository;
import jfocus.db.DatabaseCore;
import jfocus.settings.DistractionSettings;
import jfocus.settings.JdbcDistractionSettingsRepository;
import jfocus.subjects.JdbcSubjectRepository;

public class SettingsView extends VBox {

    private final FocusUI mainApp;
    private final JdbcSubjectRepository subjectRepo;
    private final SqliteDistractionRuleRepository ruleRepo;
    private final JdbcDistractionModeRepository modeRepo;
    private final JdbcDistractionSettingsRepository distractionSettingsRepo;

    private final ListView<String> subjectList = new ListView<>();
    private final ListView<String> whiteList = new ListView<>();
    private final ListView<String> blackList = new ListView<>();
    private final TextField subjectInput = new TextField();
    private final TextField whiteInput = new TextField();
    private final TextField blackInput = new TextField();
    private final Label statusLabel = new Label("");

    public SettingsView(FocusUI mainApp) {
        this.mainApp = mainApp;
        DatabaseCore db = new DatabaseCore();
        this.subjectRepo = new JdbcSubjectRepository(db);
        this.ruleRepo = new SqliteDistractionRuleRepository(db);
        this.modeRepo = new JdbcDistractionModeRepository(db);
        this.distractionSettingsRepo = new JdbcDistractionSettingsRepository(db);
        buildUI();
        refresh();
    }

    private void buildUI() {
        // 選中時自動填入 TextField
        bindSelectionToInput(subjectList, subjectInput);
        bindSelectionToInput(whiteList,   whiteInput);
        bindSelectionToInput(blackList, blackInput);

        VBox subjectSection = buildSection("科目管理", subjectList, subjectInput,
            () -> {  // 新增
                String val = subjectInput.getText().trim();
                if (val.isBlank()) return;
                subjectRepo.add(val);
                subjectInput.clear();
                refreshSubjects();
            },
            () -> {  // 編輯
                String sel = subjectList.getSelectionModel().getSelectedItem();
                String val = subjectInput.getText().trim();
                if (sel == null || val.isBlank() || val.equals(sel)) return;
                subjectRepo.rename(sel, val);
                subjectInput.clear();
                refreshSubjects();
            },
            () -> {  // 刪除
                String sel = subjectList.getSelectionModel().getSelectedItem();
                if (sel == null) return;
                subjectRepo.delete(sel);
                subjectInput.clear();
                refreshSubjects();
            }
        );

        VBox whiteSection = buildSection("白名單", whiteList, whiteInput,
            () -> {
                String val = whiteInput.getText().trim();
                if (val.isBlank()) return;
                try { ruleRepo.saveRule(RuleListType.WHITELIST, new KeywordRule(val)); } catch (IllegalArgumentException ignored) {}
                whiteInput.clear();
                refreshList(RuleListType.WHITELIST);
            },
            () -> {
                String sel = whiteList.getSelectionModel().getSelectedItem();
                String val = whiteInput.getText().trim();
                if (sel == null || val.isBlank() || val.equals(sel)) return;
                ruleRepo.deleteRule(RuleListType.WHITELIST, new KeywordRule(sel));
                try { ruleRepo.saveRule(RuleListType.WHITELIST, new KeywordRule(val)); } catch (IllegalArgumentException ignored) {}
                whiteInput.clear();
                refreshList(RuleListType.WHITELIST);
            },
            () -> {
                String sel = whiteList.getSelectionModel().getSelectedItem();
                if (sel == null) return;
                ruleRepo.deleteRule(RuleListType.WHITELIST, new KeywordRule(sel));
                whiteInput.clear();
                refreshList(RuleListType.WHITELIST);
            }
        );

        VBox blackSection = buildSection("黑名單", blackList, blackInput,
            () -> {
                String val = blackInput.getText().trim();
                if (val.isBlank()) return;
                try { ruleRepo.saveRule(RuleListType.BLACKLIST, new KeywordRule(val)); } catch (IllegalArgumentException ignored) {}
                blackInput.clear();
                refreshList(RuleListType.BLACKLIST);
            },
            () -> {
                String sel = blackList.getSelectionModel().getSelectedItem();
                String val = blackInput.getText().trim();
                if (sel == null || val.isBlank() || val.equals(sel)) return;
                ruleRepo.deleteRule(RuleListType.BLACKLIST, new KeywordRule(sel));
                try { ruleRepo.saveRule(RuleListType.BLACKLIST, new KeywordRule(val)); } catch (IllegalArgumentException ignored) {}
                blackInput.clear();
                refreshList(RuleListType.BLACKLIST);
            },
            () -> {
                String sel = blackList.getSelectionModel().getSelectedItem();
                if (sel == null) return;
                ruleRepo.deleteRule(RuleListType.BLACKLIST, new KeywordRule(sel));
                blackInput.clear();
                refreshList(RuleListType.BLACKLIST);
            }
        );

        VBox content = new VBox(16, buildBehaviorSection(), subjectSection, whiteSection, blackSection, buildAiSection());
        content.setPadding(new Insets(16));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");
        getChildren().add(scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
    }

    private VBox buildBehaviorSection() {
        // ── DistractionHandlingMode ──
        DistractionHandlingMode currentMode = modeRepo.loadMode(DistractionHandlingMode.WARN_USER);
        ToggleButton warnBtn  = new ToggleButton("警告提示");
        ToggleButton closeBtn = new ToggleButton("強制關閉");
        ToggleGroup modeGroup = new ToggleGroup();
        warnBtn.setToggleGroup(modeGroup);
        closeBtn.setToggleGroup(modeGroup);
        warnBtn.getStyleClass().addAll("segment-btn", "segment-btn-left");
        closeBtn.getStyleClass().addAll("segment-btn", "segment-btn-right");
        (currentMode == DistractionHandlingMode.WARN_USER ? warnBtn : closeBtn).setSelected(true);
        modeGroup.selectedToggleProperty().addListener((obs, old, nw) -> {
            if (nw == null) { old.setSelected(true); return; }
            modeRepo.saveMode(nw == warnBtn ? DistractionHandlingMode.WARN_USER : DistractionHandlingMode.CLOSE_DISTRACTION);
        });

        Label modeLabel = new Label("分心處理模式");
        modeLabel.getStyleClass().add("settings-toggle-label");
        Region modeSpacer = new Region();
        HBox.setHgrow(modeSpacer, Priority.ALWAYS);
        HBox modeRow = new HBox(modeLabel, modeSpacer, new HBox(warnBtn, closeBtn));
        modeRow.setAlignment(Pos.CENTER_LEFT);

        // ── System Notifications ──
        DistractionSettings ds = distractionSettingsRepo.loadSettings();
        ToggleSwitch notifToggle = new ToggleSwitch();
        notifToggle.setSelected(ds.systemNotificationsEnabled());
        notifToggle.selectedProperty().addListener((obs, old, val) ->
                distractionSettingsRepo.saveSystemNotificationsEnabled(val));

        Label notifLabel = new Label("系統通知");
        notifLabel.getStyleClass().add("settings-toggle-label");
        Region notifSpacer = new Region();
        HBox.setHgrow(notifSpacer, Priority.ALWAYS);
        HBox notifRow = new HBox(notifLabel, notifSpacer, notifToggle);
        notifRow.setAlignment(Pos.CENTER_LEFT);

        VBox section = new VBox(14, modeRow, notifRow);
        section.getStyleClass().add("settings-card");
        section.setPadding(new Insets(14));
        return section;
    }

    private void bindSelectionToInput(ListView<String> lv, TextField tf) {
        lv.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null) {
                tf.setText(sel);
            }
        });
        // EventFilter 在 selection 更新前攔截：若點到已選中的 cell 則取消選取
        lv.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            Node node = e.getPickResult().getIntersectedNode();
            while (node != null && !(node instanceof ListCell)) node = node.getParent();
            if (node instanceof ListCell<?> cell && !cell.isEmpty() && cell.isSelected()) {
                tf.setText("");
                lv.getSelectionModel().clearSelection();
                e.consume();
            }
        });
    }

    private VBox buildSection(String title, ListView<String> lv, TextField input,
                              Runnable onAdd, Runnable onEdit, Runnable onDelete) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("settings");

        lv.getStyleClass().add("settings-list");
        lv.setPrefHeight(130);
        lv.addEventHandler(ScrollEvent.ANY, javafx.event.Event::consume);


        VBox stripes = new VBox();
        stripes.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        for (int i = 0; i < 8; i++) {
            HBox row = new HBox();
            row.getStyleClass().add(i % 2 == 0 ? "settings-list-row-even" : "settings-list-row-odd");
            row.setPrefHeight(26);
            row.setMaxWidth(Double.MAX_VALUE);
            stripes.getChildren().add(row);
        }
        lv.setPlaceholder(new StackPane(stripes));
        input.setPromptText("輸入後新增或編輯...");

        Button addBtn = new Button("新增");
        Button editBtn = new Button("編輯");
        Button deleteBtn = new Button("刪除");

        addBtn.getStyleClass().add("alert-btn-primary");
        editBtn.getStyleClass().add("alert-btn-primary");
        deleteBtn.getStyleClass().add("alert-btn-danger");

        addBtn.setMaxWidth(Double.MAX_VALUE);
        editBtn.setMaxWidth(Double.MAX_VALUE);
        deleteBtn.setMaxWidth(Double.MAX_VALUE);

        addBtn.disableProperty().bind(
            lv.getSelectionModel().selectedItemProperty().isNotNull()
        );
        editBtn.disableProperty().bind(
            lv.getSelectionModel().selectedItemProperty().isNull()
        );
        deleteBtn.disableProperty().bind(
            lv.getSelectionModel().selectedItemProperty().isNull()
        );

        addBtn.setOnAction(e -> onAdd.run());
        editBtn.setOnAction(e -> onEdit.run());
        deleteBtn.setOnAction(e -> onDelete.run());

        // Enter on input → 新增
        input.setOnAction(e -> onAdd.run());

        HBox btnRow = new HBox(8, addBtn, editBtn, deleteBtn);
        HBox.setHgrow(addBtn, Priority.ALWAYS);
        HBox.setHgrow(editBtn, Priority.ALWAYS);
        HBox.setHgrow(deleteBtn, Priority.ALWAYS);
        btnRow.setAlignment(Pos.CENTER);

        VBox section = new VBox(8, titleLabel, lv, input, btnRow);
        section.getStyleClass().add("settings-card");
        section.setPadding(new Insets(14));
        return section;
    }

    private VBox buildAiSection() {
        Label titleLabel = new Label("分心偵測模型");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        statusLabel.setStyle("-fx-font-size: 13px;");
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        statusLabel.setAlignment(Pos.CENTER_RIGHT);

        HBox titleRow = new HBox(titleLabel, statusLabel);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Button resetBtn = new Button("重置訓練資料");
        resetBtn.getStyleClass().add("alert-btn-danger");
        resetBtn.setMaxWidth(Double.MAX_VALUE);
        resetBtn.setOnAction(e -> {
            new MiniDialog.Builder(resetBtn.getScene().getWindow())
                .type(MiniDialog.Type.DANGER)
                .title("重置確認")
                .message("確定要重置訓練資料？\n這會刪除 model.bin 並用預設訓練資料重新訓練。")
                .primaryBtn("取消", null)
                .dangerBtn("確定重置", () -> {
                    resetBtn.setDisable(true);
                    statusLabel.setText("重置中，請稍候...");
                    new Thread(() -> {
                        try {
                            TrainingDataResetter.resetDefaultTrainingDataAndTrain();
                            Platform.runLater(() -> { statusLabel.setText("重置完成！"); resetBtn.setDisable(false); });
                        } catch (IOException ex) {
                            Platform.runLater(() -> { statusLabel.setText("失敗：" + ex.getMessage()); resetBtn.setDisable(false); });
                        }
                    }).start();
                })
                .show();
        });

        VBox section = new VBox(10, titleRow, resetBtn);
        section.getStyleClass().add("settings-card");
        section.setPadding(new Insets(14));
        return section;
    }

    public void refresh() {
        refreshSubjects();
        refreshList(RuleListType.WHITELIST);
        refreshList(RuleListType.BLACKLIST);
    }

    private void refreshSubjects() {
        String sel = subjectList.getSelectionModel().getSelectedItem();
        List<String> items = subjectRepo.findAll();
        subjectList.setItems(FXCollections.observableArrayList(items));
        if (sel != null && items.contains(sel)) subjectList.getSelectionModel().select(sel);
    }

    private void refreshList(RuleListType listType) {
        ListView<String> lv = listType == RuleListType.WHITELIST ? whiteList : blackList;
        String sel = lv.getSelectionModel().getSelectedItem();
        List<String> items = ruleRepo.getRules(listType).stream().map(KeywordRule::keyword).toList();
        lv.setItems(FXCollections.observableArrayList(items));
        if (sel != null && items.contains(sel)) lv.getSelectionModel().select(sel);
    }
}
