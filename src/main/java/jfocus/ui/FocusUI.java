package jfocus.ui;

import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.gson.Gson; // 在最上方加入這行
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import jfocus.db.DatabaseCore;
import jfocus.io.UserData;
import jfocus.main.FocusApp;
import jfocus.notification.NotificationPayload;
import jfocus.notification.NotificationSeverity;

public class FocusUI extends Application {

    private GameManager gameManager = new GameManager();
    private String id;
    private String folderName;
    private String name;
    private String types;
    private List<String> stages; // 這是描述
    private List<String> stageNames;
    private Scene mainScene; // 宣告全域的 Scene 以便切換主題
    // --- 核心數據 (未來會與 JSON 對接) ---
    private String currentPokemonFolder = "004_charmander"; // 預設小火龍
    private int currentStage = 1;
    private Label coinLabel = new Label("💰 0");
    private Label stoneLabel = new Label("💎 0");
    private Label gachaCurrencyLabel = new Label("我的專注幣: 0");

    // --- UI 元件 ---
    private Label timerLabel = new Label("25:00");
    private ImageView pokemonImageView = new ImageView();
    private Label statusLabel = new Label("準備好開始專注了嗎？");

    // 【新增這行】把抽獎訊息標籤變成全域變數
    private Label gachaMessageLabel = new Label("來試試手氣吧！");

    // 經驗條元件
    private ProgressBar xpBar = new ProgressBar(0.0);
    private Label xpInfoLabel = new Label("XP: 0 / 50 (等級 1)");

    private Button startBtn = new Button("開始專注");
    private Button stopBtn = new Button("停止");
    private ChoiceBox<String> modeSelector = new ChoiceBox<>();

    private TextField workInput = new TextField("25");
    private TextField breakInput = new TextField("5");

    // 圖鑑相關元件
    // private ImageView bigView;
    // private ComboBox<String> stageSelector;
    // private Label pokemonNameLabel;
    // private String selectedPokemonId;
    // private String selectedPokemonName;

    // Json
    // 1. 宣告清單變數
    private List<PokemonData> pokedexList = new ArrayList<>();
    private FlowPane pokedexFlowGrid;

    // 2. 建立一個內部類別來對應 JSON 資料格式 (POJO)
    // 1. 確保類別定義是這樣的 (在 FocusApp 類別內)

    public static class PokemonData {
        private String id;
        private String folderName;
        private String name;
        private List<String> types;
        private List<String> descriptions; // 負責裝描述文字
        private List<String> stageNames; // 負責裝各階段專屬名稱

        // 建構子 (Constructor)
        public PokemonData(String id, String folderName, String name, List<String> types,
                List<String> descriptions, List<String> stageNames) {
            this.id = id;
            this.folderName = folderName;
            this.name = name;
            this.types = types;
            this.descriptions = descriptions;
            this.stageNames = stageNames;
        }

        // ==========================================
        // 下面是被你不小心吃掉的所有 Getters，一次補齊！
        // ==========================================

        public String getId() {
            return id;
        }

        public String getFolderName() {
            return folderName;
        }

        public String getName() {
            return name;
        }

        public List<String> getTypes() {
            return types;
        }

        public List<String> getDescriptions() {
            return descriptions;
        }

        public List<String> getStageNames() {
            return stageNames;
        }

        // --- 這是你原本寫好的防呆小幫手，用來抓取「特定階段」的名字 ---
        public String getStageName(int stage) {
            // 如果清單有資料，而且長度足夠，就回傳對應階段的名字 (注意 index 是 stage - 1)
            if (stageNames != null && stageNames.size() >= stage) {
                return stageNames.get(stage - 1);
            }
            // 萬一沒抓到，就退一步回傳最初始的名字
            return name;
        }
    }

    // --- 接口 (Integration Hooks) ---

    public void triggerDistractionWarning(String appTitle) {
        statusLabel.setText("警告：偵測到分心視窗 [" + appTitle + "]！");
        statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
    }

    public void updateSettingsFromJSON(int workTime, int breakTime) {
        workInput.setText(String.valueOf(workTime));
        breakInput.setText(String.valueOf(breakTime));
    }

