package jfocus.ui;

import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson; // 在最上方加入這行
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
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
import javafx.stage.Stage;

public class FocusApp extends Application {
    private GameManager gameManager = new GameManager();
    // --- 核心數據 (未來會與 JSON 對接) ---
    private String currentPokemonFolder = "004_charmander"; // 預設小火龍
    private int currentStage = 1;
    private int totalXP = 0;
    private Label coinLabel = new Label("💰 0");
    private Label stoneLabel = new Label("💎 0");

    // --- UI 元件 ---
    private Label timerLabel = new Label("25:00");
    private ImageView pokemonImageView = new ImageView();
    private Label statusLabel = new Label("準備好開始專注了嗎？");

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

    // 2. 建立一個內部類別來對應 JSON 資料格式 (POJO)
    // 1. 確保類別定義是這樣的 (在 FocusApp 類別內)
    public static class PokemonData {
        private String id;
        private String folderName;
        private String name;
        private List<String> types;
        private List<String> descriptions;

        public PokemonData(String id, String folderName, String name, List<String> types, List<String> descriptions) {
            this.id = id;
            this.folderName = folderName;
            this.name = name;
            this.types = types;
            this.descriptions = descriptions;
        }

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

    private void loadPokedexData() {
        pokedexList.clear();

        // --- 偵錯起點 ---
        System.out.println("=== 數據載入診斷中 ===");
        String currentDir = System.getProperty("user.dir");
        System.out.println("程式目前執行位置 (CWD): " + currentDir);

        File jsonFile = new File("res/pokemon_data.json");

        // C:\Users\rich0\OneDrive\Desktop\Course\1142
        // Java\JFocus\JFocus\res\pokemon_data.json
        System.out.println("試圖尋找檔案: " + jsonFile.getAbsolutePath());

        if (!jsonFile.exists()) {
            System.err.println("❌ 找不到 JSON 檔案！請確認 res 資料夾是否在正確的路徑下。");
            // 幫你列出目前路徑下有哪些東西，方便你對照
            File currentFolder = new File(".");
            System.out.println("目前資料夾下的檔案有: " + java.util.Arrays.toString(currentFolder.list()));
            return;
        }
        // --- 偵錯終點 ---

        try (FileReader reader = new FileReader(jsonFile, StandardCharsets.UTF_8)) {
            Gson gson = new Gson();
            JsonObject jsonRoot = gson.fromJson(reader, JsonObject.class);
            JsonArray array = jsonRoot.getAsJsonArray("pokedex");

            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();

                // --- 1. 安全抓取 ID, Name, Folder ---
                String id = obj.has("id") ? obj.get("id").getAsString() : "000";
                String name = obj.has("name") ? obj.get("name").getAsString() : "未知精靈";
                String folderName = obj.has("folderName") ? obj.get("folderName").getAsString() : "";

                // --- 2. 安全處理屬性 (就是這裡出事！) ---
                List<String> types = new ArrayList<>();
                JsonElement typeEl = obj.get("types"); // 檢查你的 JSON 裡是不是叫 "types"？

                if (typeEl != null && !typeEl.isJsonNull()) {
                    if (typeEl.isJsonArray()) {
                        for (JsonElement t : typeEl.getAsJsonArray())
                            types.add(t.getAsString());
                    } else {
                        types = java.util.Arrays.asList(typeEl.getAsString().split(" / "));
                    }
                } else {
                    types.add("一般"); // 如果找不到屬性，給個預設值
                }

                // --- 3. 安全處理描述 ---
                List<String> descriptions = new ArrayList<>();
                JsonElement stagesEl = obj.get("stages");
                if (stagesEl != null && stagesEl.isJsonArray()) {
                    for (JsonElement desc : stagesEl.getAsJsonArray()) {
                        descriptions.add(desc.getAsString());
                    }
                } else {
                    // 如果沒描述，補三個空的以免後面 index 出錯
                    descriptions.addAll(java.util.Arrays.asList("無描述", "無描述", "無描述"));
                }

                // --- 4. 加入清單 ---
                pokedexList.add(new PokemonData(id, folderName, name, types, descriptions));
            }
            System.out.println("✅ 數據載入成功！共 " + pokedexList.size() + " 隻。");

        } catch (Exception e) {
            System.err.println("❌ 解析 JSON 時發生錯誤:");
            e.printStackTrace();
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
        timerLayout.setStyle("-fx-background-color: #2c3e50; -fx-padding: 30;");

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
                timerLabel.setStyle("-fx-font-size: 80px; -fx-text-fill: #27ae60; -fx-font-weight: bold;");
                statusLabel.setText("正在與精靈一起努力工作中...");
            }
        });

        stopBtn.setOnAction(e -> {
            timerLabel.setStyle("-fx-font-size: 80px; -fx-text-fill: #c0392b; -fx-font-weight: bold;");
            statusLabel.setText("休息是為了走更長遠的路！");
        });
    }

    // start
    @Override
    public void start(Stage primaryStage) {
        loadPokedexData();

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

        // 右上角貨幣層
        VBox currencyHeader = new VBox(5, coinLabel, stoneLabel);
        currencyHeader.setAlignment(Pos.TOP_RIGHT);
        currencyHeader.setPadding(new javafx.geometry.Insets(15));
        currencyHeader.setPickOnBounds(false);

        StackPane rootStack = new StackPane(tabPane, currencyHeader);
        Scene scene = new Scene(rootStack, 480, 750);

        // CSS 載入
        File cssFile = new File("res/css/Pokemon.css");
        if (cssFile.exists()) {
            scene.getStylesheets().add(cssFile.toURI().toString());
        }

        // --- 重要：一定要呼叫這個，按鈕才會動 ---
        handleButtonEvents();
        setupButtonStyles();

        primaryStage.setTitle("JFocus - Pokemon Focus Sentinel");
        primaryStage.setScene(scene);
        primaryStage.show();

        updatePokemonDisplay(currentPokemonFolder, currentStage);
    }

    // 抽獎
    // 動畫
    private void playGachaAnimation(ImageView ballView, Label coinLabel) {
        // 1. 晃動動畫 (Shake)
        RotateTransition shake = new RotateTransition(javafx.util.Duration.millis(100), ballView);
        shake.setFromAngle(-15);
        shake.setToAngle(15);
        shake.setCycleCount(10);
        shake.setAutoReverse(true);

        shake.setOnFinished(event -> {
            // 2. 隨機決定中獎的寶可夢
            String resultId = gameManager.performPokeBallDraw(); // 假設這會回傳一個 ID

            // 3. 換圖並噴發效果 (這裡先簡單換成中獎圖)
            ballView.setImage(new Image("file:res/pokemon/" + resultId + "/stage1.png"));

            // 放大效果 (Pop up)
            ScaleTransition pop = new ScaleTransition(javafx.util.Duration.millis(300), ballView);
            pop.setFromX(0.5);
            pop.setFromY(0.5);
            pop.setToX(1.5);
            pop.setToY(1.5);
            pop.play();

            coinLabel.setText("我的專注幣: " + gameManager.getFocusCoins());
            statusLabel.setText("恭喜！收服了新夥伴！");
        });

        shake.play();
    }

    // 抽獎主邏輯
    private Tab createGachaTab() {
        VBox layout = new VBox(30);
        layout.setAlignment(Pos.CENTER);
        layout.setStyle("-fx-background-color: #34495e; -fx-padding: 40;");

        Label title = new Label("精靈補給站");
        title.setStyle("-fx-text-fill: #f1c40f; -fx-font-size: 32px; -fx-font-weight: bold;");

        // 顯示貨幣（之後要串接 GameManager）
        Label currencyLabel = new Label("我的專注幣: " + gameManager.getFocusCoins());
        currencyLabel.setStyle("-fx-text-fill: white; -fx-font-size: 18px;");

        // 抽獎展示區（這就是動畫發生的地方）
        StackPane gachaDisplay = new StackPane();
        ImageView ballView = new ImageView(new Image("file:res/pokemon/000_ball.png")); // 找一顆精靈球的圖
        ballView.setFitHeight(150);
        ballView.setPreserveRatio(true);

        gachaDisplay.getChildren().add(ballView);

        Button drawBtn = new Button("普通球抽獎 (200 幣)");
        drawBtn.getStyleClass().add("gacha-button"); // 記得去 CSS 加這個樣式

        drawBtn.setOnAction(e -> {
            if (gameManager.getFocusCoins() >= 200) {
                playGachaAnimation(ballView, currencyLabel);
            } else {
                statusLabel.setText("錢不夠啦！再去專注幾分鐘吧！");
            }
        });

        layout.getChildren().addAll(title, currencyLabel, gachaDisplay, drawBtn);
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
     * @param stage  進化階段 (1, 2, 或 3)
     */
    public void updatePokemonDisplay(String folder, int stage) {
        String path = "res/pokemon/" + folder + "/stage" + stage + ".png";
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
        FlowPane flowGrid = new FlowPane(10, 10);
        flowGrid.setStyle("-fx-background-color: #2c3e50; -fx-padding: 15;");
        flowGrid.setPrefWrapLength(450); // 這裡設定跟你的視窗寬度差不多
        flowGrid.setAlignment(Pos.TOP_LEFT);

        // 在 createPokedexTab() 方法裡的迴圈處
        for (PokemonData data : pokedexList) {
            for (int i = 1; i <= 3; i++) {
                // --- 這裡改為直接傳入 data 物件 ---
                VBox card = createPokemonCard(data, i);
                flowGrid.getChildren().add(card);
            }
        }

        scrollPane.setContent(flowGrid);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");
        return new Tab("精靈圖鑑", scrollPane);
    }

    /**
     * 建立單個寶可夢卡片
     */
    private VBox createPokemonCard(PokemonData data, int stage) {
        VBox card = new VBox(5);
        card.getStyleClass().add("pokemon-card"); // 確保 CSS 有這條
        card.setPrefSize(70, 90);
        card.setAlignment(Pos.CENTER);

        ImageView view = new ImageView();
        String path = "file:res/pokemon/" + data.getFolderName() + "/stage" + stage + ".png";

        try {
            Image img = new Image(path);
            view.setImage(img);
        } catch (Exception e) {
            // 如果找不到圖片，放一個預設占位圖或顯示錯誤
            System.err.println("找不到圖片: " + path);
        }

        view.setFitWidth(55);
        view.setPreserveRatio(true);

        // 如果沒解鎖，變黑色 (這裡假設 gameManager 有這個方法)
        if (!gameManager.isStageUnlocked(data.getId(), stage)) {
            ColorAdjust blackout = new ColorAdjust();
            blackout.setBrightness(-1.0);
            view.setEffect(blackout);
        }

        // 點擊事件：改用我們之前設計的 showDetailView (包含屬性和出戰)
        card.setOnMouseClicked(e -> showDetailView(
                data.getFolderName(),
                stage,
                data.getName(),
                data.getTypes(),
                data.getDescriptions().get(stage - 1)));

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
    public void showDetailView(String folder, int stage, String name, List<String> types, String description) {
        Stage detailStage = new Stage();
        VBox layout = new VBox(15);
        layout.setAlignment(Pos.CENTER);
        layout.setStyle(
                "-fx-background-color: #2c3e50; -fx-padding: 20; -fx-border-color: #f1c40f; -fx-border-width: 2;");

        ImageView bigView = new ImageView(new Image("file:res/pokemon/" + folder + "/stage" + stage + ".png"));
        bigView.setFitWidth(280);
        bigView.setPreserveRatio(true);

        Label nameLabel = new Label(name + " 階段 " + stage);
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
            this.currentPokemonFolder = folder;
            this.currentStage = stage;
            updatePokemonDisplay(folder, stage);
            detailStage.close();
        });

        layout.getChildren().addAll(bigView, nameLabel, typeBox, descText, selectBtn);
        detailStage.setScene(new Scene(layout));
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
        layout.setStyle("-fx-background-color: #2c3e50;");

        Button btnAdd = new Button("DEBUG: 增加資源");
        btnAdd.setOnAction(e -> {
            gameManager.addFocusTime(500); // 模擬加錢
            // --- 現在這裡就找得到變數了 ---
            coinLabel.setText("💰 " + gameManager.getFocusCoins());
            stoneLabel.setText("💎 " + gameManager.getMasterStones());
        });

        layout.getChildren().addAll(new Label("數據統計區"), new Separator(), btnAdd);
        return new Tab("數據分析", layout);
    }

    public static void main(String[] args) {
        launch(args);
    }
}