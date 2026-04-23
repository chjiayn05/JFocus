package jfocus.ui;

import java.io.File;

import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import jfocus.db.DatabaseCore;

public class FocusApp extends Application {

    // --- UI 元件 ---
    private Label timerLabel = new Label("25:00");
    private ImageView petImageView = new ImageView();
    private Label petStatusLabel = new Label("老皮正在觀察你...");

    private Button startBtn = new Button("開始專注");
    private Button stopBtn = new Button("停止");
    private ChoiceBox<String> modeSelector = new ChoiceBox<>();

    // 預留給成員 C 的輸入區
    private TextField workInput = new TextField("25");
    private TextField breakInput = new TextField("5");

    @Override
    public void start(Stage primaryStage) {
        DatabaseCore.initializeDatabase();

        startBtn.setOnAction(e -> {
            timerLabel.setStyle("-fx-font-size: 80px; -fx-text-fill: #27ae60; -fx-font-weight: bold;"); // 變綠色
            petStatusLabel.setText("老皮：喔喔喔！要開始認真了嗎？");
            // 這裡以後會呼叫 FocusEngine.start()
        });

        stopBtn.setOnAction(e -> {
            timerLabel.setStyle("-fx-font-size: 80px; -fx-text-fill: #c0392b; -fx-font-weight: bold;"); // 變紅色
            petStatusLabel.setText("老皮：才剛開始就想偷懶嗎？");
        });
        // 1. 初始化老皮 (先放一張預設圖)
        loadPetImage("idle.gif");
        petImageView.setFitHeight(200);
        petImageView.setFitWidth(200);

        // 2. 模式選擇區
        modeSelector.getItems().addAll("碼表模式", "倒數模式", "番茄鐘");
        modeSelector.setValue("番茄鐘");

        HBox inputArea = new HBox(10, new Label("工:"), workInput, new Label("休:"), breakInput);
        inputArea.setAlignment(Pos.CENTER);
        inputArea.setStyle("-fx-text-fill: white;");

        // 3. 按鈕區
        HBox btnBox = new HBox(15, startBtn, stopBtn);
        btnBox.setAlignment(Pos.CENTER);
        setupButtonStyles();

        // 4. 建立計時主佈局 (Timer Tab)
        VBox timerLayout = new VBox(20);
        timerLayout.setAlignment(Pos.CENTER);
        timerLayout.setStyle("-fx-background-color: #2c3e50; -fx-padding: 40;");

        timerLabel.setStyle("-fx-font-size: 80px; -fx-text-fill: white; -fx-font-weight: bold;");
        petStatusLabel.setStyle("-fx-text-fill: #ecf0f1; -fx-font-size: 16px; -fx-font-style: italic;");

        timerLayout.getChildren().addAll(
                new Label("JFocus - 專注監控系統") {
                    {
                        setStyle("-fx-text-fill: #bdc3c7; -fx-font-size: 14px;");
                    }
                },
                modeSelector,
                inputArea,
                petImageView,
                petStatusLabel,
                timerLabel,
                btnBox);

        // 5. 建立分頁系統 (TabPane)
        TabPane tabPane = new TabPane();
        Tab focusTab = new Tab("專注計時", timerLayout);
        Tab statsTab = new Tab("數據分析", new StackPane(new Label("數據統計圖表預留區")));

        focusTab.setClosable(false);
        statsTab.setClosable(false);
        tabPane.getTabs().addAll(focusTab, statsTab);

        // 6. 顯示視窗
        Scene scene = new Scene(tabPane, 450, 700);
        primaryStage.setTitle("JFocus - Aegis Sentinel");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void setupButtonStyles() {
        startBtn.setStyle(
                "-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 30;");
        stopBtn.setStyle(
                "-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 30;");
    }

    private void loadPetImage(String fileName) {
        // 預留給之後放置 GIF 的路徑
        File file = new File("res/pets/" + fileName);
        if (file.exists()) {
            petImageView.setImage(new Image(file.toURI().toString()));
        }
    }

    public void updatePetState(String state) {
        switch (state) {
            case "FOCUS":
                loadPetImage("focusing.gif");
                break;
            case "IDLE":
                loadPetImage("idle.gif");
                break;
            case "REST":
                loadPetImage("dancing.gif");
                break;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}