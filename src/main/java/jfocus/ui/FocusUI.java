package jfocus.ui;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement; // 在最上方加入這行
import com.google.gson.JsonObject;

import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
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
    private Scene evolutionScene;
    private Stage primaryStage;
    private Stage detailStage;
    // --- 核心數據 (未來會與 JSON 對接) ---
    private String currentPokemonFolder = "004_charmander"; // 預設小火龍
    private int currentStage = 1;
    private final java.util.Map<String, Integer> selectedStageMap = new java.util.HashMap<>();
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
    private final javafx.beans.property.BooleanProperty sessionActive =
            new javafx.beans.property.SimpleBooleanProperty(false);
    public javafx.beans.property.BooleanProperty sessionActiveProperty() { return sessionActive; }

    // 加在最上面的變數宣告區
    private TimerView timerView;
    private Canvas borderCanvas;
    private AnimationTimer currentBorderTimer;
    private javafx.scene.control.TabPane tabPane;
    private String css = "PokemonDark.css";
    static final List<String> activeStylesheets = new ArrayList<>();
    private StatsView statsView;
    private TodoView todoView;
    // 圖鑑相關元件
    // private ImageView bigView;
    // private ComboBox<String> stageSelector;
    // private Label pokemonNameLabel;
    // private String selectedPokemonId;
    // private String selectedPokemonName;

    // Json
    // 1. 宣告清單變數
    private List<PokemonData> pokedexList = new ArrayList<>();
    private final Set<String> activeTypeFilters = new HashSet<>();
    private final Map<String, VBox> pokedexCardCache = new HashMap<>();
    private FlowPane pokedexGrid1star;
    private FlowPane pokedexGrid2star;
    private FlowPane pokedexGrid3star;
    private VBox pokedexSection1;
    private VBox pokedexSection2;
    private VBox pokedexSection3;

    // 2. 建立一個內部類別來對應 JSON 資料格式 (POJO)
    // 1. 確保類別定義是這樣的 (在 FocusApp 類別內)

public static class PokemonData {
        private String id;
        private String folderName;
        private String name;
        private List<String> types;
        private List<String> descriptions; 
        private List<String> stageNames; 
        private String rarity; // 🌟 新增：稀有度變數

        // 🌟 建構子最後面加上 String rarity
        public PokemonData(String id, String folderName, String name, List<String> types,
                List<String> descriptions, List<String> stageNames, String rarity) {
            this.id = id;
            this.folderName = folderName;
            this.name = name;
            this.types = types;
            this.descriptions = descriptions;
            this.stageNames = stageNames;
            this.rarity = rarity; // 🌟 綁定稀有度
        }

        // 🌟 新增：給 GameManager 讀取稀有度的方法 (防呆預設為普通池)
        public String getRarity() { return this.rarity == null ? "STANDARD" : this.rarity; }

        public String getId() { return id; }
        public String getFolderName() { return folderName; }
        public String getName() { return name; }
        public List<String> getTypes() { return types; }
        public List<String> getDescriptions() { return descriptions; }
        public List<String> getStageNames() { return stageNames; }

        public String getStageName(int stage) {
            if (stageNames != null && stageNames.size() >= stage) {
                return stageNames.get(stage - 1);
            }
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

    String getCurrentPokemonId() {
        if (currentPokemonFolder == null || currentPokemonFolder.length() < 3) {
            return null;
        }

        String pokemonId = currentPokemonFolder.substring(0, 3);
        if (!pokemonId.matches("\\d{3}")) {
            return null;
        }

        return pokemonId;
    }

    private int getDisplayStageForPokemon(String pokemonId) {
        return selectedStageMap.getOrDefault(pokemonId, gameManager.getEvolutionStage(pokemonId));
    }

    public void refreshOnEnded() {
        refreshCurrencyLabels();
        refreshPokedexGrid();
        timerView.refreshXpDisplay();
    }

    void refreshCurrencyLabels() {
        coinLabel.setText(String.valueOf(gameManager.getFocusCoins()));
        stoneLabel.setText(String.valueOf(gameManager.getMasterStones()));
    }

    void refreshXpDisplay() {
        if (timerView != null) {
            timerView.refreshXpDisplay();
        }
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
            selectedStageMap.putAll(UserData.loadSelectedStages());
            refreshCurrencyLabels();
        } catch (RuntimeException ex) {
            statusLabel.setText("讀取存檔失敗，將使用預設資料。");
            System.err.println("讀取存檔失敗: " + ex.getMessage());
        }
    }

    void saveUserProgressSafely() {
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
                // 🌟 新增：從 JSON 抓出 rarity，如果沒寫就預設給 "STANDARD"
                String rarity = obj.has("rarity") ? obj.get("rarity").getAsString() : "STANDARD";

                // 🌟 把 rarity 傳進去！
                pokedexList.add(new PokemonData(id, folderName, name, types, descriptions, stageNames, rarity));

            }

