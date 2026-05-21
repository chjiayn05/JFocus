package jfocus.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import javafx.collections.ObservableList;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import jfocus.engine.FocusEngine;
import jfocus.monitor.WindowSession;

class DistractedAlert {
    private static final int MAX_SELECTED_KEYWORDS = 3;
    private static final double WINDOW_WIDTH = 460;
    private static final double PANEL_HEIGHT = 380;
    private static final double CONTENT_WRAP_WIDTH = 390;
    private static final Duration FOREGROUND_WATCHDOG_INTERVAL = Duration.seconds(1.5);
    private static final Set<String> ACTIVE_ALERT_KEYS = new HashSet<>();

    private final FocusEngine engine;
    private final WindowSession session;
    private final Stage stage;
    private final List<Stage> blockerStages = new ArrayList<>();
    private final StackPane overlay;
    private Timeline foregroundWatchdog;
    private final String alertKey;

    public DistractedAlert(FocusEngine engine, WindowSession session) {
        this.engine = Objects.requireNonNull(engine, "engine cannot be null");
        this.session = Objects.requireNonNull(session, "session cannot be null");
        this.alertKey = createAlertKey(session);
        this.stage = new Stage();
        this.stage.setTitle("分心提醒");
        this.stage.initModality(Modality.APPLICATION_MODAL);
        this.stage.initStyle(StageStyle.TRANSPARENT);
        this.stage.setAlwaysOnTop(true);
        this.overlay = createOverlay();
        configureStageScene();
    }

    public static void showIfNotShowing(FocusEngine engine, WindowSession session) {
        DistractedAlert alert = new DistractedAlert(engine, session);
        alert.show();
    }

    public void show() {
        if (!registerActiveAlert()) {
            return;
        }
        showWarningPanel();
        stage.setOnHidden(event -> {
            stopForegroundWatchdog();
            closeBlockerStages();
            unregisterActiveAlert();
        });
        showBlockerStages();
        stage.show();
        startForegroundWatchdog();
        forceAlertToForeground();
    }

    private void showWarningPanel() {
        VBox root = createRoot();

        Label title = createTitleLabel("偵測到分心！");
        Label appLabel = createBodyLabel("應用程式: " + safeText(session.processName));
        Label contentLabel = createBodyLabel("內容: " + safeText(session.title));

        Button ignoreButton = createButton("忽略");
        ignoreButton.setOnAction(event -> {
            engine.ignoreWindow(session);
            stage.close();
        });

        Button whitelistButton = createButton("加入白名單");
        whitelistButton.setOnAction(event -> showWhitelistPanel());

        Button closeButton = createButton("關閉視窗");
        closeButton.setOnAction(event -> {
            stopForegroundWatchdog();
            stage.hide();
            closeBlockerStages();
            boolean closed = engine.closeDistractingTarget(session);
            if (!closed) {
                System.err.println("無法關閉分心視窗或分頁: " + safeText(session.title));
            }
            stage.close();
        });

        HBox buttonRow = new HBox(10, ignoreButton, whitelistButton, closeButton);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);