    private boolean validateTimeInputs() {
        try {
            int w = Integer.parseInt(workInput.getText());
            int b = Integer.parseInt(breakInput.getText());
            return w >= 2 * b;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private int parsePositiveInt(String text, int fallback) {
        try {
            int value = Integer.parseInt(text.trim());
            return value > 0 ? value : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String getCurrentPokemonId() {
        if (currentPokemonFolder == null || currentPokemonFolder.length() < 3) {
            return null;
        }

        String pokemonId = currentPokemonFolder.substring(0, 3);
        if (!pokemonId.matches("\\d{3}")) {
            return null;
        }

        return pokemonId;
    }

    private void refreshCurrencyLabels() {
        coinLabel.setText("💰 " + gameManager.getFocusCoins());
        stoneLabel.setText("💎 " + gameManager.getMasterStones());
        if (gachaCurrencyLabel != null) {
            gachaCurrencyLabel.setText("我的專注幣: " + gameManager.getFocusCoins());
        }
    }

    private void refreshXpDisplay() {
        String pokemonId = getCurrentPokemonId();
        int xp = gameManager.getPokemonXp(pokemonId);
        int stages;
        int nextGoal;
        double progress;

        if (xp < 50) {
            stages = 1;
            nextGoal = 50;
            progress = Math.min(1.0, xp / 50.0);
        } else if (xp < 200) {
            stages = 2;
            nextGoal = 200;
            progress = Math.min(1.0, (xp - 50) / 150.0);
        } else {
            stages = 3;
            nextGoal = 200;
            progress = 1.0;
        }
        // 1. 先宣告 currentId！(請根據你 GameManager 裡實際的方法名稱微調，例如 getPartnerId 或
        // getCurrentPokemonId)
        String currentId = gameManager.getCurrentPokemonId();

        // 2. 不要求 GameManager 了！我們自己用 for 迴圈去 FocusUI 的圖鑑清單裡找！
        PokemonData data = null;
        for (PokemonData p : pokedexList) {
            if (p.getId().equals(currentId)) {
                data = p; // 找到了！把它存起來
                break; // 找到就停止迴圈
            }
        }

        // 3. 確保有找到資料，就把真正的名字放上去！
        if (data != null) {
            String realName = data.getStageName(stages);
            // 乾淨俐落的名字，順便把後面醜醜的 " (階段 X)" 刪掉了！
            statusLabel.setText("夥伴：" + realName);
        } else {
            statusLabel.setText("夥伴：未知");
        }
    }

    private void loadUserProgressSafely() {
        try {
            int[] stats = UserData.loadPlayerStats();
            Set<String> unlockedStages = UserData.loadUnlockedStages();
            Map<String, Integer> pokemonXpMap = UserData.loadPokemonXp();
            gameManager.initializePlayerState(stats[0], stats[1], stats[2], unlockedStages, pokemonXpMap);
            refreshCurrencyLabels();
            refreshXpDisplay();
        } catch (RuntimeException ex) {
            statusLabel.setText("讀取存檔失敗，將使用預設資料。");
            System.err.println("讀取存檔失敗: " + ex.getMessage());
        }
    }

    private void saveUserProgressSafely() {
        try {
            UserData.savePlayerStats(
                    gameManager.getFocusCoins(),
                    gameManager.getMasterStones(),
                    gameManager.getTotalXP(),
                    gameManager.getCurrentPokemonId());

            for (String stageKey : gameManager.getUnlockedStageKeys()) {
                UserData.saveUnlockedStage(stageKey);
            }

            UserData.savePokemonXp(gameManager.getPokemonXpMap());
        } catch (RuntimeException ex) {
            statusLabel.setText("儲存資料失敗，請稍後再試。");
            System.err.println("儲存存檔失敗: " + ex.getMessage());
        }
    }

    private void settleCurrentSession() {
        int settledMinutes = parsePositiveInt(workInput.getText(), 25);
        gameManager.addFocusTime(settledMinutes, getCurrentPokemonId());
        refreshCurrencyLabels();
        refreshXpDisplay();
        refreshPokedexGrid();
        saveUserProgressSafely();

        timerLabel.setStyle("-fx-font-size: 80px; -fx-text-fill: #f39c12; -fx-font-weight: bold;");
        statusLabel.setText("本輪已結算，獲得 " + settledMinutes + " 專注幣與 XP！");
    }

    private void showStopSettlementDialog() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initModality(Modality.APPLICATION_MODAL);
        alert.setTitle("專注結算");
        alert.setHeaderText("要繼續專注，還是現在結算？");
        alert.setContentText("結算會把本輪專注時間換成獎勵。\n你也可以選擇繼續，不進行結算。");

        ButtonType continueButton = new ButtonType("繼續專注");
        ButtonType settleButton = new ButtonType("立即結算");
        alert.getButtonTypes().setAll(continueButton, settleButton);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty()) {
            return;
        }

        if (result.get() == settleButton) {
            settleCurrentSession();
        } else if (result.get() == continueButton) {
            timerLabel.setStyle("-fx-font-size: 80px; -fx-text-fill: #27ae60; -fx-font-weight: bold;");
            statusLabel.setText("已返回專注模式，繼續加油！");
        }
    }

    private void loadPokedexData() {
    pokedexList.clear();
    File jsonFile = new File("res/pokemon_data.json");

    if (!jsonFile.exists()) {
        System.err.println("❌ 找不到數據檔案: " + jsonFile.getAbsolutePath());
        return;
    }

    try (FileReader reader = new FileReader(jsonFile, StandardCharsets.UTF_8)) {
        JsonObject jsonRoot = new Gson().fromJson(reader, JsonObject.class);
        JsonArray array = jsonRoot.getAsJsonArray("pokedex");

        for (JsonElement element : array) {
            JsonObject obj = element.getAsJsonObject();

            // 1. 抓取基本字串資料
            String id = obj.get("id").getAsString();
            String folderName = obj.get("folderName").getAsString();
            String name = obj.get("name").getAsString();

            // 2. 【核心修正】將 "火 / 飛行" 這種字串切開轉成 List<String>
            // 這是為了解決 String cannot be converted to List<String> 的報錯
            List<String> types = java.util.Arrays.asList(obj.get("types").getAsString().split(" / "));

            // 3. 抓取描述文字 (JSON 中的 key 是 stages)
            List<String> descriptions = new ArrayList<>();
            if (obj.has("stages") && obj.get("stages").isJsonArray()) {
                for (JsonElement desc : obj.get("stages").getAsJsonArray()) {
                    descriptions.add(desc.getAsString());
                }
            }

            // 4. 抓取各階段名字 (stageNames)
            List<String> stageNames = new ArrayList<>();
            if (obj.has("stageNames") && obj.get("stageNames").isJsonArray()) {
                for (JsonElement n : obj.get("stageNames").getAsJsonArray()) {
                    stageNames.add(n.getAsString());
                }
            } else {
                // 防呆：沒名字時用基本名字填滿
                stageNames.addAll(java.util.Arrays.asList(name, name, name));
            }

            // 5. 所有的參數現在型態都對了 (String, String, String, List, List, List)
            pokedexList.add(new PokemonData(id, folderName, name, types, descriptions, stageNames));
        }
        
        System.out.println("✅ 數據載入成功！共 " + pokedexList.size() + " 隻。");

    } catch (Exception e) {
        System.err.println("❌ 解析 JSON 失敗: " + e.getMessage());
    }
}

    /**
     * 補上這段手動初始化，讓 pokedexList 不再是空的
     */

    /**
     * 把原本 start 裡的按鈕事件抽出來，程式碼會更整潔
     */
    private VBox createTimerLayout() {
        // 1. 設定計時器主配置
        VBox timerLayout = new VBox(15);
        timerLayout.setAlignment(Pos.CENTER);
        timerLayout.setStyle("-fx-padding: 30;");

        // 2. 組合輸入區域 (工/休時間)
        HBox inputArea = new HBox(10, new Label("工:") {
            {
                setStyle("-fx-text-fill: white;");
            }
        }, workInput,
                new Label("休:") {
                    {
                        setStyle("-fx-text-fill: white;");
                    }
                }, breakInput);
        inputArea.setAlignment(Pos.CENTER);

        // 3. 組合按鈕區域
        HBox btnBox = new HBox(15, startBtn, stopBtn);
        btnBox.setAlignment(Pos.CENTER);

        // 4. 設定圖片尺寸
        pokemonImageView.setFitHeight(200);
        pokemonImageView.setFitWidth(200);
        pokemonImageView.setPreserveRatio(true);

        // 5. 設定經驗值條樣式
        xpBar.setPrefWidth(300);
        xpBar.setStyle("-fx-accent: #3498db;");
        xpInfoLabel.setStyle("-fx-text-fill: #bdc3c7; -fx-font-size: 12px;");

        // 6. 把所有零件塞進垂直佈局
        timerLayout.getChildren().addAll(
                new Label("JFocus - 專注衛士") {
                    {
                        setStyle("-fx-text-fill: #bdc3c7; -fx-font-size: 14px;");
                    }
                },
                modeSelector,
                inputArea,
                pokemonImageView,
                xpBar,
                xpInfoLabel,
                statusLabel,
                timerLabel,
                btnBox);

        return timerLayout;
    }

    private void handleButtonEvents() {
        startBtn.setOnAction(e -> {
            if (validateTimeInputs()) {
                FocusApp.startNewSession();
                timerLabel.setStyle("-fx-font-size: 80px; -fx-text-fill: #27ae60; -fx-font-weight: bold;");
                statusLabel.setText("正在與精靈一起努力工作中...");
            } else {
                statusLabel.setText("時間設定需符合：工時至少是休息的兩倍。");
            }
        });

        stopBtn.setOnAction(e -> {
            showStopSettlementDialog();
        });
    }

    /**
     * 動態切換 CSS 主題
     */
    private void switchTheme(String cssFileName) {
        if (mainScene == null)
            return;

        File cssFile = new File("res/css/" + cssFileName);
        if (cssFile.exists()) {
            mainScene.getStylesheets().clear();
            mainScene.getStylesheets().add(cssFile.toURI().toString());
        } else {
            System.err.println("找不到主題檔案: " + cssFileName);
        }
    }

    // start
    @Override
    public void start(Stage primaryStage) {
        DatabaseCore.initializeDatabase(); // 確保資料庫在 UI 啟動前就準備好
        loadPokedexData();
        loadUserProgressSafely();

        TabPane tabPane = new TabPane();

        Tab focusTab = new Tab("專注計時", createTimerLayout());
        focusTab.setClosable(false);

        Tab pokedexTab = createPokedexTab(); // 建立圖鑑
        pokedexTab.setClosable(false);

        // 確保你有這個方法，否則會報錯
        Tab statsTab = createStatsTab();
        statsTab.setClosable(false);

        // 新增：把抽獎分頁也加進去
        Tab gachaTab = createGachaTab();
        gachaTab.setClosable(false);

        tabPane.getTabs().addAll(focusTab, gachaTab, pokedexTab, statsTab);

        // --- 右上角加入主題切換下拉選單 ---
        // --- 頂部狀態列 (主題切換 + 貨幣) ---
        // 1. 這裡的選項名稱，必須跟下面的 switch 完全一樣！
        ChoiceBox<String> themeSelector = new ChoiceBox<>();
        themeSelector.getItems().addAll("暗黑電競", "明亮清新", "經典紅", "大師球");
        themeSelector.setValue("暗黑電競"); // 預設值也要改

        // 2. 你的 switch 邏輯 (保持你原本寫好的就好)
        themeSelector.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            String css = switch (newVal) {
                case "暗黑電競" -> "PokemonDark.css";
                case "明亮清新" -> "PokemonLight.css";
                case "經典紅" -> "PokemonRed.css";
                case "大師球" -> "PokemonPurple.css";
                default -> "PokemonDark.css";
            };
            switchTheme(css);
        });

        // 建立一個有彈性的空白區域，把下拉選單推到左邊，把金幣推到右邊
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        javafx.scene.layout.HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        // 組合頂部導覽列
        HBox topBar = new HBox(15, themeSelector, spacer, coinLabel, stoneLabel);
        topBar.setAlignment(Pos.CENTER);
        topBar.setPadding(new javafx.geometry.Insets(10, 20, 10, 20));
        topBar.getStyleClass().add("top-bar"); // 綁定 CSS class 讓我們可以幫導覽列上色

        // 使用 VBox 垂直排列：上面是狀態列，下面是分頁內容 (徹底解決重疊問題！)
        VBox rootLayout = new VBox(topBar, tabPane);
        javafx.scene.layout.VBox.setVgrow(tabPane, javafx.scene.layout.Priority.ALWAYS);

        mainScene = new Scene(rootLayout, 480, 750);

        // 預設載入新主題
        switchTheme("PokemonRed.css");

        // 重新組合右上角 (加入 themeSelector)
        HBox currencyHeader = new HBox(15, themeSelector, coinLabel, stoneLabel);
        currencyHeader.setAlignment(Pos.TOP_RIGHT);
        currencyHeader.setPadding(new javafx.geometry.Insets(15));
        currencyHeader.setPickOnBounds(false);

        StackPane rootStack = new StackPane(tabPane, currencyHeader);

        // 【注意這裡】我們改用 mainScene 來存取畫面
        mainScene = new Scene(rootStack, 480, 750);

        // 預設載入暗黑主題
        switchTheme("PokemonDark.css");

        // --- 重要：一定要呼叫這個，按鈕才會動 ---
        handleButtonEvents();
        setupButtonStyles();
        refreshCurrencyLabels();
        refreshXpDisplay();

        primaryStage.setTitle("JFocus - Pokemon Focus Sentinel");
        primaryStage.setScene(mainScene);
        primaryStage.setOnCloseRequest(event -> {
            saveUserProgressSafely();
            FocusApp.shutdownNotificationService();
        });
        primaryStage.show();

        FocusApp.getNotificationService().notify(
                new NotificationPayload(
                        "JFocus 已啟動",
                        "通知介面已就緒，可開始串接專注事件。",
                        NotificationSeverity.INFO,
                        "ui"));

        updatePokemonDisplay(currentPokemonFolder, currentStage);
    }