            System.out.println("✅ 數據載入成功！共 " + pokedexList.size() + " 隻。");

            // 🌟 【關鍵修改】：由 FocusUI 自己分類，只傳字串給 GameManager！
            List<String> standardPool = new ArrayList<>();
            List<String> rarePool = new ArrayList<>();

            for (PokemonData data : pokedexList) {
                // 不分大小寫檢查，只要是 RARE 就丟大師池，其餘丟普通池
                String r = data.getRarity() == null ? "" : data.getRarity().trim().toUpperCase();
                if (r.equals("RARE") || r.equals("UNCOMMON")) {
                    rarePool.add(data.getFolderName());
                } else {
                    standardPool.add(data.getFolderName());
                }
            }

            // 將分類好的純字串名單交給 GameManager
            gameManager.setGachaPools(standardPool, rarePool);

        } catch (Exception e) {
            System.err.println("❌ 解析 JSON 失敗: " + e.getMessage());
        }
    }


    private void switchTheme(String cssFileName) {
        File globalFile = new File("res/css/global.css");
        File cssFile = new File("res/css/" + cssFileName);
        File typesFile = new File("res/css/pokemonTypes.css");
        if (!cssFile.exists()) {
            System.err.println("找不到主題檔案: " + cssFileName);
            return;
        }

        List<String> sheets = new ArrayList<>();
        if (globalFile.exists()) sheets.add(globalFile.toURI().toString());
        sheets.add(cssFile.toURI().toString());
        if (typesFile.exists()) sheets.add(typesFile.toURI().toString());

        activeStylesheets.clear();
        activeStylesheets.addAll(sheets);

        Scene[] allScene = { mainScene, detailScene, evolutionScene };
        for (Scene targetScene : allScene) {
            if (targetScene == null) continue;
            targetScene.getStylesheets().setAll(sheets);
        }

        if (statsView != null) {
            statsView.refreshCurrentView();
        }
        if (todoView != null) {
            todoView.refreshTodoList();
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
                    int displayStage = this.currentStage > 0
                            ? this.currentStage
                            : getDisplayStageForPokemon(data.getId());
                    String partnerName = data.getStageName(displayStage);
                    String imgPath = "res/pokemon/" + data.getFolderName() + "/stage" + displayStage + ".png";
                    java.io.File imgFile = new java.io.File(imgPath);
                    if (imgFile.exists()) {
                        javafx.scene.image.Image initImage = new javafx.scene.image.Image(imgFile.toURI().toString());
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

        // 還原上次使用的角色與出戰狀態
        String savedPartnerId = gameManager.getCurrentPokemonId();
        if (savedPartnerId != null) {
            for (PokemonData data : pokedexList) {
                if (savedPartnerId.equals(data.getId())) {
                    this.currentPokemonFolder = data.getFolderName();
                    this.currentStage = selectedStageMap.getOrDefault(
                        savedPartnerId, gameManager.getEvolutionStage(savedPartnerId));
                    break;
                }
            }
        }

        // 1. 初始化 TabPane 與分頁
        tabPane = new javafx.scene.control.TabPane();

        Tab focusTab = createTimerTab(); // 👉 綁定你的開機喚醒邏輯
        focusTab.setClosable(false);
        Tab pokedexTab = createPokedexTab();
        pokedexTab.setClosable(false);
        Tab statsTab = createStatsTab();
        statsTab.setClosable(false);
        Tab gachaTab = createGachaTab();
        gachaTab.setClosable(false);
        Tab todoTab = createTodoTab();
        todoTab.setClosable(false);
        Tab settingsTab = createSettingsTab();
        settingsTab.setClosable(false);

        gachaTab.setOnSelectionChanged(e -> {
            if (gachaTab.isSelected())
                refreshDrawBtnStatus();
        });
        
        tabPane.getTabs().addAll(focusTab, gachaTab, pokedexTab, statsTab, todoTab, settingsTab);

        // 2. 頂部狀態列 (主題切換 + 貨幣)
        ChoiceBox<String> themeSelector = new ChoiceBox<>();
        themeSelector.getStyleClass().add("theme-selector");
        themeSelector.getItems().addAll("暗黑電競", "明亮清新", "經典紅", "大師球");
        String savedThemeName = jfocus.io.UserData.loadAppSetting("theme_name", "暗黑電競");
        themeSelector.setValue(savedThemeName);

        themeSelector.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            css = switch (newVal) {
                case "暗黑電競" -> "PokemonDark.css";
                case "明亮清新" -> "PokemonLight.css";
                case "經典紅" -> "PokemonRed.css";
                case "大師球" -> "PokemonPurple.css";
                default -> "PokemonDark.css";
            };
            jfocus.io.UserData.saveAppSetting("theme_name", newVal);
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

        borderCanvas = new Canvas(530, 750);
        borderCanvas.setMouseTransparent(true);
        StackPane root = new StackPane(rootLayout, borderCanvas);
        root.layoutBoundsProperty().addListener((obs, old, b) -> {
            borderCanvas.setWidth(b.getWidth());
            borderCanvas.setHeight(b.getHeight());
        });

        mainScene = new Scene(root, 530, 750);
        css = switch (savedThemeName) {
            case "明亮清新" -> "PokemonLight.css";
            case "經典紅" -> "PokemonRed.css";
            case "大師球" -> "PokemonPurple.css";
            default -> "PokemonDark.css";
        };
        switchTheme(css);

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
        updatePokemonDisplay(currentPokemonFolder, currentStage);
        primaryStage.show();
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
            String caughtName = "未知精靈";
            String drawnPokemonId = null;
            for (PokemonData data : pokedexList) {
                if (data.getFolderName().equals(resultId)) {
                    caughtName = data.getName();
                    drawnPokemonId = data.getId();
                    break;
                }
            }
            final String finalCaughtName = caughtName;
            final String finalDrawnId = drawnPokemonId;

            ScaleTransition ballExpand = new ScaleTransition(javafx.util.Duration.millis(250), ballView);
            ballExpand.setFromX(0.8);
            ballExpand.setFromY(0.8);
            ballExpand.setToX(1.4);
            ballExpand.setToY(1.4);
            ballExpand.setOnFinished(e2 -> {
                ScaleTransition ballShrink = new ScaleTransition(javafx.util.Duration.millis(150), ballView);
                ballShrink.setToX(0);
                ballShrink.setToY(0);
                ballShrink.setOnFinished(e3 -> {
                    ScaleTransition settle = new ScaleTransition(javafx.util.Duration.millis(200), ballView);
                    ballView.setImage(new Image("file:res/pokemon/" + resultId + "/stage1.png"));
                    settle.setToX(1.0);
                    settle.setToY(1.0);
                    settle.setOnFinished(e4 -> {
                        refreshCurrencyLabels();
                        timerView.refreshXpDisplay();
                        new Thread(this::saveUserProgressSafely).start();
                    });
                    settle.play();
                });
                ballShrink.play();
            });
            ballExpand.play();

            // 4. 動畫啟動前只更新輕量 UI
            gachaMessageLabel.setText("恭喜！收服了：" + finalCaughtName + "！");
            gachaMessageLabel.setStyle("-fx-text-fill: #2ecc71; -fx-font-size: 16px; -fx-font-weight: bold; -fx-padding: 10 20;");

            startBorderCountdown(3.5);
            PauseTransition delay = new PauseTransition(Duration.seconds(3.5));
            delay.setOnFinished(e -> {
                ScaleTransition ballExpandBack = new ScaleTransition(javafx.util.Duration.millis(150), ballView);
                ballExpandBack.setToX(0);
                ballExpandBack.setToY(0);
                ballExpandBack.setOnFinished(e3 -> {
                    ScaleTransition settleBack = new ScaleTransition(javafx.util.Duration.millis(200), ballView);
                    ballView.setImage(new Image("file:res/pokemon/000_ball.png"));
                    settleBack.setToX(1.0);
                    settleBack.setToY(1.0);
                    settleBack.play();
                });
                ballExpandBack.play();
                if (finalDrawnId != null) refreshPokedexGrid(finalDrawnId);
                else refreshPokedexGrid();
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
        ballView.setFitHeight(200);
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

    public int getCurrentPokemonStage() {
        return currentStage;
    }

    public PokemonData getPokemonDataById(String pokemonId) {
        if (pokemonId == null) return null;
        for (PokemonData d : pokedexList) {
            if (pokemonId.equals(d.getId())) return d;
        }
        return null;
    }

    public void showEvolutionUnlockDialog(String pokemonId, int unlockedStage) {
        PokemonData data = getPokemonDataById(pokemonId);
        if (data == null) return;

        Stage dialog = new Stage();
        dialog.initOwner(primaryStage);
        dialog.setResizable(false);

        String stageName = data.getStageName(unlockedStage);
        Label titleLabel = new Label("解鎖新狀態！");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Label msgLabel = new Label("「" + stageName + "」已解鎖！");
        msgLabel.setStyle("-fx-font-size: 14px;");
        msgLabel.setWrapText(true);
        msgLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        ImageView stageView = new ImageView();
        java.io.File imgFile = new java.io.File(
            "res/pokemon/" + data.getFolderName() + "/stage" + unlockedStage + ".png");
        if (imgFile.exists()) {
            stageView.setImage(new Image(imgFile.toURI().toString()));
        }
        stageView.setFitWidth(160);
        stageView.setPreserveRatio(true);

        Button confirmBtn = new Button("確認");
        confirmBtn.getStyleClass().add("select-button");
        confirmBtn.setOnAction(e -> {
            dialog.close();
            if (!selectedStageMap.containsKey(data.getId())) {
                int keepStage = unlockedStage - 1;
                selectedStageMap.put(data.getId(), keepStage);
                jfocus.io.UserData.saveSelectedStage(data.getId(), keepStage);
            }
            refreshPokedexGrid();
        });

        Button selectBtn = new Button("選擇出戰");
        selectBtn.getStyleClass().add("select-button");
        java.io.File swordFile = new java.io.File("res/pokemon/sword.png");
        if (swordFile.exists()) {
            ImageView swordIcon = new ImageView(new Image(swordFile.toURI().toString()));
            swordIcon.setFitHeight(18);
            swordIcon.setPreserveRatio(true);
            selectBtn.setGraphic(swordIcon);
            selectBtn.setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
        }
        selectBtn.setOnAction(e -> {
            dialog.close();
            String battleName = data.getStageName(unlockedStage);
            gameManager.setCurrentPokemonId(data.getId());
            jfocus.io.UserData.saveCurrentPartner(data.getId());
            this.currentPokemonFolder = data.getFolderName();
            this.currentStage = unlockedStage;
            selectedStageMap.put(data.getId(), unlockedStage);
            jfocus.io.UserData.saveSelectedStage(data.getId(), unlockedStage);
            java.io.File imgFile2 = new java.io.File(
                "res/pokemon/" + data.getFolderName() + "/stage" + unlockedStage + ".png");
            javafx.scene.image.Image img = imgFile2.exists()
                ? new Image(imgFile2.toURI().toString()) : null;
            updatePokemonDisplay(data.getFolderName(), unlockedStage);
            timerView.refreshXpDisplay();
            if (img != null) timerView.updatePartnerDisplay(battleName, img);
            pokedexCardCache.remove(data.getId());
            refreshPokedexGrid();
        });

        HBox btnRow = new HBox(12, confirmBtn, selectBtn);
        btnRow.setAlignment(Pos.CENTER);

        VBox layout = new VBox(14, titleLabel, stageView, msgLabel, btnRow);
        layout.setAlignment(Pos.CENTER);
        layout.getStyleClass().add("detailView");
        layout.setStyle("-fx-padding: 28;");

        evolutionScene = new Scene(layout, 300, 340);
        switchTheme(css);
        dialog.setScene(evolutionScene);
        dialog.show();
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
        // ── 屬性篩選列 ──
        FlowPane typeFilterBar = new FlowPane(6, 6);
        typeFilterBar.setPadding(new Insets(10, 15, 8, 15));

        List<HBox> typeChips = new ArrayList<>();

        // 「全部」chip
        HBox clearChip = new HBox();
        clearChip.getStyleClass().addAll("type-filter-all", "active");
        clearChip.setPadding(new Insets(2, 6, 2, 6));
        clearChip.setAlignment(Pos.CENTER);
        clearChip.setCursor(Cursor.HAND);
        Label clearLabel = new Label("全部");
        clearLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: white;");
        clearChip.getChildren().add(clearLabel);
        clearChip.setOnMouseClicked(e -> {
            activeTypeFilters.clear();
            typeChips.forEach(c -> c.getStyleClass().remove("active"));
            if (!clearChip.getStyleClass().contains("active"))
                clearChip.getStyleClass().add("active");
            refreshPokedexGrid();
        });
        typeFilterBar.getChildren().add(clearChip);

        // 各屬性 chip（按中文名排序）
        List<Map.Entry<String, String>> sortedTypes = new ArrayList<>(TYPE_TO_FILE.entrySet());
        sortedTypes.sort((a, b) -> a.getKey().compareTo(b.getKey()));

        for (Map.Entry<String, String> entry : sortedTypes) {
            String typeName = entry.getKey();
            String engName = entry.getValue();

            HBox chip = new HBox();
            chip.getStyleClass().addAll("type-tag", "type-filter-chip", engName.toLowerCase());
            chip.setAlignment(Pos.CENTER_LEFT);
            chip.setCursor(Cursor.HAND);

            java.io.File typeFile = new java.io.File("res/pokemon_type/" + engName + "_Icon.png");
            if (typeFile.exists()) {
                ImageView icon = new ImageView(new Image(typeFile.toURI().toString()));
                icon.setFitHeight(14);
                icon.setPreserveRatio(true);
                chip.getChildren().add(icon);
            }
            Label lbl = new Label(typeName);
            lbl.setStyle("-fx-font-size: 13px; -fx-text-fill: white; -fx-font-weight: bold;");
            chip.getChildren().add(lbl);

            chip.setOnMouseClicked(e -> {
                clearChip.getStyleClass().remove("active");
                if (activeTypeFilters.contains(typeName)) {
                    activeTypeFilters.remove(typeName);
                    chip.getStyleClass().remove("active");
                    if (activeTypeFilters.isEmpty())
                        clearChip.getStyleClass().add("active");
                } else {
                    activeTypeFilters.add(typeName);
                    chip.getStyleClass().add("active");
                }
                refreshPokedexGrid();
            });

            typeChips.add(chip);
            typeFilterBar.getChildren().add(chip);
        }

        // ── 稀有度區塊 ──
        Label header3 = new Label("★★★  傳說");
        header3.getStyleClass().add("rarity-header-3");
        header3.setPadding(new Insets(12, 15, 4, 15));
        pokedexGrid3star = new FlowPane(10, 10);
        pokedexGrid3star.setPadding(new Insets(5, 15, 10, 15));
        pokedexGrid3star.setAlignment(Pos.TOP_LEFT);
        pokedexSection3 = new VBox(header3, pokedexGrid3star);

        Label header2 = new Label("★★  稀有");
        header2.getStyleClass().add("rarity-header-2");
        header2.setPadding(new Insets(12, 15, 4, 15));
        pokedexGrid2star = new FlowPane(10, 10);
        pokedexGrid2star.setPadding(new Insets(5, 15, 10, 15));
        pokedexGrid2star.setAlignment(Pos.TOP_LEFT);
        pokedexSection2 = new VBox(header2, pokedexGrid2star);

        Label header1 = new Label("★  普通");
        header1.getStyleClass().add("rarity-header-1");
        header1.setPadding(new Insets(12, 15, 4, 15));
        pokedexGrid1star = new FlowPane(10, 10);
        pokedexGrid1star.setPadding(new Insets(5, 15, 10, 15));
        pokedexGrid1star.setAlignment(Pos.TOP_LEFT);
        pokedexSection1 = new VBox(header1, pokedexGrid1star);

        VBox sectionsContainer = new VBox(pokedexSection1, pokedexSection2, pokedexSection3);
        sectionsContainer.setPadding(new Insets(0, 0, 15, 0));

        refreshPokedexGrid();

        ScrollPane scrollPane = new ScrollPane(sectionsContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");

        javafx.scene.layout.BorderPane tabContent = new javafx.scene.layout.BorderPane();
        tabContent.setTop(typeFilterBar);
        tabContent.setCenter(scrollPane);
        tabContent.setStyle("-fx-background-color: transparent;");

        return new Tab("寶可夢圖鑑", tabContent);
    }

    void refreshPokedexGrid() {
        if (pokedexGrid1star == null) return;

        for (PokemonData data : pokedexList) {
            VBox card = pokedexCardCache.get(data.getId());

            if (card == null) {
                int displayStage = getDisplayStageForPokemon(data.getId());
                card = createPokemonCard(data, displayStage);
                pokedexCardCache.put(data.getId(), card);

                // 找對應 grid，有舊卡就 replace（cache invalidate 後），否則 add（第一次）
                String rarity = data.getRarity().toUpperCase();
                FlowPane targetGrid = "RARE".equals(rarity) ? pokedexGrid3star
                        : "UNCOMMON".equals(rarity) ? pokedexGrid2star : pokedexGrid1star;
                boolean replaced = false;
                for (int i = 0; i < targetGrid.getChildren().size(); i++) {
                    if (data.getId().equals(targetGrid.getChildren().get(i).getUserData())) {
                        targetGrid.getChildren().set(i, card);
                        replaced = true;
                        break;
                    }
                }
                if (!replaced) targetGrid.getChildren().add(card);
            }

            // 篩選只切 visible/managed，不重建
            boolean show = activeTypeFilters.isEmpty();
            if (!show) {
                for (String t : data.getTypes()) {
                    if (activeTypeFilters.contains(t.trim())) { show = true; break; }
                }
            }
            card.setVisible(show);
            card.setManaged(show);
        }

        boolean has3 = false, has2 = false, has1 = false;
        for (javafx.scene.Node n : pokedexGrid3star.getChildren()) if (n.isManaged()) { has3 = true; break; }
        for (javafx.scene.Node n : pokedexGrid2star.getChildren()) if (n.isManaged()) { has2 = true; break; }
        for (javafx.scene.Node n : pokedexGrid1star.getChildren()) if (n.isManaged()) { has1 = true; break; }
        pokedexSection3.setVisible(has3); pokedexSection3.setManaged(has3);
        pokedexSection2.setVisible(has2); pokedexSection2.setManaged(has2);
        pokedexSection1.setVisible(has1); pokedexSection1.setManaged(has1);

        // 同步 active-partner 高亮（快取卡片不重建，直接更新 class）
        String currentId = gameManager.getCurrentPokemonId();
        for (Map.Entry<String, VBox> entry : pokedexCardCache.entrySet()) {
            VBox card = entry.getValue();
            if (entry.getKey().equals(currentId)) {
                if (!card.getStyleClass().contains("active-partner"))
                    card.getStyleClass().add("active-partner");
            } else {
                card.getStyleClass().remove("active-partner");
            }
        }
    }

    void refreshPokedexGrid(String pokemonId) {
        if (pokedexGrid1star == null) return;
        PokemonData target = null;
        for (PokemonData d : pokedexList) {
            if (d.getId().equals(pokemonId)) { target = d; break; }
        }
        if (target == null) return;

        // 快取失效，重建這一張
        int displayStage = getDisplayStageForPokemon(target.getId());
        VBox newCard = createPokemonCard(target, displayStage);
        pokedexCardCache.put(pokemonId, newCard);

        // 套用目前篩選狀態
        boolean show = activeTypeFilters.isEmpty();
        if (!show) {
            for (String t : target.getTypes()) {
                if (activeTypeFilters.contains(t.trim())) { show = true; break; }
            }
        }
        newCard.setVisible(show);
        newCard.setManaged(show);

        // 替換對應 grid 裡的舊卡
        for (FlowPane grid : new FlowPane[]{pokedexGrid1star, pokedexGrid2star, pokedexGrid3star}) {
            for (int i = 0; i < grid.getChildren().size(); i++) {
                if (pokemonId.equals(grid.getChildren().get(i).getUserData())) {
                    grid.getChildren().set(i, newCard);
                    return;
                }
            }
        }
        refreshPokedexGrid();
    }

    private VBox createPokemonCard(PokemonData data, int stages) {
        VBox card = new VBox();
        card.setUserData(data.getId());
        card.getStyleClass().add("pokemon-card");
        if (data.getId().equals(gameManager.getCurrentPokemonId())) {
            card.getStyleClass().add("active-partner");
        }
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
        boolean Unlocked = gameManager.isStageUnlocked(data.getId(), stages);

        if (!Unlocked) {
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

            card.setOnMouseClicked(e -> showDetailView(data, stages));

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

    public void showDetailView(PokemonData data, int initialStage) {
        if (detailStage != null && detailStage.isShowing()) {
            detailStage.close();
        }
        detailStage = new Stage();
        detailStage.initOwner(primaryStage);
        detailStage.setResizable(false);

        VBox layout = new VBox(12);
        layout.setAlignment(Pos.CENTER);
        layout.getStyleClass().add("detailView");

        final int[] selectedStage = {initialStage};

        ImageView bigView = new ImageView();
        bigView.setFitWidth(240);
        bigView.setPreserveRatio(true);

        Label nameLabel = new Label();
        nameLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        Label descLabel = new Label();
        descLabel.setMaxWidth(300);
        descLabel.setMinHeight(40);
        descLabel.setWrapText(true);
        descLabel.setTextAlignment(TextAlignment.CENTER);

        Runnable updateDisplay = () -> {
            int s = selectedStage[0];
            java.io.File imgFile = new java.io.File("res/pokemon/" + data.getFolderName() + "/stage" + s + ".png");
            if (imgFile.exists()) {
                bigView.setImage(new Image(imgFile.toURI().toString()));
            }
            nameLabel.setText(data.getStageName(s));
            String desc = "這是一隻神秘的寶可夢，暫無描述。";
            if (data.getDescriptions() != null && data.getDescriptions().size() >= s) {
                desc = data.getDescriptions().get(s - 1);
            }
            descLabel.setText(desc);
        };
        updateDisplay.run();

        // 屬性標籤
        HBox typeBox = new HBox(8);
        typeBox.setAlignment(Pos.CENTER);
        List<String> types = data.getTypes() != null ? data.getTypes() : java.util.Arrays.asList("未知");
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

        // Stage 選擇器
        int totalStages = data.getStageNames() != null ? Math.min(data.getStageNames().size(), 3) : 3;
        HBox stageSelector = new HBox(10);
        stageSelector.setAlignment(Pos.CENTER);
        Button[] stageBtns = new Button[totalStages];

        for (int s = 1; s <= totalStages; s++) {
            final int stageNum = s;
            boolean unlocked = gameManager.isStageUnlocked(data.getId(), s);

            ImageView thumb = new ImageView();
            java.io.File thumbFile = new java.io.File("res/pokemon/" + data.getFolderName() + "/stage" + s + ".png");
            if (thumbFile.exists()) {
                thumb.setImage(new Image(thumbFile.toURI().toString()));
            }
            thumb.setFitHeight(70);
            thumb.setPreserveRatio(true);
            if (!unlocked) {
                ColorAdjust blackout = new ColorAdjust();
                blackout.setBrightness(-1.0);
                thumb.setEffect(blackout);
            }

            Label stageName = new Label(data.getStageName(s));
            stageName.setStyle("-fx-font-size: 12px;");

            VBox stageCard = new VBox(2, thumb, stageName);
            stageCard.setAlignment(Pos.CENTER);

            Button btn = new Button();
            btn.setGraphic(stageCard);
            btn.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
            btn.getStyleClass().add("stage-select-btn");

            if (unlocked) {
                btn.setCursor(Cursor.HAND);
                btn.setOnAction(e -> {
                    selectedStage[0] = stageNum;
                    updateDisplay.run();
                    for (Button b : stageBtns) {
                        if (b != null) b.getStyleClass().remove("stage-selected");
                    }
                    btn.getStyleClass().add("stage-selected");
                });
            } else {
                btn.getStyleClass().add("locked");
            }

            stageBtns[s - 1] = btn;
            stageSelector.getChildren().add(btn);
        }
        if (stageBtns[initialStage - 1] != null) {
            stageBtns[initialStage - 1].getStyleClass().add("stage-selected");
        }

        // 出戰按鈕
        Button selectBtn = new Button("選擇出戰");
        java.io.File swordFile = new java.io.File("res/pokemon/sword.png");
        if (swordFile.exists()) {
            ImageView swordIcon = new ImageView(new Image(swordFile.toURI().toString()));
            swordIcon.setFitHeight(18);
            swordIcon.setPreserveRatio(true);
            selectBtn.setGraphic(swordIcon);
            selectBtn.setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
        }
        selectBtn.getStyleClass().add("select-button");
        selectBtn.setOnAction(e -> {
            int s = selectedStage[0];
            String battleName = data.getStageName(s);
            gameManager.setCurrentPokemonId(data.getId());
            jfocus.io.UserData.saveCurrentPartner(data.getId());
            this.currentPokemonFolder = data.getFolderName();
            this.currentStage = s;
            selectedStageMap.put(data.getId(), s);
            jfocus.io.UserData.saveSelectedStage(data.getId(), s);
            updatePokemonDisplay(data.getFolderName(), s);
            timerView.refreshXpDisplay();
            this.timerView.updatePartnerDisplay(battleName, bigView.getImage());
            pokedexCardCache.remove(data.getId());
            refreshPokedexGrid();
            detailStage.close();
        });

        layout.getChildren().addAll(bigView, nameLabel, typeBox, stageSelector, descLabel, selectBtn);
        detailScene = new Scene(layout, 400, 580);
        switchTheme(css);
        detailStage.setScene(detailScene);
        detailStage.show();
    }

    private Tab createStatsTab() {
        this.statsView = new StatsView(this.gameManager, this.timerView, this);
        Tab tab = new Tab("數據分析", statsView);
        tab.setClosable(false);
        tab.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                statsView.refreshCurrentView();
            }
        });
        return tab;
    }

    private Tab createTodoTab() {
        this.todoView = new TodoView(this);
        Tab tab = new Tab("待辦事項", todoView);
        tab.setClosable(false);
        tab.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                todoView.refreshTodoList();
            }
        });
        return tab;
    }
    private Tab createSettingsTab() {
        SettingsView settingsView = new SettingsView(this);
        Tab tab = new Tab("設定", settingsView);
        tab.setClosable(false);
        tab.setOnSelectionChanged(e -> { if (tab.isSelected()) settingsView.refresh(); });
        return tab;
    }

    public void startBorderCountdown(double durationSeconds) {
        startBorderCountdown(durationSeconds, null);
    }

    public void startBorderCountdown(double durationSeconds, Runnable onComplete) {
        if (borderCanvas == null) return;
        if (currentBorderTimer != null) currentBorderTimer.stop();
        double yOff = resolveBorderParams()[0];
        Color strokeColor = resolveBorderStrokeColor();
        final double yOffset = yOff;
        final Color finalColor = strokeColor;
        long durationNanos = (long) (durationSeconds * 1_000_000_000L);
        long[] t0 = {-1L};
        currentBorderTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (t0[0] < 0) t0[0] = now;
                double p = Math.min((now - t0[0]) / (double) durationNanos, 1.0);
                drawBorderErase(p, yOffset, finalColor);
                if (p >= 1.0) {
                    stop();
                    if (onComplete != null) Platform.runLater(onComplete);
                }
            }
        };
        currentBorderTimer.start();
    }

    public void startBorderFlash(double durationSeconds, Runnable onComplete) {
        if (borderCanvas == null) return;
        if (currentBorderTimer != null) currentBorderTimer.stop();
        final Color flashColor = Color.web("#cd0000");
        long[] t0 = {-1L};
        currentBorderTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (t0[0] < 0) t0[0] = now;
                double elapsed = (now - t0[0]) / 1_000_000_000.0;
                if (elapsed >= durationSeconds) {
                    borderCanvas.getGraphicsContext2D().clearRect(0, 0, borderCanvas.getWidth(), borderCanvas.getHeight());
                    stop();
                    if (onComplete != null) Platform.runLater(onComplete);
                    return;
                }
                double alpha = 0.55 + 0.45 * Math.sin(elapsed * Math.PI * 2.5);
                alpha = Math.max(0.05, alpha);
                drawFullBorder(Color.color(flashColor.getRed(), flashColor.getGreen(), flashColor.getBlue(), alpha));
            }
        };
        currentBorderTimer.start();
    }

    private void drawFullBorder(Color strokeColor) {
        double W = borderCanvas.getWidth();
        double H = borderCanvas.getHeight();
        GraphicsContext gc = borderCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, W, H);
        if (W == 0 || H == 0) return;

        double pad  = 1.5;
        double r    = FullScreenAlert.isMacOS() ? 11 : 7;
        double topY = 3.5;

        gc.setStroke(strokeColor);
        gc.setLineWidth(3);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setLineJoin(StrokeLineJoin.ROUND);

        // 上角直角、下角圓角
        gc.beginPath();
        gc.moveTo(pad, topY);
        gc.lineTo(W - pad, topY);                                          // 頂邊
        gc.lineTo(W - pad, H - pad - r);                                   // 右側
        gc.arcTo(W - pad, H - pad, W - pad - r, H - pad, r);              // 右下圓角
        gc.lineTo(pad + r, H - pad);                                       // 底邊
        gc.arcTo(pad, H - pad, pad, H - pad - r, r);                      // 左下圓角
        gc.lineTo(pad, topY);                                              // 左側
        gc.stroke();
    }

    private double[] resolveBorderParams() {
        double yOff = 0;
        if (tabPane != null) {
            javafx.scene.Node header = tabPane.lookup(".tab-header-area");
            if (header != null) {
                yOff = header.localToScene(0, header.getBoundsInLocal().getHeight()).getY();
            }
        }
        return new double[]{yOff};
    }

    private Color resolveBorderStrokeColor() {
        Color strokeColor = Color.web("#5eabff");
        if (tabPane != null) {
            javafx.scene.Node headerBg = tabPane.lookup(".tab-header-background");
            if (headerBg instanceof javafx.scene.layout.Region) {
                javafx.scene.layout.Border border = ((javafx.scene.layout.Region) headerBg).getBorder();
                if (border != null) {
                    outer:
                    for (javafx.scene.layout.BorderStroke bs : border.getStrokes()) {
                        for (javafx.scene.paint.Paint p : new javafx.scene.paint.Paint[]{
                                bs.getBottomStroke(), bs.getTopStroke(),
                                bs.getLeftStroke(), bs.getRightStroke()}) {
                            if (p instanceof Color c) { strokeColor = c; break outer; }
                        }
                    }
                }
            }
        }
        return strokeColor;
    }

    private void drawBorderErase(double progress, double yOffset, Color strokeColor) {
        double W = borderCanvas.getWidth();
        double H = borderCanvas.getHeight();
        GraphicsContext gc = borderCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, W, H);
        if (progress >= 1.0 || W == 0 || H == 0) return;

        double pad = 1;
        double r = FullScreenAlert.isMacOS() ? 11 : 7;
        double x = pad, y = yOffset, w = W - pad * 2, h = H - y - pad;

        gc.setStroke(strokeColor);
        gc.setLineWidth(3);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setLineJoin(StrokeLineJoin.ROUND);

        double straightSide = h - r;
        double arcLen = Math.PI * r / 2.0;
        double totalPerArm = straightSide + arcLen + (w / 2.0 - r);
        double eaten = progress * totalPerArm;

        drawArmRemaining(gc, eaten, x, y, w, h, r, straightSide, arcLen, true);
        drawArmRemaining(gc, eaten, x, y, w, h, r, straightSide, arcLen, false);
    }

    private void drawArmRemaining(GraphicsContext gc, double eaten,
                                   double x, double y, double w, double h, double r,
                                   double straightSide, double arcLen, boolean leftArm) {
        double phase2End = straightSide + arcLen;
        double sideX = leftArm ? x : x + w;
        double arcCx = leftArm ? x + r : x + w - r;
        double arcCy = y + h - r;

        gc.beginPath();
        if (eaten < straightSide) {
            gc.moveTo(sideX, y + eaten);
            gc.lineTo(sideX, arcCy);
            if (leftArm) gc.arcTo(x, y + h, x + r, y + h, r);
            else         gc.arcTo(x + w, y + h, x + w - r, y + h, r);
            gc.lineTo(x + w / 2.0, y + h);
        } else if (eaten < phase2End) {
            // Partial arc: parametric Y-down compensated (angle π→3π/2 left, 0→-π/2 right)
            double frac = (eaten - straightSide) / arcLen;
            double startAngle = leftArm
                ? Math.PI + (Math.PI / 2.0) * frac
                : -(Math.PI / 2.0) * frac;
            gc.moveTo(arcCx + r * Math.cos(startAngle), arcCy - r * Math.sin(startAngle));
            for (int i = 1; i <= 6; i++) {
                double t = frac + (1.0 - frac) * i / 6.0;
                double a = leftArm ? Math.PI + (Math.PI / 2.0) * t : -(Math.PI / 2.0) * t;
                gc.lineTo(arcCx + r * Math.cos(a), arcCy - r * Math.sin(a));
            }
            gc.lineTo(x + w / 2.0, y + h);
        } else {
            double bEaten = eaten - phase2End;
            double startX = leftArm ? x + r + bEaten : x + w - r - bEaten;
            double endX = x + w / 2.0;
            if (leftArm ? startX >= endX : startX <= endX) return;
            gc.moveTo(startX, y + h);
            gc.lineTo(endX, y + h);
        }
        gc.stroke();
    }

}
