package jfocus.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import jfocus.engine.FocusEngine;

abstract class FullScreenAlert {

    protected static final double WINDOW_WIDTH = 480;
    protected static final double CONTENT_WRAP_WIDTH = 400;
    protected static final Duration FOREGROUND_WATCHDOG_INTERVAL = Duration.seconds(1.5);

    protected final FocusEngine engine;
    protected final Stage stage;
    protected final List<Stage> blockerStages = new ArrayList<>();
    protected final StackPane overlay;
    protected Timeline foregroundWatchdog;

    protected FullScreenAlert(FocusEngine engine, String title) {
        this.engine = Objects.requireNonNull(engine, "engine cannot be null");
        this.stage = new Stage();
        this.stage.setTitle(title);
        this.stage.initModality(Modality.APPLICATION_MODAL);
        this.stage.initStyle(StageStyle.TRANSPARENT);
        this.stage.setAlwaysOnTop(true);
        this.overlay = createOverlay();
        configureStageScene();
    }

    public final void show() {
        onBeforeShow();
        if (!tryAcquire()) return;
        showContent();
        stage.setOnHidden(event -> {
            stopForegroundWatchdog();
            closeBlockerStages();
            release();
        });
        showBlockerStages();
        stage.show();
        startForegroundWatchdog();
        forceAlertToForeground();
    }

    protected void onBeforeShow() {}

    protected abstract boolean tryAcquire();

    protected abstract void release();

    protected abstract void showContent();

    protected VBox createRoot() {
        VBox root = new VBox(14);
        root.setPadding(new Insets(26));
        root.setAlignment(Pos.TOP_LEFT);
        root.setMinWidth(WINDOW_WIDTH);
        root.setPrefWidth(WINDOW_WIDTH);
        root.setMaxWidth(WINDOW_WIDTH);
        root.getStyleClass().add("alert-panel");
        return root;
    }

    protected void setPanelHeight(VBox root, double height) {
        root.setMinHeight(height);
        root.setPrefHeight(height);
        root.setMaxHeight(height);
    }

    protected VBox createSpacer() {
        return new VBox();
    }

    /** icon: emoji字元（⛔ / 💤）, titleStyleClass: alert-title-distraction / alert-title-idle */
    protected HBox createTitleRow(String icon, String text, String titleStyleClass) {
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("alert-icon");

        Label titleLabel = new Label(text);
        titleLabel.setWrapText(true);
        titleLabel.getStyleClass().addAll("alert-title", titleStyleClass);

        HBox row = new HBox(10, iconLabel, titleLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    protected Label createBodyLabel(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(CONTENT_WRAP_WIDTH);
        label.getStyleClass().add("alert-body");
        return label;
    }

    protected HBox createDivider() {
        HBox line = new HBox();
        line.setMinHeight(1);
        line.setMaxHeight(1);
        line.setMaxWidth(Double.MAX_VALUE);
        line.getStyleClass().add("alert-divider");
        javafx.scene.layout.VBox.setMargin(line, new Insets(4, 0, 4, 0));
        return line;
    }

    protected Button createButton(String text, double minWidth) {
        Button button = new Button(text);
        button.setMinHeight(36);
        button.setMinWidth(minWidth);
        button.setFocusTraversable(true);
        return button;
    }

    protected void setScene(VBox root) {
        overlay.getChildren().setAll(root);
        StackPane.setAlignment(root, Pos.CENTER);
    }

    private StackPane createOverlay() {
        StackPane rootOverlay = new StackPane();
        rootOverlay.setPadding(new Insets(32));
        rootOverlay.getStyleClass().add("alert-overlay");
        return rootOverlay;
    }

    private void configureStageScene() {
        var bounds = Screen.getPrimary().getVisualBounds();
        // sceneRoot is the actual .root node — give it a transparent inline style so the
        // theme CSS's .root { -fx-background-color: -my-primary-color } doesn't win over
        // the overlay's rgba background via cascade.
        StackPane sceneRoot = new StackPane(overlay);
        sceneRoot.setStyle("-fx-background-color: transparent;");
        Scene scene = new Scene(sceneRoot, bounds.getWidth(), bounds.getHeight());
        scene.setFill(Color.TRANSPARENT);
        if (!FocusUI.activeStylesheets.isEmpty()) {
            scene.getStylesheets().addAll(FocusUI.activeStylesheets);
        }
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
        blockerOverlay.getStyleClass().add("alert-overlay");

        StackPane blockerRoot = new StackPane(blockerOverlay);
        blockerRoot.setStyle("-fx-background-color: transparent;");

        var bounds = screen.getVisualBounds();
        Scene scene = new Scene(blockerRoot, bounds.getWidth(), bounds.getHeight());
        scene.setFill(Color.TRANSPARENT);
        if (!FocusUI.activeStylesheets.isEmpty()) {
            scene.getStylesheets().addAll(FocusUI.activeStylesheets);
        }
        blocker.setScene(scene);
        blocker.setX(bounds.getMinX());
        blocker.setY(bounds.getMinY());
        blocker.setWidth(bounds.getWidth());
        blocker.setHeight(bounds.getHeight());
        return blocker;
    }

    protected void closeBlockerStages() {
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

    protected void stopForegroundWatchdog() {
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
                System.err.println("無法將提醒切到前景: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("無法將提醒切到前景: " + e.getMessage());
            }

            Platform.runLater(() -> {
                stage.toFront();
                stage.requestFocus();
            });
        }, "jfocus-alert-foreground");
        foregroundThread.setDaemon(true);
        foregroundThread.start();
    }

    protected boolean isMacOS() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("mac");
    }
}
