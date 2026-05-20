package jfocus.ui;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import jfocus.ai.distraction.DistractionHandlingMode;
import jfocus.engine.FocusEngine;
import jfocus.engine.FocusListener;

public class TimerView extends VBox implements FocusListener {

    private GameManager gameManager;
    private FocusEngine engine;

    // --- 將原本 FocusUI 中的零件搬到這裡 ---
    private Label timerLabel;
    private Button startBtn;
    private Button stopBtn;
    private TextField workInput;
    private TextField breakInput;
    private ComboBox<String> modeSelector;
    private ImageView pokemonImageView;
    private ProgressBar xpBar;
    private Label xpInfoLabel;
    private Label statusLabel;

    private Button pauseBtn; // 新增這行
    private boolean isPaused = false; // 記錄目前的暫停狀態
    // 新增這行：讓 TimerView 記住目前夥伴的名字
    private String currentPartnerName = "神秘夥伴";

    public TimerView(GameManager gameManager) {

        this.gameManager = gameManager;
        // 原本的 startBtn 和 stopBtn
        startBtn = new Button("開始冒險");
        stopBtn = new Button("放棄");
        stopBtn.setDisable(true);

        // 👇 【新增】建立暫停按鈕
        pauseBtn = new Button("暫停");
        pauseBtn.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5;");
        pauseBtn.setDisable(true); // 一開始還沒倒數，不能暫停

        // 👇 【修改】把 pauseBtn 一起塞進 HBox 裡面
        HBox btnBox = new HBox(15, startBtn, pauseBtn, stopBtn);
        // 1. 實例化所有 UI 零件
        timerLabel = new Label("25:00");
        timerLabel.setStyle("-fx-font-size: 60px; -fx-font-weight: bold; -fx-text-fill: white;");

        workInput = new TextField("25");
        workInput.setPrefWidth(50);
        breakInput = new TextField("5");
        breakInput.setPrefWidth(50);

        modeSelector = new ComboBox<>();
        modeSelector.getItems().addAll("番茄鐘模式", "正向碼表");
        modeSelector.setValue("番茄鐘模式");
        // 👇 【新增這段】：監聽下拉選單的切換
        modeSelector.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if ("正向碼表".equals(newVal)) {
                // 碼表模式：鎖定輸入框，時間歸零
                workInput.setDisable(true);
                breakInput.setDisable(true);
                timerLabel.setText("00:00"); 
            } else {
                // 番茄鐘模式：解鎖輸入框，恢復預設時間
                workInput.setDisable(false);
                breakInput.setDisable(false);
                try {
                    int m = Integer.parseInt(workInput.getText().trim());
                    timerLabel.setText(String.format("%02d:00", Math.max(0, m)));
                } catch (NumberFormatException ex) {
                    timerLabel.setText("25:00");
                }
            }
        });
        pokemonImageView = new ImageView();
        pokemonImageView.setFitHeight(200);
        pokemonImageView.setFitWidth(200);
        pokemonImageView.setPreserveRatio(true);

        try {
            java.io.File defaultImg = new java.io.File("res/pokemon/000_masterball.png"); // 隨便放一張問號或精靈球的圖
            if(defaultImg.exists()) {
                pokemonImageView.setImage(new javafx.scene.image.Image(defaultImg.toURI().toString()));
            }
        } catch (Exception e) {
            System.out.println("找不到預設圖片");
        }

        xpBar = new ProgressBar(0);
        xpBar.setPrefWidth(300);
        xpBar.setStyle("-fx-accent: #3498db;");

        xpInfoLabel = new Label("XP: 0 / 200");
        xpInfoLabel.setStyle("-fx-text-fill: #bdc3c7; -fx-font-size: 12px;");

        statusLabel = new Label("準備就緒");
        statusLabel.setStyle("-fx-text-fill: white;");

        // 2. 開始套用你設計的精美排版
        this.setAlignment(Pos.CENTER);
        this.setSpacing(15);
        this.getStyleClass().add("timer-layout"); // 建議未來把 padding 寫進 CSS

        HBox inputArea = new HBox(10, 
            new Label("工:") {{ setStyle("-fx-text-fill: white;"); }}, workInput,
            new Label("休:") {{ setStyle("-fx-text-fill: white;"); }}, breakInput
        );
        inputArea.setAlignment(Pos.CENTER);


        btnBox.setAlignment(Pos.CENTER);

        // 3. 把所有零件組裝起來 (就是你原本的寫法)
        this.getChildren().addAll(
                new Label("JFocus - Just Focus") {{ setStyle("-fx-text-fill: #bdc3c7; -fx-font-size: 14px;"); }},
                modeSelector,
                inputArea,
                pokemonImageView,
                xpBar,
                xpInfoLabel,
                statusLabel,
                timerLabel,
                btnBox
        );

        // 4. 掛載組員寫的引擎與按鈕事件
        this.engine = createFocusEngine();

