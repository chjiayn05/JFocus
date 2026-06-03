package jfocus.ui;

import java.util.Objects;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import jfocus.engine.FocusEngine;

public class IdleAlert extends FullScreenAlert {
    private static final double PANEL_HEIGHT = 300;
    private static boolean isShowing = false;

    private final long idleTimeMillis;

    public IdleAlert(FocusEngine engine, long idleTimeMillis) {
        super(Objects.requireNonNull(engine, "engine cannot be null"), "閒置提醒");
        this.idleTimeMillis = idleTimeMillis;
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

    @Override
    protected boolean tryAcquire() {
        // isShowing was already set true in showIfNotShowing before construction,
        // so this alert always owns the lock by the time show() is called.
        return true;
    }

    @Override
    protected void release() {
        synchronized (IdleAlert.class) {
            isShowing = false;
        }
        engine.resume();
    }

    @Override
    protected void showContent() {
        VBox root = createRoot();

        HBox titleRow = createTitleRow("𝗓ᶻ", "偵測到閒置！", "alert-title-idle");

        long idleSeconds = idleTimeMillis / 1000;
        String timeText = idleSeconds < 60
                ? idleSeconds + " 秒"
                : (idleSeconds / 60) + " 分 " + (idleSeconds % 60) + " 秒";

        Label desc1 = createBodyLabel("系統偵測到您已閒置了 " + timeText + "。");
        Label desc2 = createBodyLabel("計時已自動暫停，且時間已重新設定回閒置前的狀態。");
        Label desc3 = createBodyLabel("準備好後點擊下方按鈕或關閉此視窗，重新開始計時。");

        Button resumeButton = createButton("我回來了，繼續計時", 160);
        resumeButton.getStyleClass().add("alert-btn-primary");
        resumeButton.setOnAction(event -> stage.close());

        HBox buttonRow = new HBox(resumeButton);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);

        VBox spacer = createSpacer();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        root.getChildren().addAll(titleRow, desc1, desc2, desc3, spacer, createDivider(), buttonRow);
        setPanelHeight(root, PANEL_HEIGHT);
        setScene(root);
        Platform.runLater(resumeButton::requestFocus);
    }
}