    // 抽獎動畫 (加入 drawType 參數)
    private void playGachaAnimation(ImageView ballView, String drawType) {
        // 1. 晃動動畫 (Shake)
        RotateTransition shake = new RotateTransition(javafx.util.Duration.millis(100), ballView);
        shake.setFromAngle(-15);
        shake.setToAngle(15);
        shake.setCycleCount(10);
        shake.setAutoReverse(true);

        shake.setOnFinished(event -> {
            // 2. 隨機決定中獎的寶可夢
            String resultId = gameManager.performPokeBallDraw(drawType);

            if ("INSUFFICIENT_FUNDS".equals(resultId)) {
                gachaMessageLabel.setText("資源不夠啦！再去專注幾分鐘吧！");
                gachaMessageLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 16px; -fx-font-weight: bold;");
                return;
            }

            // 3. 換圖並噴發效果
            ballView.setImage(new Image("file:res/pokemon/" + resultId + "/stage1.png"));

            ScaleTransition pop = new ScaleTransition(javafx.util.Duration.millis(300), ballView);
            pop.setFromX(0.5);
            pop.setFromY(0.5);
            pop.setToX(1.5);
            pop.setToY(1.5);
            pop.play();

            refreshCurrencyLabels();
            refreshXpDisplay();
            refreshPokedexGrid();
            saveUserProgressSafely();

            // --- 【新增】從 pokedexList 找出中文名稱 ---
            String caughtName = "未知精靈";
            for (PokemonData data : pokedexList) {
                if (data.getId().equals(resultId)) {
                    caughtName = data.getName();
                    break;
                }
            }

            // 4. 動畫結束，顯示超有成就感的中獎訊息！
            gachaMessageLabel.setText("恭喜！收服了：" + caughtName + "！");
            gachaMessageLabel.setStyle("-fx-text-fill: #2ecc71; -fx-font-size: 18px; -fx-font-weight: bold;");
        });

        shake.play();
    }

