package jfocus.ui;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement; // 在最上方加入這行
import com.google.gson.JsonObject;

import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import jfocus.ai.distraction.DistractionHandlingMode;
import jfocus.ai.distraction.JdbcDistractionModeRepository;
import jfocus.db.DatabaseCore;
import jfocus.io.UserData;
import jfocus.main.FocusApp;

public class FocusUI extends Application {

    private static final Map<String, String> TYPE_TO_FILE = Map.ofEntries(
        Map.entry("水", "Water"), Map.entry("火", "Fire"), Map.entry("草", "Grass"),
        Map.entry("毒", "Poison"), Map.entry("蟲", "Bug"), Map.entry("飛行", "Flying"),
        Map.entry("一般", "Normal"), Map.entry("電", "Electric"), Map.entry("冰", "Ice"),
        Map.entry("格鬥", "Fighting"), Map.entry("地面", "Ground"), Map.entry("岩石", "Rock"),
        Map.entry("超能力", "Psychic"), Map.entry("幽靈", "Ghost"), Map.entry("龍", "Dragon"),
        Map.entry("惡", "Dark"), Map.entry("鋼", "Steel"), Map.entry("妖精", "Fairy")
    );

    private GameManager gameManager = new GameManager();
    private Scene mainScene; // 宣告全域的 Scene 以便切換主題
    private Scene detailScene;
    private Stage primaryStage;
    private Stage detailStage;
    // --- 核心數據 (未來會與 JSON 對接) ---
    private String currentPokemonFolder = "004_charmander"; // 預設小火龍
    private int currentStage = 1;
    private Label coinLabel = new Label("0");
    private Label stoneLabel = new Label("0");

    // --- UI 元件 ---
    private Label timerLabel = new Label("25:00");
    private ImageView pokemonImageView = new ImageView();
    private Label statusLabel = new Label("準備好冒險了嗎？");

    // 【新增這行】把抽獎訊息標籤變成全域變數
    private Label gachaMessageLabel = new Label("來試試手氣吧！");

    private Button startBtn = new Button("開始冒險");
    private Button stopBtn = new Button("停止");
    private ChoiceBox<String> modeSelector = new ChoiceBox<>();

    private TextField workInput = new TextField("25");
    private TextField breakInput = new TextField("5");

    private ImageView ballView;
    private Button normalBtn;
    private Button premiumBtn;
    // 加在最上面的變數宣告區
    private TimerView timerView;
    private String css = "PokemonDark.css";
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

    public void refreshOnEnded() {
        refreshCurrencyLabels();
        refreshPokedexGrid();
        timerView.refreshXpDisplay();
    }

    private void refreshCurrencyLabels() {
        coinLabel.setText(String.valueOf(gameManager.getFocusCoins()));
        stoneLabel.setText(String.valueOf(gameManager.getMasterStones()));
    }
    
    private void refreshDrawBtnStatus() {
        int tempCoins = gameManager.getFocusCoins();
        int tempStones = gameManager.getMasterStones();
        if (tempCoins < 200 && tempStones < 1) {
            normalBtn.setDisable(true);
            premiumBtn.setDisable(true);
            gachaMessageLabel.setText("資源不夠啦！再去專注幾分鐘吧！");
            gachaMessageLabel.setStyle(
                    "-fx-text-fill: #e74c3c; -fx-font-size: 16px; -fx-font-weight: bold; -fx-padding: 10 20;");
        } else {
            if (tempCoins < 200) {
                normalBtn.setDisable(true);
                premiumBtn.setDisable(false);
            } else if (tempStones < 1) {
                normalBtn.setDisable(false);
                premiumBtn.setDisable(true);
            } else {
                normalBtn.setDisable(false);
                premiumBtn.setDisable(false);
            }
            gachaMessageLabel.setText("來試試手氣吧！");
            gachaMessageLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-padding: 10 20;");
        }
    }

    private void initCurrencyIcons() {
        setLabeledIcon(coinLabel, "res/icon/coinlabel.png");
        setLabeledIcon(stoneLabel, "res/icon/stonelabel.png");
    }

    private void setLabeledIcon(Labeled labeled, String path) {
        File file = new File(path);
        if (file.exists()) {
            ImageView iv = new ImageView(new Image(file.toURI().toString()));
            iv.setFitWidth(20);
            iv.setFitHeight(20);
            iv.setPreserveRatio(true);
            labeled.setGraphic(iv);
        }
    }