startBtn.setOnAction(e -> {
            try {
                String selectedMode = modeSelector.getValue();
                int minutes = 0;

                // 🛡️ 提前檢查番茄鐘模式的輸入 (防呆：擋下 0 或負數)
                if (!"正向碼表".equals(selectedMode)) {
                    minutes = Integer.parseInt(workInput.getText().trim());
                    if (minutes <= 0) {
                        statusLabel.setText("⚠️ 時間必須大於 0 分鐘喔！");
                        resetUI(); 
                        return; // 直接中斷，不讓計時器啟動
                    }
                }

                // 切換 UI 狀態 (鎖死開始鍵，解鎖暫停與放棄鍵)
                startBtn.setDisable(true);
                pauseBtn.setDisable(false);
                stopBtn.setDisable(false);
                isPaused = false;
                pauseBtn.setText("暫停");

                // 🚀 根據模式啟動不同的引擎邏輯
                if ("正向碼表".equals(selectedMode)) {
                    statusLabel.setText("正在與 " + currentPartnerName + " 一起冒險 (碼表模式) ...");
                    engine.startStopwatch(); // 呼叫組員的碼表引擎
                } else {
                    statusLabel.setText("正在與 " + currentPartnerName + " 一起冒險中...");
                    engine.start(minutes * 60); // 呼叫番茄鐘引擎倒數
                }

            } catch (NumberFormatException ex) {
                statusLabel.setText("⚠️ 請輸入有效的數字！");
                resetUI(); 
            }
        });

        // 👇 【新增】暫停與繼續的切換邏輯
// 在 TimerView.java 裡面：
        pauseBtn.setOnAction(e -> {
            if (!isPaused) {
                isPaused = true;
                pauseBtn.setText("繼續冒險");
                statusLabel.setText("計時已暫停，等你回來！");
                
                engine.pause(); // 呼叫引擎的暫停
            } else {
                isPaused = false;
                pauseBtn.setText("暫停");
                statusLabel.setText("冒險中，請保持專心！");
                
                engine.resume(); // 呼叫引擎的繼續
            }
        });


stopBtn.setOnAction(e -> {
            // 1. 建立一個確認視窗 (Confirmation Dialog)
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
            alert.setTitle("放棄冒險");
            alert.setHeaderText("確定要放棄這次的冒險嗎？");
            alert.setContentText("現在放棄的話，將無法獲得任何專注幣與經驗值喔！");

            // 2. 顯示視窗並等待玩家點擊按鈕
            java.util.Optional<javafx.scene.control.ButtonType> result = alert.showAndWait();
            
            // 3. 判斷玩家按了什麼
            if (result.isPresent() && result.get() == javafx.scene.control.ButtonType.OK) {
                // 玩家按下「確定」：執行原本的停止邏輯
                engine.shutdown();
                engine = createFocusEngine(); // 重新建立引擎準備下次使用
                resetUI();
                statusLabel.setText("冒險已取消。");
            } else {
                // 玩家按下「取消」或關閉視窗：什麼都不做，讓計時繼續
                System.out.println("玩家取消了放棄操作。");
            }
        });
    }

    private FocusEngine createFocusEngine() {
        FocusEngine newEngine = new FocusEngine(this);
        newEngine.setDistractionUserNotifier(session ->
                Platform.runLater(() -> DistractedAlert.showIfNotShowing(newEngine, session)));
        return newEngine;
    }


// 給 FocusUI 呼叫的方法：用來更新出戰夥伴的圖片和名字
    public void updatePartnerDisplay(String name, javafx.scene.image.Image image) {
        this.currentPartnerName = name; // 更新名字記憶
        
        // 如果你的 TimerView 裡面有 pokemonImageView，順便把圖片也更新了！
        if (this.pokemonImageView != null && image != null) {
            this.pokemonImageView.setImage(image);
        }
    }

    public void setDistractionHandlingMode(DistractionHandlingMode mode) {
        if (engine != null) {
            engine.setDistractionHandlingMode(mode);
        }
    }
    // ==========================================
    // 實作 FocusListener (接收引擎每秒的回傳)
    // ==========================================
    @Override
    public void onTick(int secondsRemaining) {
        // 必須用 Platform.runLater 讓 UI 執行緒去更新畫面，否則會當機！
        Platform.runLater(() -> {
            int m = secondsRemaining / 60;
            int s = secondsRemaining % 60;
            timerLabel.setText(String.format("%02d:%02d", m, s));
        });
    }

    @Override
    public void onFinished() {
        Platform.runLater(() -> {
            int focusedMinutes = Integer.parseInt(workInput.getText());
            statusLabel.setText("🎉 冒險結束！獲得 " + focusedMinutes + " 枚專注幣！");
            
            // 呼叫 GameManager 結算
            String currentId = gameManager.getCurrentPokemonId();
            gameManager.addFocusTime(focusedMinutes, currentId);

            resetUI();
            // 這裡未來可以加一段更新經驗值條 (xpBar) 的邏輯
        });
    }

private void resetUI() {
        int m = Integer.parseInt(workInput.getText());
        timerLabel.setText(String.format("%02d:00", m));
        startBtn.setDisable(false);
        pauseBtn.setDisable(true); // 鎖死暫停按鈕
        pauseBtn.setText("暫停");  // 文字歸位
        stopBtn.setDisable(true);
        isPaused = false;
    }
}