    // 抽獎主邏輯
    private Tab createGachaTab() {
        gachaMessageLabel.setText("來試試手氣吧！");
        gachaMessageLabel.setStyle("-fx-text-fill: #bdc3c7; -fx-font-size: 16px; -fx-font-weight: bold;");
        VBox layout = new VBox(30);
        layout.setAlignment(Pos.CENTER);
        layout.setStyle("-fx-background-color: #34495e; -fx-padding: 40;");

        Label title = new Label("精靈補給站");
        title.setStyle("-fx-text-fill: #f1c40f; -fx-font-size: 32px; -fx-font-weight: bold;");

        // 顯示貨幣
        gachaCurrencyLabel = new Label("我的專注幣: " + gameManager.getFocusCoins());
        gachaCurrencyLabel.setStyle("-fx-text-fill: white; -fx-font-size: 18px;");

        // 抽獎展示區
        StackPane gachaDisplay = new StackPane();
        ImageView ballView = new ImageView(new Image("file:res/pokemon/000_ball.png"));
        ballView.setFitHeight(150);
        ballView.setPreserveRatio(true);
        gachaDisplay.getChildren().add(ballView);

        // --- 【修改區塊：雙按鈕與互動邏輯】 ---
        Button normalBtn = new Button("普通球抽獎 (200 💰)");
        normalBtn.setStyle(
                "-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20;");

        Button premiumBtn = new Button("大師球抽獎 (1 💎)");
        premiumBtn.setStyle(
                "-fx-background-color: #8e44ad; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20;");

        HBox btnBox = new HBox(20, normalBtn, premiumBtn);
        btnBox.setAlignment(Pos.CENTER);

        // 滑鼠懸停切換精靈球圖片
        normalBtn.setOnMouseEntered(e -> ballView.setImage(new Image("file:res/pokemon/000_ball.png")));
        premiumBtn.setOnMouseEntered(e -> ballView.setImage(new Image("file:res/pokemon/000_masterball.png")));

        // 點擊事件：呼叫動畫並傳入對應標籤
        normalBtn.setOnAction(e -> playGachaAnimation(ballView, "normal"));
        premiumBtn.setOnAction(e -> playGachaAnimation(ballView, "premium"));
        // ------------------------------------

        layout.getChildren().addAll(title, gachaCurrencyLabel, gachaDisplay, btnBox);
        return new Tab("精靈抽獎", layout);
    }