    private void loadUserProgressSafely() {
        try {
            int[] stats = UserData.loadPlayerStats();
            Set<String> unlockedStages = UserData.loadUnlockedStages();
            Map<String, Integer> pokemonXpMap = UserData.loadPokemonXp();
            gameManager.initializePlayerState(stats[0], stats[1], stats[2], unlockedStages, pokemonXpMap);
            refreshCurrencyLabels();
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
        timerView.refreshXpDisplay();
        refreshPokedexGrid();
        saveUserProgressSafely();

        timerLabel.setStyle("-fx-font-size: 80px; -fx-text-fill: #f39c12; -fx-font-weight: bold;");
        statusLabel.setText("本輪已結算，獲得 " + settledMinutes + " 專注幣與 XP！");
    }

    private void showStopSettlementDialog() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initModality(Modality.APPLICATION_MODAL);
        alert.setTitle("冒險結算");
        alert.setHeaderText("要繼續冒險，還是現在結算？");
        alert.setContentText("結算會把本輪專注時間換成獎勵。\n你也可以選擇繼續，不進行結算。");

        ButtonType continueButton = new ButtonType("繼續專冒險");
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
            statusLabel.setText("已返回冒險模式，繼續加油！");
        }
    }

    private void loadPokedexData() {
        pokedexList.clear();
        File jsonFile = new File("res/pokemon_data.json");

        if (!jsonFile.exists()) {
            System.err.println("找不到數據檔案: " + jsonFile.getAbsolutePath());
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

            System.out.println("數據載入成功！共 " + pokedexList.size() + " 隻。");

        } catch (Exception e) {
            System.err.println("解析 JSON 失敗: " + e.getMessage());
        }
    }

    /**
     * 動態切換 CSS 主題
     */
    private void switchTheme(String cssFileName) {
        Scene allScene[] = { mainScene, detailScene };
        for (Scene targetScene : allScene){
            if (targetScene == null)
                return;

            File cssFile = new File("res/css/" + cssFileName);
            File typesFile = new File("res/css/PokemonTypes.css");
            if (cssFile.exists()) {
                targetScene.getStylesheets().clear();
                targetScene.getStylesheets().add(cssFile.toURI().toString());
                if (typesFile.exists()) {
                    targetScene.getStylesheets().add(typesFile.toURI().toString());
                }
            } else {
                System.err.println("找不到主題檔案: " + cssFileName);
            }
        }
        
    }


    private Tab createTimerTab() {
        Tab tab = new Tab("專注計時");
        tab.setClosable(false);

        this.timerView = new TimerView(this.gameManager, this);

        // 👇 【新增這段：開機喚醒寶可夢！】
        // 取得當前出戰夥伴的 ID
        String currentPartnerId = gameManager.getCurrentPokemonId();

        // 如果有存檔，就去圖鑑列表 (pokedexList) 找這隻寶可夢的資料
        if (currentPartnerId != null && !currentPartnerId.isEmpty()) {
            for (PokemonData data : pokedexList) {
                if (data.getId().equals(currentPartnerId)) {
                    // 取得它現在進化到第幾階段
                    int currentStage = gameManager.getEvolutionStage(data.getId());

                    String partnerName = data.getStageName(currentStage);

                    // 組合圖片路徑並載入
                    String imgPath = "res/pokemon/" + data.getFolderName() + "/stage" + currentStage + ".png";
                    java.io.File imgFile = new java.io.File(imgPath);
                    if (imgFile.exists()) {
                        javafx.scene.image.Image initImage = new javafx.scene.image.Image(imgFile.toURI().toString());
                        // 把名字和圖片傳給 TimerView！
                        this.timerView.updatePartnerDisplay(partnerName, initImage);
                    }
                    break;
                }
            }
        }

        tab.setContent(this.timerView);
        return tab;
    }
    
    @Override
    public void start(javafx.stage.Stage primaryStage) {
        this.primaryStage = primaryStage;
        for (String w : new String[]{"ExtraLight","Light","Regular","Medium","SemiBold","Bold","ExtraBold","Black"}) {
            File f = new File("res/css/fonts/ChironGoRoundTC-" + w + ".ttf");
            if (f.exists()) javafx.scene.text.Font.loadFont(f.toURI().toString(), 12);
        }
        loadPokedexData();
        loadUserProgressSafely();

        // 1. 初始化 TabPane 與分頁
        javafx.scene.control.TabPane tabPane = new javafx.scene.control.TabPane();

        Tab focusTab = createTimerTab(); // 👉 綁定你的開機喚醒邏輯
        focusTab.setClosable(false);
        Tab pokedexTab = createPokedexTab();
        pokedexTab.setClosable(false);
        Tab statsTab = createStatsTab();
        statsTab.setClosable(false);
        Tab gachaTab = createGachaTab();
        gachaTab.setClosable(false);
        gachaTab.setOnSelectionChanged(e -> { if (gachaTab.isSelected()) refreshDrawBtnStatus(); });

        tabPane.getTabs().addAll(focusTab, gachaTab, pokedexTab, statsTab);

        // 2. 頂部狀態列 (主題切換 + 貨幣)
        ChoiceBox<String> themeSelector = new ChoiceBox<>();
        themeSelector.getItems().addAll("暗黑電競", "明亮清新", "經典紅", "大師球");
        themeSelector.setValue("暗黑電競"); 

        themeSelector.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            css = switch (newVal) {
                case "暗黑電競" -> "PokemonDark.css";
                case "明亮清新" -> "PokemonLight.css";
                case "經典紅" -> "PokemonRed.css";
                case "大師球" -> "PokemonPurple.css";
                default -> "PokemonDark.css";
            };
            switchTheme(css);
        });

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        javafx.scene.layout.HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        HBox topBar = new HBox(15, themeSelector, spacer, coinLabel, stoneLabel);
        topBar.setAlignment(Pos.CENTER);
        topBar.setPadding(new javafx.geometry.Insets(10, 20, 10, 5));
        topBar.getStyleClass().add("top-bar");

        // 3. 佈局組合：上面是狀態列，下面是分頁內容
        VBox rootLayout = new VBox(topBar, tabPane);
        javafx.scene.layout.VBox.setVgrow(tabPane, javafx.scene.layout.Priority.ALWAYS);

        mainScene = new Scene(rootLayout, 530, 750);
        switchTheme("PokemonDark.css");

        // 4. 刷新初始狀態
        setupButtonStyles();
        initCurrencyIcons();
        refreshCurrencyLabels();
        timerView.refreshXpDisplay();

        // 5. 設定視窗與關閉事件 (保留組員的通知關閉邏輯)
        primaryStage.setTitle("JFocus - Pokemon Focus Sentinel");
        primaryStage.setScene(mainScene);
        primaryStage.setOnCloseRequest(event -> {
            saveUserProgressSafely();
            FocusApp.shutdownNotificationService();
        });
        primaryStage.setResizable(false);
        primaryStage.show();

        updatePokemonDisplay(currentPokemonFolder, currentStage);
    }

    public void bringToFront() {
        if (primaryStage == null) return;
        Platform.runLater(() -> {
            primaryStage.setAlwaysOnTop(true);
            primaryStage.toFront();
            primaryStage.requestFocus();
            primaryStage.setAlwaysOnTop(false);
        });
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
            Thread t = new Thread(() -> {
                long pid = ProcessHandle.current().pid();
                String script = """
                        tell application "System Events"
                            set frontmost of first application process whose unix id is %d to true
                        end tell
                        """.formatted(pid);
                try {
                    Process p = new ProcessBuilder("/usr/bin/osascript", "-e", script).start();
                    p.waitFor();
                } catch (IOException e) {
                    System.err.println("bringToFront osascript failed: " + e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                Platform.runLater(() -> {
                    primaryStage.toFront();
                    primaryStage.requestFocus();
                });
            }, "jfocus-bring-to-front");
            t.setDaemon(true);
            t.start();
        }
    }

    // 抽獎動畫 (加入 drawType 參數)
    private void playGachaAnimation(ImageView ballView, String drawType) {
        String resultId = gameManager.performPokeBallDraw(drawType);
        if ("INSUFFICIENT_FUNDS".equals(resultId)) {
            gachaMessageLabel.setText("資源不夠啦！再去專注幾分鐘吧！");
            gachaMessageLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 16px; -fx-font-weight: bold; -fx-padding: 10 20;");
            return;
        }

        normalBtn.setDisable(true);
        premiumBtn.setDisable(true);
            
        RotateTransition shake = new RotateTransition(javafx.util.Duration.millis(100), ballView);
        shake.setFromAngle(-15);
        shake.setToAngle(15);
        shake.setCycleCount(10);
        shake.setAutoReverse(true);

        shake.setOnFinished(event -> {
            // 3. 換圖並噴發效果
            if (drawType .equals("NORMAL")) {
                ballView.setImage(new Image("file:res/pokemon/000_ball.png"));
            } else {
                ballView.setImage(new Image("file:res/pokemon/000_masterball.png"));  
            }

            ScaleTransition pop = new ScaleTransition(javafx.util.Duration.millis(400), ballView);
            pop.setFromX(0.5);
            pop.setFromY(0.5);
            pop.setToX(1.3);
            pop.setToY(1.3);
            pop.setOnFinished(e2 -> {
                ScaleTransition settle = new ScaleTransition(javafx.util.Duration.millis(300), ballView);
                ballView.setImage(new Image("file:res/pokemon/" + resultId + "/stage1.png"));
                settle.setToX(1.0);
                settle.setToY(1.0);
                settle.play();
            });
            pop.play();

            refreshCurrencyLabels();
            timerView.refreshXpDisplay();
            refreshPokedexGrid();
            saveUserProgressSafely();

            // --- 【新增】從 pokedexList 找出中文名稱 ---
            String caughtName = "未知精靈";
            for (PokemonData data : pokedexList) {
                if (data.getFolderName().equals(resultId)) {
                    caughtName = data.getName();
                    break;
                }
            }

            // 4. 動畫結束，顯示超有成就感的中獎訊息！
            gachaMessageLabel.setText("恭喜！收服了：" + caughtName + "！");
            gachaMessageLabel.setStyle("-fx-text-fill: #2ecc71; -fx-font-size: 16px; -fx-font-weight: bold; -fx-padding: 10 20;");

            PauseTransition delay = new PauseTransition(Duration.seconds(3.5));
            delay.setOnFinished(e -> {
                ballView.setImage(new Image("file:res/pokemon/000_ball.png"));
                refreshDrawBtnStatus();
            });
            delay.play();
        });

        shake.play();
    }

    // 抽獎主邏輯
    private Tab createGachaTab() {
        gachaMessageLabel.setText("來試試手氣吧！");
        gachaMessageLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-padding: 10 20;");
        gachaMessageLabel.setMinHeight(56);
        VBox layout = new VBox(20);
        layout.setAlignment(Pos.CENTER);
        //layout.setStyle("-fx-background-color: #34495e; -fx-padding: 40;");

        Label title = new Label("寶可夢孵育中心");
        title.setStyle("-fx-font-size: 32px; -fx-font-weight: bold;");
        title.getStyleClass().add("changeColor");

        // 抽獎展示區
        StackPane gachaDisplay = new StackPane();
        ballView = new ImageView(new Image("file:res/pokemon/000_ball.png"));
        ballView.setFitHeight(150);
        ballView.setPreserveRatio(true);
        gachaDisplay.getChildren().add(ballView);

        // --- 【修改區塊：雙按鈕與互動邏輯】 ---
        normalBtn = new Button("普通球抽獎");
        normalBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20;");

        Label normalCostLabel = new Label("200 / 抽");
        setLabeledIcon(normalCostLabel, "res/icon/coinlabel.png");
        normalCostLabel.setStyle("-fx-font-size: 14px;");

        VBox normalBox = new VBox(8, normalBtn, normalCostLabel);
        normalBox.setAlignment(Pos.CENTER);

        premiumBtn = new Button("大師球抽獎");
        premiumBtn.setStyle("-fx-background-color: #8e44ad; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20;");

        Label premiumCostLabel = new Label("1 / 抽");
        setLabeledIcon(premiumCostLabel, "res/icon/stonelabel.png");
        premiumCostLabel.setStyle("-fx-font-size: 14px;");

        VBox premiumBox = new VBox(8, premiumBtn, premiumCostLabel);
        premiumBox.setAlignment(Pos.CENTER);

        HBox btnBox = new HBox(20, normalBox, premiumBox);
        btnBox.setAlignment(Pos.CENTER);

        // 滑鼠懸停切換精靈球圖片
        normalBtn.setOnMouseEntered(e -> {
            ballView.setImage(new Image("file:res/pokemon/000_ball.png"));
        });
        premiumBtn.setOnMouseEntered(e -> {
            ballView.setImage(new Image("file:res/pokemon/000_masterball.png"));
        });

        // 點擊事件：呼叫動畫並傳入對應標籤
        normalBtn.setOnAction(e -> {
            playGachaAnimation(ballView, "NORMAL");
        });
        premiumBtn.setOnAction(e -> {
            playGachaAnimation(ballView, "MASTERBALL");
        });
        // ------------------------------------

        layout.getChildren().addAll(title, gachaDisplay, gachaMessageLabel, btnBox);
        return new Tab("精靈抽獎", layout);
    }

    private void setupButtonStyles() {
        startBtn.setStyle(
                "-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 30;");
        stopBtn.setStyle(
                "-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 30;");
    }

    public String getCurrentPokemonFolder() {
        return currentPokemonFolder;
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
        return new Tab("寶可夢圖鑑", scrollPane);
    }

    private void refreshPokedexGrid() {
        if (pokedexFlowGrid == null) {
            return;
        }

        pokedexFlowGrid.getChildren().clear();
        pokedexFlowGrid.setAlignment(Pos.CENTER);

        int count = 0;
        for (PokemonData data : pokedexList) {
            for (int i = 1; i <= 3; i++) {
                VBox card = createPokemonCard(data, i);
                pokedexFlowGrid.getChildren().add(card);
            }

            if (data == pokedexList.getLast()) {
                continue;
            }

            if (count == 0) {
                Separator separatorVer = new Separator();
                separatorVer.setOrientation(Orientation.VERTICAL);
                pokedexFlowGrid.getChildren().add(separatorVer);
                count++;
            } else if (count == 1) {
                Separator separatorHor1 = new Separator();
                Separator separatorHor2 = new Separator();
                separatorHor1.setMinWidth(230);
                separatorHor2.setMinWidth(230);
                HBox separatorHorBox = new HBox(20, separatorHor1, separatorHor2);
                pokedexFlowGrid.getChildren().add(separatorHorBox);
                count = 0;
            }
        }
    }

    // FocusUI.java 裡面的 createPokemonCard 方法

    private VBox createPokemonCard(PokemonData data, int stages) {
        VBox card = new VBox();
        card.getStyleClass().add("pokemon-card");
        card.setPrefSize(70, 90);
        card.setAlignment(Pos.CENTER);

        HBox typeBadges = new HBox(2);
        typeBadges.setPickOnBounds(false);
        List<String> types = data.getTypes();
        if (types != null) {
            for (String type : types) {
                String eng = TYPE_TO_FILE.get(type.trim());
                if (eng != null) {
                    java.io.File typeFile = new java.io.File("res/pokemon_type/" + eng + "_Icon.png");
                    if (typeFile.exists()) {
                        ImageView typeIcon = new ImageView(new Image(typeFile.toURI().toString()));
                        typeIcon.setFitHeight(14);
                        typeIcon.setPreserveRatio(true);
                        typeBadges.getChildren().add(typeIcon);
                    }
                }
            }
        }

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
            System.err.println("找不到圖片: " + relativePath);
            // 如果找不到圖片，可以設一個預設圖片，防止 UI 空白
            // view.setImage(new Image("file:res/pokemon/unknown.png"));
        }

        view.setFitWidth(60);
        view.setPreserveRatio(true);

        // 從 GameManager 檢查這隻寶可夢的這一個階段是否解鎖
        boolean isUnlocked = gameManager.isStageUnlocked(data.getId(), stages);

        if (!isUnlocked) {
            //【修復黑影】：尚未解鎖：利用 ColorAdjust 變黑！
            ColorAdjust blackout = new ColorAdjust();
            blackout.setBrightness(-1.0); // 100% 變黑
            view.setEffect(blackout);

            card.getStyleClass().add("locked");

            // 尚未解鎖：點擊只顯示警告，不跳出詳細視窗
            card.setOnMouseClicked(e -> {
                statusLabel.setText("這隻精靈尚未解鎖喔！去補給站試試手氣吧！");
                statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;"); // 警告紅字
            });

            StackPane stack = new StackPane();
            stack.setPrefSize(70, 90);
            stack.getChildren().addAll(view);
            StackPane.setAlignment(view, Pos.BOTTOM_CENTER);
            card.getChildren().add(stack);

        } else {
            //【修復解鎖】：已經解鎖：正常顯示，點擊跳出詳細視窗
            view.setEffect(null); // 清除效果
            card.setCursor(Cursor.HAND);
            card.getStyleClass().remove("locked");

            card.setOnMouseClicked(e -> {
                // 抓取各階段專屬名字 (例如: 卡咪龜)
                String realName = data.getStageName(stages);

                // 終極防呆：確保就算沒抓到描述，也不會當機！
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
                        safeDescription);
            });

            StackPane stack = new StackPane();
            stack.setPrefSize(70, 90);
            stack.getChildren().addAll(view, typeBadges);
            StackPane.setAlignment(view, Pos.BOTTOM_CENTER);
            StackPane.setAlignment(typeBadges, Pos.TOP_LEFT);
            StackPane.setMargin(typeBadges, new Insets(4, 0, 0, 4));
            card.getChildren().add(stack);
        }
        // ============================================

        return card;
    }

    // 在 FocusApp.java 類別中

    /**
     * 顯示選中精靈的詳細資訊 (強化版：加入彩色屬性 Tag 與出戰按鈕)
     * @param folder 資料夾名稱 (如 007_squirtle)
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
    public void showDetailView(String id, String folder, int stages, String name, List<String> types, String description) {
        if (detailStage != null && detailStage.isShowing()) {
            detailStage.close();
        }
        detailStage = new Stage();
        detailStage.initOwner(primaryStage);
        VBox layout = new VBox(15);
        layout.setAlignment(Pos.CENTER);
        layout.getStyleClass().add("detailView");

        ImageView bigView = new ImageView();

        // 同樣用 File 轉 URI 的安全寫法
        java.io.File bigImgFile = new java.io.File("res/pokemon/" + folder + "/stage" + stages + ".png");
        if (bigImgFile.exists()) {
            bigView.setImage(new Image(bigImgFile.toURI().toString()));
        }

        bigView.setFitWidth(280);
        bigView.setPreserveRatio(true);

        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        // 屬性標籤
        HBox typeBox = new HBox(8);
        typeBox.setAlignment(Pos.CENTER);
        for (String t : types) {
            String eng = TYPE_TO_FILE.get(t.trim());
            java.io.File typeFile = eng != null ? new java.io.File("res/pokemon_type/" + eng + "_Icon.png") : null;

            HBox typeBadge = new HBox(4);
            typeBadge.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            typeBadge.getStyleClass().addAll("type-tag", eng != null ? eng.toLowerCase() : "");

            if (typeFile != null && typeFile.exists()) {
                ImageView typeIcon = new ImageView(new Image(typeFile.toURI().toString()));
                typeIcon.setFitHeight(20);
                typeIcon.setPreserveRatio(true);
                typeBadge.getChildren().add(typeIcon);
            }

            Label tag = new Label(t.trim());
            tag.getStyleClass().add("type-text");
            typeBadge.getChildren().add(tag);

            typeBox.getChildren().add(typeBadge);
        }

        Label descLabel = new Label(description);
        descLabel.setMaxWidth(300);
        descLabel.setMaxHeight(40);
        descLabel.setWrapText(true);
        descLabel.setTextAlignment(TextAlignment.CENTER);

        Button selectBtn = new Button("選擇出戰");
        java.io.File swordFile = new java.io.File("res/pokemon/sword.png");
        if (swordFile.exists()) {
            ImageView swordIcon = new ImageView(new Image(swordFile.toURI().toString()));
            swordIcon.setFitHeight(18);
            swordIcon.setPreserveRatio(true);
            selectBtn.setGraphic(swordIcon);
            selectBtn.setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
        }
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
            timerView.refreshXpDisplay();
            // 替換掉你截圖裡報錯的那一行：
            this.timerView.updatePartnerDisplay(name, bigView.getImage());
            detailStage.close();
        });

        // --- 組合視窗的剩餘程式碼 (照你原本的寫法) ---
        layout.getChildren().addAll(bigView, nameLabel, typeBox, descLabel, selectBtn);
        detailScene = new Scene(layout, 400, 550);
        switchTheme(css);
        detailStage.setScene(detailScene);
        detailStage.show();
    }

    private Tab createStatsTab() {
        VBox layout = new VBox(20);
        layout.setAlignment(Pos.CENTER);
        layout.setStyle("-fx-padding: 40;");

        Button btnAdd = new Button("DEBUG: 增加資源 200");
        Button btnAdd1 = new Button("DEBUG: 增加資源 100");
        Button btnAdd2 = new Button("DEBUG: 增加資源 50");
        JdbcDistractionModeRepository modeRepository = new JdbcDistractionModeRepository(new DatabaseCore());
        DistractionHandlingMode currentMode = modeRepository.loadMode(DistractionHandlingMode.WARN_USER);
        Label modeStatusLabel = new Label("目前分心處理模式: " + currentMode.name());
        ToggleButton modeSwitch = createDistractionModeSwitch(currentMode, modeStatusLabel, modeRepository);
        Label warnLabel = new Label("提醒");
        Label closeLabel = new Label("關閉");
        HBox modeSwitchRow = new HBox(12, warnLabel, modeSwitch, closeLabel);
        modeSwitchRow.setAlignment(Pos.CENTER);

        btnAdd.setOnAction(e -> {
            gameManager.addFocusTime(200, getCurrentPokemonId()); // 模擬加錢
            refreshCurrencyLabels();
            refreshPokedexGrid();
            saveUserProgressSafely();
            timerView.refreshXpDisplay();
        });

        btnAdd1.setOnAction(e -> {
            gameManager.addFocusTime(100, getCurrentPokemonId()); // 模擬加錢
            refreshCurrencyLabels();
            refreshPokedexGrid();
            saveUserProgressSafely();
            timerView.refreshXpDisplay();
        });

        btnAdd2.setOnAction(e -> {
            gameManager.addFocusTime(50, getCurrentPokemonId()); // 模擬加錢
            refreshCurrencyLabels();
            refreshPokedexGrid();
            saveUserProgressSafely();
            timerView.refreshXpDisplay();
        });

        layout.getChildren().addAll(
                new Label("數據統計區"),
                new Separator(),
                modeStatusLabel,
                modeSwitchRow,
                new Separator(),
                btnAdd,
                btnAdd1,
                btnAdd2);
        return new Tab("數據分析", layout);
    }

    private ToggleButton createDistractionModeSwitch(
            DistractionHandlingMode initialMode,
            Label modeStatusLabel,
            JdbcDistractionModeRepository modeRepository) {
        ToggleButton toggle = new ToggleButton();
        toggle.setSelected(initialMode == DistractionHandlingMode.CLOSE_DISTRACTION);
        toggle.setCursor(Cursor.HAND);
        toggle.setFocusTraversable(true);
        toggle.setMinSize(66, 40);
        toggle.setPrefSize(66, 40);
        toggle.setStyle("-fx-background-color: transparent; -fx-padding: 0;");

        Rectangle track = new Rectangle(60, 34);
        track.setArcWidth(34);
        track.setArcHeight(34);

        Circle thumb = new Circle(14);
        thumb.setFill(Color.WHITE);
        thumb.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.28), 5, 0, 0, 1);");

        StackPane switchGraphic = new StackPane(track, thumb);
        switchGraphic.setPadding(new Insets(3));
        switchGraphic.setAlignment(Pos.CENTER_LEFT);
        toggle.setGraphic(switchGraphic);

        updateSlidingSwitchVisual(toggle, track, thumb, false);

        toggle.selectedProperty().addListener((obs, oldValue, selected) -> {
            DistractionHandlingMode selectedMode = selected
                    ? DistractionHandlingMode.CLOSE_DISTRACTION
                    : DistractionHandlingMode.WARN_USER;
            modeRepository.saveMode(selectedMode);
            if (timerView != null) {
                timerView.setDistractionHandlingMode(selectedMode);
            }
            modeStatusLabel.setText("目前分心處理模式: " + selectedMode.name());
            updateSlidingSwitchVisual(toggle, track, thumb, true);
            System.out.println("[DEBUG][Distraction] Stats tab switch mode: " + selectedMode.name());
        });

        return toggle;
    }

    private void updateSlidingSwitchVisual(ToggleButton toggle, Rectangle track, Circle thumb, boolean animated) {
        boolean selected = toggle.isSelected();
        track.setFill(selected ? Color.web("#27ae60") : Color.web("#7f8c8d"));

        double targetX = selected ? 29 : 3;
        if (!animated) {
            thumb.setTranslateX(targetX);
            return;
        }

        TranslateTransition transition = new TranslateTransition(Duration.millis(160), thumb);
        transition.setToX(targetX);
        transition.play();
    }

}
