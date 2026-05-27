package jfocus.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import jfocus.ai.distraction.SystemAwareMediaPauser;
import jfocus.engine.FocusEngine;
import jfocus.monitor.WindowSession;

class DistractedAlert extends FullScreenAlert {
    private static final int MAX_SELECTED_KEYWORDS = 3;
    private static final double PANEL_HEIGHT = 400;
    private static final Object ACTIVE_ALERT_LOCK = new Object();
    private static String activeAlertKey;

    private final WindowSession session;
    private final String alertKey;

    public DistractedAlert(FocusEngine engine, WindowSession session) {
        super(engine, "分心提醒");
        this.session = Objects.requireNonNull(session, "session cannot be null");
        this.alertKey = createAlertKey(session);
    }

    public static void showIfNotShowing(FocusEngine engine, WindowSession session) {
        DistractedAlert alert = new DistractedAlert(engine, session);
        alert.show();
    }

    @Override
    protected void onBeforeShow() {
        SystemAwareMediaPauser.pauseAllMedia();
    }

    @Override
    protected boolean tryAcquire() {
        synchronized (ACTIVE_ALERT_LOCK) {
            if (activeAlertKey != null) {
                return false;
            }
            activeAlertKey = alertKey;
            return true;
        }
    }

    @Override
    protected void release() {
        synchronized (ACTIVE_ALERT_LOCK) {
            if (Objects.equals(activeAlertKey, alertKey)) {
                activeAlertKey = null;
            }
        }
    }

    @Override
    protected void showContent() {
        showWarningPanel();
    }

    private void showWarningPanel() {
        VBox root = createRoot();

        HBox titleRow = createTitleRow("⊖", "偵測到分心！", "alert-title-distraction");
        Label appLabel = createBodyLabel("應用程式：" + safeText(session.processName));
        Label contentLabel = createBodyLabel("內容：" + safeText(session.title));

        Button ignoreButton = createButton("忽略", 80);
        ignoreButton.setOnAction(event -> {
            engine.ignoreWindow(session);
            stage.close();
        });

        Button whitelistButton = createButton("加入白名單", 100);
        whitelistButton.setOnAction(event -> showWhitelistPanel());

        Button closeButton = createButton("關閉視窗", 100);
        closeButton.getStyleClass().add("alert-btn-danger");
        closeButton.setOnAction(event -> {
            stopForegroundWatchdog();
            closeBlockerStages();
            boolean closed = engine.closeDistractingTarget(session);
            if (!closed) {
                System.err.println("無法關閉分心視窗或分頁: " + safeText(session.title));
            }
            stage.close();
        });

        HBox buttonRow = new HBox(8, ignoreButton, whitelistButton, closeButton);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);

        VBox spacer = createSpacer();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        root.getChildren().addAll(titleRow, appLabel, contentLabel, spacer, createDivider(), buttonRow);
        setPanelHeight(root, PANEL_HEIGHT);
        setScene(root);
        Platform.runLater(closeButton::requestFocus);
    }

    private void showWhitelistPanel() {
        VBox root = createRoot();

        HBox titleRow = createTitleRow("⊖", "選擇白名單關鍵字", "alert-title-distraction");
        Label hint = createBodyLabel("最多選擇 " + MAX_SELECTED_KEYWORDS + " 個關鍵字。");
        List<String> keywords = engine.suggestWhitelistKeywordsForSession(session);

        Button backButton = createButton("返回", 80);
        backButton.setOnAction(event -> showWarningPanel());

        Button confirmButton = createButton("確認加入", 100);
        confirmButton.setDisable(true);

        if (keywords.isEmpty()) {
            Label emptyLabel = createBodyLabel("沒有可加入的關鍵字。");
            HBox buttonRow = new HBox(8, backButton);
            buttonRow.setAlignment(Pos.CENTER_RIGHT);
            VBox spacer = createSpacer();
            VBox.setVgrow(spacer, Priority.ALWAYS);
            root.getChildren().addAll(titleRow, hint, emptyLabel, spacer, createDivider(), buttonRow);
            setPanelHeight(root, PANEL_HEIGHT);
            setScene(root);
            return;
        }

        VBox keywordBox = new VBox(8);
        keywordBox.setFillWidth(true);
        List<ToggleButton> keywordButtons = new ArrayList<>();
        for (String keyword : keywords) {
            ToggleButton keywordButton = createKeywordButton(keyword);
            keywordButton.selectedProperty().addListener((obs, wasSelected, isSelected) ->
                    updateKeywordSelectionState(keywordButtons, confirmButton));
            keywordButtons.add(keywordButton);
            keywordBox.getChildren().add(keywordButton);
        }

        ScrollPane scrollPane = new ScrollPane(keywordBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setMinHeight(0);
        scrollPane.setPrefViewportHeight(200);
        scrollPane.getStyleClass().add("edge-to-edge");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        confirmButton.setOnAction(event -> {
            for (ToggleButton keywordButton : keywordButtons) {
                if (keywordButton.isSelected()) {
                    engine.addWhitelistRule(keywordButton.getText());
                }
            }
            stage.close();
        });

        HBox buttonRow = new HBox(8, backButton, confirmButton);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(titleRow, hint, scrollPane, createDivider(), buttonRow);
        setPanelHeight(root, PANEL_HEIGHT);
        setScene(root);
        Platform.runLater(confirmButton::requestFocus);
    }

    private void updateKeywordSelectionState(List<ToggleButton> keywordButtons, Button confirmButton) {
        long selectedCount = keywordButtons.stream().filter(ToggleButton::isSelected).count();
        boolean reachedLimit = selectedCount >= MAX_SELECTED_KEYWORDS;

        for (ToggleButton keywordButton : keywordButtons) {
            keywordButton.setDisable(reachedLimit && !keywordButton.isSelected());
        }
        boolean hadFocus = confirmButton.isFocused();
        confirmButton.setDisable(selectedCount == 0);
        if (hadFocus || selectedCount > 0) {
            Platform.runLater(confirmButton::requestFocus);
        }
    }

    private ToggleButton createKeywordButton(String keyword) {
        ToggleButton button = new ToggleButton(keyword);
        button.setWrapText(true);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setMinHeight(40);
        button.setFocusTraversable(true);
        button.getStyleClass().add("alert-keyword-btn");
        return button;
    }

    private String safeText(String value) {
        if (value == null || value.isBlank()) {
            return "未知";
        }
        return value;
    }

    private static String createAlertKey(WindowSession session) {
        if (session == null) {
            return "";
        }
        return normalizeKeyPart(session.processName) + "\n" + normalizeKeyPart(session.title);
    }

    private static String normalizeKeyPart(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase();
    }
}