    private void setupButtonStyles() {
        startBtn.setStyle(
                "-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 30;");
        stopBtn.setStyle(
                "-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 30;");
    }

    /**
     * 更新精靈圖片顯示
     * 
     * @param folder 資料夾名稱 (如 004_charmander)
     * @param stages 進化階段 (1, 2, 或 3)
     */
    public void updatePokemonDisplay(String folder, int stages) {
        String path = "res/pokemon/" + folder + "/stage" + stages + ".png";
        File file = new File(path);
        if (file.exists()) {
            pokemonImageView.setImage(new Image(file.toURI().toString()));
        } else {
            System.out.println("找不到圖片路徑: " + path);
        }
    }

    /**
     * 建立圖鑑分頁
     */
    private Tab createPokedexTab() {
        ScrollPane scrollPane = new ScrollPane();
        // 使用 FlowPane 取代 VBox，設定間距為 10
        pokedexFlowGrid = new FlowPane(10, 10);
        pokedexFlowGrid.setStyle("-fx-padding: 15;");
        pokedexFlowGrid.setPrefWrapLength(450); // 這裡設定跟你的視窗寬度差不多
        pokedexFlowGrid.setAlignment(Pos.TOP_LEFT);

        refreshPokedexGrid();

        scrollPane.setContent(pokedexFlowGrid);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");
        return new Tab("精靈圖鑑", scrollPane);
    }

