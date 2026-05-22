package jfocus.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javafx.collections.ObservableList;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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

public class IdleAlert {
    private static final double WINDOW_WIDTH = 460;
    private static final double PANEL_HEIGHT = 280;
    private static final double CONTENT_WRAP_WIDTH = 390;
    private static final Duration FOREGROUND_WATCHDOG_INTERVAL = Duration.seconds(1.5);
    private static boolean isShowing = false;

    private final FocusEngine engine;
    private final Stage stage;
    private final List<Stage> blockerStages = new ArrayList<>();
    private final StackPane overlay;
    private Timeline foregroundWatchdog;
    private final long idleTimeMillis;

    public IdleAlert(FocusEngine engine, long idleTimeMillis) {
        this.engine = Objects.requireNonNull(engine, "engine cannot be null");
        this.idleTimeMillis = idleTimeMillis;
        this.stage = new Stage();
        this.stage.setTitle("閒置提醒");
        this.stage.initModality(Modality.APPLICATION_MODAL);
        this.stage.initStyle(StageStyle.TRANSPARENT);
        this.stage.setAlwaysOnTop(true);
        this.overlay = createOverlay();
        configureStageScene();
    }

    public static void showIfNotShowing(FocusEngine engine, long idleTimeMillis) {
        synchronized (IdleAlert.class) {
            if (isShowing) {
                return;
            }
            isShowing = true;
        }
        Platform.runLater(() -> {
            IdleAlert alert = new IdleAlert(engine, idleTimeMillis);
            alert.show();
        });
    }

    public void show() {
        showWarningPanel();
        stage.setOnHidden(event -> {
            stopForegroundWatchdog();
            closeBlockerStages();
            synchronized (IdleAlert.class) {
                isShowing = false;
            }
            engine.resume();
        });
        showBlockerStages();
        stage.show();
        startForegroundWatchdog();
        forceAlertToForeground();
    }

    private void showWarningPanel() {
        VBox root = createRoot();

        Label title = createTitleLabel("偵測到閒置！");

        long idleSeconds = idleTimeMillis / 1000;
        String timeText;
        if (idleSeconds < 60) {
            timeText = idleSeconds + " 秒";
        } else {
            timeText = (idleSeconds / 60) + " 分 " + (idleSeconds % 60) + " 秒";
        }

        Label desc1 = createBodyLabel("系統偵測到您已閒置了 " + timeText + "。");
        Label desc2 = createBodyLabel("計時已自動暫停，且時間已重新設定回閒置前的狀態。");
        Label desc3 = createBodyLabel("請在準備好後點擊下方按鈕或關閉此視窗，以重新開始計時。");

        Button resumeButton = createButton("我回來了，繼續計時");
        resumeButton.setStyle("""
                -fx-background-color: #2c7be5;
                -fx-text-fill: white;
                -fx-font-weight: bold;
                -fx-background-radius: 6;
                -fx-border-radius: 6;
                """);
        resumeButton.setOnAction(event -> {
            stage.close();
        });

        HBox buttonRow = new HBox(10, resumeButton);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);

        VBox spacer = createSpacer();
        root.getChildren().addAll(title, desc1, desc2, desc3, spacer, buttonRow);
        VBox.setVgrow(spacer, Priority.ALWAYS);
        setPanelHeight(root);
        setScene(root);
        Platform.runLater(resumeButton::requestFocus);
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
        label.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #e67e22;");
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
        button.setMinWidth(150);
        button.setFocusTraversable(true);
        return button;
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
                System.err.println("無法將閒置提醒切到前景: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("無法將閒置提醒切到前景: " + e.getMessage());
            }

            Platform.runLater(() -> {
                stage.toFront();
                stage.requestFocus();
            });
        }, "jfocus-idle-alert-foreground");
        foregroundThread.setDaemon(true);
        foregroundThread.start();
    }

    private boolean isMacOS() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("mac");
    }
}