        VBox spacer = createSpacer();
        root.getChildren().addAll(title, appLabel, contentLabel, spacer, buttonRow);
        VBox.setVgrow(spacer, Priority.ALWAYS);
        setPanelHeight(root);
        setScene(root);
        Platform.runLater(closeButton::requestFocus);
    }

    private void showWhitelistPanel() {
        VBox root = createRoot();

        Label title = createTitleLabel("選擇白名單關鍵字");
        Label hint = createBodyLabel("最多選擇 " + MAX_SELECTED_KEYWORDS + " 個關鍵字。");
        List<String> keywords = engine.suggestWhitelistKeywordsForSession(session);

        Button backButton = createButton("返回");
        backButton.setOnAction(event -> showWarningPanel());

        Button confirmButton = createButton("確認加入");
        confirmButton.setDisable(true);

        if (keywords.isEmpty()) {
            Label emptyLabel = createBodyLabel("沒有可加入的關鍵字。");
            HBox buttonRow = new HBox(10, backButton);
            buttonRow.setAlignment(Pos.CENTER_RIGHT);
            VBox spacer = createSpacer();
            root.getChildren().addAll(title, hint, emptyLabel, spacer, buttonRow);
            VBox.setVgrow(spacer, Priority.ALWAYS);
            setPanelHeight(root);
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
        scrollPane.setPrefViewportHeight(210);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        confirmButton.setOnAction(event -> {
            for (ToggleButton keywordButton : keywordButtons) {
                if (keywordButton.isSelected()) {
                    engine.addWhitelistRule(keywordButton.getText());
                }
            }
            stage.close();
        });

        HBox buttonRow = new HBox(10, backButton, confirmButton);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(title, hint, scrollPane, buttonRow);
        setPanelHeight(root);
        setScene(root);
        Platform.runLater(confirmButton::requestFocus);
    }

    private void updateKeywordSelectionState(List<ToggleButton> keywordButtons, Button confirmButton) {
        long selectedCount = keywordButtons.stream().filter(ToggleButton::isSelected).count();
        boolean reachedLimit = selectedCount >= MAX_SELECTED_KEYWORDS;

        for (ToggleButton keywordButton : keywordButtons) {
            keywordButton.setDisable(reachedLimit && !keywordButton.isSelected());
            updateKeywordButtonStyle(keywordButton);
        }
        boolean hadFocus = confirmButton.isFocused();
        confirmButton.setDisable(selectedCount == 0);
        if (hadFocus || selectedCount > 0) {
            Platform.runLater(confirmButton::requestFocus);
        }
    }

    private VBox createRoot() {
        VBox root = new VBox(16);
        root.setPadding(new Insets(22));
        root.setAlignment(Pos.TOP_LEFT);
        root.setMinWidth(WINDOW_WIDTH);
        root.setPrefWidth(WINDOW_WIDTH);
        root.setMaxWidth(WINDOW_WIDTH);
        root.setStyle("""
                -fx-background-color: #ffffff;
                -fx-background-radius: 8;
                -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 24, 0, 0, 8);
                """);
        return root;
    }

    private void setPanelHeight(VBox root) {
        root.setMinHeight(PANEL_HEIGHT);
        root.setPrefHeight(PANEL_HEIGHT);
        root.setMaxHeight(PANEL_HEIGHT);
    }

    private VBox createSpacer() {
        return new VBox();
    }

    private Label createTitleLabel(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #c0392b;");
        return label;
    }

    private Label createBodyLabel(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(CONTENT_WRAP_WIDTH);
        label.setStyle("-fx-font-size: 14px; -fx-text-fill: #2c3e50;");
        return label;
    }

    private Button createButton(String text) {
        Button button = new Button(text);
        button.setMinHeight(38);
        button.setMinWidth(96);
        button.setFocusTraversable(true);
        return button;
    }

    private ToggleButton createKeywordButton(String keyword) {
        ToggleButton button = new ToggleButton(keyword);
        button.setWrapText(true);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setMinHeight(40);
        button.setFocusTraversable(true);
        updateKeywordButtonStyle(button);
        return button;
    }

    private void updateKeywordButtonStyle(ToggleButton button) {
        if (button.isSelected()) {
            button.setStyle("""
                    -fx-background-color: #2c7be5;
                    -fx-text-fill: white;
                    -fx-font-size: 14px;
                    -fx-font-weight: bold;
                    -fx-background-radius: 6;
                    -fx-border-radius: 6;
                    -fx-padding: 9 12;
                    """);
            return;
        }

        button.setStyle("""
                -fx-background-color: #f7f9fc;
                -fx-text-fill: #2c3e50;
                -fx-font-size: 14px;
                -fx-background-radius: 6;
                -fx-border-color: #d9e2ec;
                -fx-border-radius: 6;
                -fx-padding: 9 12;
                """);
    }

    private void setScene(VBox root) {
        overlay.getChildren().setAll(root);
        StackPane.setAlignment(root, Pos.CENTER);
    }

    private StackPane createOverlay() {
        StackPane rootOverlay = new StackPane();
        rootOverlay.setPadding(new Insets(24));
        rootOverlay.setStyle("-fx-background-color: rgba(15, 23, 42, 0.45);");
        return rootOverlay;
    }

    private void configureStageScene() {
        var bounds = Screen.getPrimary().getVisualBounds();
        Scene scene = new Scene(overlay, bounds.getWidth(), bounds.getHeight());
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());
    }

    private void showBlockerStages() {
        ObservableList<Screen> screens = Screen.getScreens();
        Screen primaryScreen = Screen.getPrimary();
        for (Screen screen : screens) {
            if (screen.equals(primaryScreen)) {
                continue;
            }
            Stage blocker = createBlockerStage(screen);
            blockerStages.add(blocker);
            blocker.show();
        }
    }

    private Stage createBlockerStage(Screen screen) {
        Stage blocker = new Stage();
        blocker.initModality(Modality.NONE);
        blocker.initStyle(StageStyle.TRANSPARENT);
        blocker.setAlwaysOnTop(true);

        StackPane blockerOverlay = new StackPane();
        blockerOverlay.setStyle("-fx-background-color: rgba(15, 23, 42, 0.45);");

        var bounds = screen.getVisualBounds();
        Scene scene = new Scene(blockerOverlay, bounds.getWidth(), bounds.getHeight());
        scene.setFill(Color.TRANSPARENT);
        blocker.setScene(scene);
        blocker.setX(bounds.getMinX());
        blocker.setY(bounds.getMinY());
        blocker.setWidth(bounds.getWidth());
        blocker.setHeight(bounds.getHeight());
        return blocker;
    }

    private void closeBlockerStages() {
        for (Stage blocker : blockerStages) {
            blocker.close();
        }
        blockerStages.clear();
    }

    private void startForegroundWatchdog() {
        stopForegroundWatchdog();
        foregroundWatchdog = new Timeline(new KeyFrame(FOREGROUND_WATCHDOG_INTERVAL, event -> forceAlertToForeground()));
        foregroundWatchdog.setCycleCount(Timeline.INDEFINITE);
        foregroundWatchdog.play();
    }

    private void stopForegroundWatchdog() {
        if (foregroundWatchdog != null) {
            foregroundWatchdog.stop();
            foregroundWatchdog = null;
        }
    }

    private void forceAlertToForeground() {
        stage.setAlwaysOnTop(true);
        stage.toFront();
        stage.requestFocus();
        Platform.runLater(() -> {
            stage.toFront();
            stage.requestFocus();
        });

        if (isMacOS()) {
            bringCurrentProcessToFrontOnMac();
        }
    }

    private void bringCurrentProcessToFrontOnMac() {
        Thread foregroundThread = new Thread(() -> {
            long pid = ProcessHandle.current().pid();
            String script = """
                    tell application "System Events"
                        set frontmost of first application process whose unix id is %d to true
                    end tell
                    """.formatted(pid);

            try {
                Process process = new ProcessBuilder("/usr/bin/osascript", "-e", script).start();
                process.waitFor();
            } catch (IOException e) {
                System.err.println("無法將分心提醒切到前景: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("無法將分心提醒切到前景: " + e.getMessage());
            }

            Platform.runLater(() -> {
                stage.toFront();
                stage.requestFocus();
            });
        }, "jfocus-alert-foreground");
        foregroundThread.setDaemon(true);
        foregroundThread.start();
    }

    private boolean isMacOS() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("mac");
    }

    private String safeText(String value) {
        if (value == null || value.isBlank()) {
            return "未知";
        }
        return value;
    }

    private boolean registerActiveAlert() {
        synchronized (ACTIVE_ALERT_KEYS) {
            return ACTIVE_ALERT_KEYS.add(alertKey);
        }
    }

    private void unregisterActiveAlert() {
        synchronized (ACTIVE_ALERT_KEYS) {
            ACTIVE_ALERT_KEYS.remove(alertKey);
        }
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