    private void refreshPokedexGrid() {
        if (pokedexFlowGrid == null) {
            return;
        }

        pokedexFlowGrid.getChildren().clear();
        for (PokemonData data : pokedexList) {
            for (int i = 1; i <= 3; i++) {
                VBox card = createPokemonCard(data, i);
                pokedexFlowGrid.getChildren().add(card);
            }
        }
    }

    /**
     * 建立單個寶可夢卡片
     */
    /**
     * 建立單個寶可夢卡片
     */
// FocusUI.java 裡面的 createPokemonCard 方法

private VBox createPokemonCard(PokemonData data, int stages) {
    VBox card = new VBox(5);
    card.getStyleClass().add("pokemon-card");
    card.setPrefSize(70, 90);
    card.setAlignment(Pos.CENTER);

    ImageView view = new ImageView();

    // 1. 【安全寫法】：用 File 物件去抓路徑，防呆，確認圖片是否存在
    String relativePath = "res/pokemon/" + data.getFolderName() + "/stage" + stages + ".png";
    java.io.File imgFile = new java.io.File(relativePath);

    if (imgFile.exists()) {
        // 使用 file: 前綴從檔案系統載入圖片
        Image img = new Image(imgFile.toURI().toString());
        view.setImage(img);
        
        // 確保寬度有設定，且不會因為排版消失
        view.setFitWidth(55);
        view.setManaged(true);
        view.setVisible(true);
    } else {
        System.err.println("❌ 找不到圖片: " + relativePath);
        // 如果找不到圖片，可以設一個預設圖片，防止 UI 空白
        // view.setImage(new Image("file:res/pokemon/unknown.png"));
    }

    view.setFitWidth(55);
    view.setPreserveRatio(true);

    // ============================================
    // 👇 【大修復區塊】：防護網與點擊邏輯 (把黑影裝回來！)
    // ============================================
    
    // 從 GameManager 檢查這隻寶可夢的這一個階段是否解鎖
    boolean isUnlocked = gameManager.isStageUnlocked(data.getId(), stages);

    if (!isUnlocked) {
        // 🛡️ 【修復黑影】：尚未解鎖：利用 ColorAdjust 變黑！
        ColorAdjust blackout = new ColorAdjust();
        blackout.setBrightness(-1.0); // 100% 變黑
        view.setEffect(blackout);

        // 尚未解鎖：點擊只顯示警告，不跳出詳細視窗
        card.setOnMouseClicked(e -> {
            statusLabel.setText("這隻精靈尚未解鎖喔！去補給站試試手氣吧！");
            statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;"); // 警告紅字
        });
    } else {
        // ✨ 【修復解鎖】：已經解鎖：正常顯示，點擊跳出詳細視窗
        view.setEffect(null); // 清除效果

        card.setOnMouseClicked(e -> {
            // 抓取各階段專屬名字 (例如: 卡咪龜)
            String realName = data.getStageName(stages);

            // 🛡️ 終極防呆：確保就算沒抓到描述，也不會當機！
            String safeDescription = "這是一隻神秘的寶可夢，暫無描述。";
            if (data.getDescriptions() != null && data.getDescriptions().size() >= stages) {
                safeDescription = data.getDescriptions().get(stages - 1);
            }

            // 顯示詳細視窗
            showDetailView(
                data.getId(),
                data.getFolderName(),
                stages,
                realName,
                // 防呆：如果沒抓到屬性，顯示 "未知"
                data.getTypes() != null ? data.getTypes() : java.util.Arrays.asList("未知"),
                safeDescription 
            );
        });
    }
    // ============================================

    //👇 就是少了這行啦！！！把你的畫家 (view) 掛回畫框 (card) 上去！
    card.getChildren().add(view);

    return card;
}

    // 在 FocusApp.java 類別中

    /**
     * 顯示選中精靈的詳細資訊 (強化版：加入彩色屬性 Tag 與出戰按鈕)
     * * @param folder 資料夾名稱 (如 007_squirtle)
     * 
     * @param stage       階段 (1, 2, 3)
     * @param name        精靈名字
     * @param types       屬性清單 (例如：{"水", "飛行"}) -> 這裡是假設資料格式
     * @param description 描述文字
     */
    /**
     * 顯示精靈詳細資訊視窗 (含彩色屬性標籤、描述文字與出戰按鈕)
     */
    // 1. 【修改】在括號裡第一個位置，加上 String id
    public void showDetailView(String id, String folder, int stages, String name, List<String> types,
            String description) {
        Stage detailStage = new Stage();
        VBox layout = new VBox(15);
        layout.setAlignment(Pos.CENTER);
        layout.setStyle(
                "-fx-background-color: #2c3e50; -fx-padding: 20; -fx-border-color: #f1c40f; -fx-border-width: 2;");

        ImageView bigView = new ImageView();

        // 同樣用 File 轉 URI 的安全寫法
        java.io.File bigImgFile = new java.io.File("res/pokemon/" + folder + "/stage" + stages + ".png");
        if (bigImgFile.exists()) {
            bigView.setImage(new Image(bigImgFile.toURI().toString()));
        }

        bigView.setFitWidth(280);
        bigView.setPreserveRatio(true);

        // 2. 【修改】因為傳進來的 name 已經是「水箭龜」了，直接用就好，把醜醜的 " 階段 X" 拿掉！
        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-text-fill: white; -fx-font-size: 20px; -fx-font-weight: bold;");

        // 屬性標籤
        HBox typeBox = new HBox(10);
        typeBox.setAlignment(Pos.CENTER);
        for (String t : types) {
            Label tag = new Label(t.trim());
            tag.getStyleClass().addAll("type-tag", getStyleClassForType(t.trim()));
            typeBox.getChildren().add(tag);
        }

        javafx.scene.text.Text descText = new javafx.scene.text.Text(description);
        descText.setFill(javafx.scene.paint.Color.WHITE);
        descText.setWrappingWidth(350);

        Button selectBtn = new Button("選擇出戰");
        selectBtn.getStyleClass().add("gacha-button");
        selectBtn.setOnAction(e -> {
            // 3. 【修改】這裡直接使用傳進來的 id，就不會報錯了！
            gameManager.setCurrentPokemonId(id);

            // 寫入 SQLite 資料庫存檔！
            jfocus.io.UserData.saveCurrentPartner(id);

            // 更新本地變數與主畫面 (幫你把重複的 refreshXpDisplay 整理乾淨了)
            this.currentPokemonFolder = folder;
            this.currentStage = stages;
            updatePokemonDisplay(folder, stages);
            refreshXpDisplay();

            detailStage.close();
        });

        // --- 組合視窗的剩餘程式碼 (照你原本的寫法) ---
        layout.getChildren().addAll(bigView, nameLabel, typeBox, descText, selectBtn);
        Scene scene = new Scene(layout, 400, 550);
        detailStage.setScene(scene);
        detailStage.show();
    }

    // 輔助：判定屬性顏色
    private String getStyleClassForType(String type) {
        switch (type) {
            case "火":
                return "fire";
            case "水":
                return "water";
            case "草":
                return "grass";
            case "電":
                return "electric";
            default:
                return "normal";
        }
    }

    private Tab createStatsTab() {
        VBox layout = new VBox(20);
        layout.setAlignment(Pos.CENTER);
        layout.setStyle("-fx-padding: 40;");

        Button btnAdd = new Button("DEBUG: 增加資源 200");
        Button btnAdd1 = new Button("DEBUG: 增加資源 100");
        Button btnAdd2 = new Button("DEBUG: 增加資源 50");
        btnAdd.setOnAction(e -> {
            gameManager.addFocusTime(200, getCurrentPokemonId()); // 模擬加錢
            refreshCurrencyLabels();
            refreshXpDisplay();
            refreshPokedexGrid();
            saveUserProgressSafely();
        });

        btnAdd1.setOnAction(e -> {
            gameManager.addFocusTime(100, getCurrentPokemonId()); // 模擬加錢
            refreshCurrencyLabels();
            refreshXpDisplay();
            refreshPokedexGrid();
            saveUserProgressSafely();
        });

        btnAdd2.setOnAction(e -> {
            gameManager.addFocusTime(50, getCurrentPokemonId()); // 模擬加錢
            refreshCurrencyLabels();
            refreshXpDisplay();
            refreshPokedexGrid();
            saveUserProgressSafely();
        });

        layout.getChildren().addAll(new Label("數據統計區"), new Separator(), btnAdd, btnAdd1, btnAdd2);
        return new Tab("數據分析", layout);
    }

}