package jfocus.ui;

import java.util.function.UnaryOperator;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import jfocus.ai.distraction.DistractionHandlingMode;
import jfocus.db.DatabaseCore;
import jfocus.engine.FocusEngine;
import jfocus.engine.FocusListener;
import jfocus.settings.JdbcTimerSettingsRepository;
import jfocus.settings.TimerSettings;

public class TimerView extends VBox implements FocusListener {

    private final int STAGE_1 = 0;
    private final int STAGE_2 = 1;
    private final int STAGE_3 = 2;

    private final int STAGE_1_XP_REQUIREMENT = 50;
    private final int STAGE_2_XP_REQUIREMENT = 200;
    private final int STAGE_3_XP_REQUIREMENT = 500;

    private final int[] STAGE_XP_REQUIREMENT = {
        STAGE_1_XP_REQUIREMENT,
        STAGE_1_XP_REQUIREMENT + STAGE_2_XP_REQUIREMENT,
        STAGE_1_XP_REQUIREMENT + STAGE_2_XP_REQUIREMENT + STAGE_3_XP_REQUIREMENT};

    private GameManager gameManager;
    private FocusUI focusUI;
    private FocusEngine engine;
    private final JdbcTimerSettingsRepository timerSettingsRepository;
    private final jfocus.subjects.JdbcSubjectRepository subjectRepo;

    // --- 將原本 FocusUI 中的零件搬到這裡 ---
    private Label timerLabel;
    private Button startBtn;
    private Button stopBtn;
    private TextField workInput;
    private TextField breakInput;
    private HBox inputArea;
    private ChoiceBox<String> modeSelector;
    private ChoiceBox<String> subjectSelector;
    private VBox settingsSection;
    private double settingsSectionHeight = -1;
    private double inputAreaHeight = -1;
    private ImageView pokemonImageView;
    private ProgressBar xpBar;
    private Label xpInfoLabel;
    private Label statusLabel;

    private Button pauseBtn; // 新增這行
    private Button debugBtn10s;
    private Button debugBtn1m;
    private Button debugBtn5m;
    private boolean isPaused = false; // 記錄目前的暫停狀態
    private int lastTickSeconds = 0; // 碼表模式：記錄最後一次 tick 的秒數

    private enum status { WORKING, CHILLING, IDLEING };
    private status userStatus = status.IDLEING;
    private String currentPartnerName = "神秘夥伴";
    private int previousEvolutionStage = -1;
    private String previousPokemonId = null;

    public TimerView(GameManager gameManager, FocusUI focusUI) {

        this.gameManager = gameManager;
        this.focusUI = focusUI;

        this.timerSettingsRepository = new JdbcTimerSettingsRepository(new DatabaseCore());
        this.subjectRepo = new jfocus.subjects.JdbcSubjectRepository(new DatabaseCore());
        // 原本的 startBtn 和 stopBtn
        startBtn = new Button("開始冒險");
        startBtn.setStyle("-fx-background-color: #27ae60; -fx-font-size: 16px; -fx-text-fill: white; -fx-padding: 5 10; -fx-background-radius: 8;");

        stopBtn = new Button("放棄");
        stopBtn.setStyle("-fx-background-color: #c0392b; -fx-font-size: 16px; -fx-text-fill: white; -fx-padding: 5 10;  -fx-background-radius: 8;");
        stopBtn.setVisible(false);
        stopBtn.setManaged(false);

        pauseBtn = new Button("暫停");
        pauseBtn.setStyle("-fx-background-color: #f39c12; -fx-font-size: 16px; -fx-text-fill: white; -fx-padding: 5 10;  -fx-background-radius: 8;");
        pauseBtn.setVisible(false);
        pauseBtn.setManaged(false);

        //TODO Debug區域
        debugBtn10s = new Button("⟳10s");
        debugBtn10s.setStyle("-fx-background-color: #7f8c8d; -fx-font-size: 16px; -fx-text-fill: white; -fx-padding: 5 10; -fx-background-radius: 8;");
        debugBtn10s.setVisible(false);
        debugBtn10s.setManaged(false);
        debugBtn10s.setOnAction(e -> engine.debugForward(10));

        debugBtn1m = new Button("⟳1m");
        debugBtn1m.setStyle("-fx-background-color: #7f8c8d; -fx-font-size: 16px; -fx-text-fill: white; -fx-padding: 5 10; -fx-background-radius: 8;");
        debugBtn1m.setVisible(false);
        debugBtn1m.setManaged(false);
        debugBtn1m.setOnAction(e -> engine.debugForward(60));
        
        debugBtn5m = new Button("⟳5m");
        debugBtn5m.setStyle("-fx-background-color: #7f8c8d; -fx-font-size: 16px; -fx-text-fill: white; -fx-padding: 5 10; -fx-background-radius: 8;");
        debugBtn5m.setVisible(false);
        debugBtn5m.setManaged(false);
        debugBtn5m.setOnAction(e -> engine.debugForward(60* 5));

        HBox btnBox = new HBox(15, startBtn, pauseBtn, stopBtn);
        HBox debugBtnBox = new HBox(15, debugBtn10s, debugBtn1m, debugBtn5m);
        // 1. 實例化所有 UI 零件
        TimerSettings timerSettings = loadTimerSettingsSafely();

        timerLabel = new Label(String.format("%02d:00", timerSettings.workMinutes()));
        timerLabel.setStyle("-fx-font-size: 60px; -fx-font-weight: bold;");

        PauseTransition digitsOnlyErrorDelay = new PauseTransition(Duration.seconds(2));
        digitsOnlyErrorDelay.setOnFinished(event -> {
            statusLabel.getStyleClass().removeAll("error");
            statusLabel.setText("準備就緒");
        });
        UnaryOperator<TextFormatter.Change> digitsOnly = change -> {
            String text = change.getControlNewText();
            if (text.matches("\\d*")) {
                return change;
            }
            statusLabel.setText("必須輸入正整數!");
            if (!statusLabel.getStyleClass().contains("error")) {
                statusLabel.getStyleClass().add("error");
            }
            digitsOnlyErrorDelay.playFromStart();
            focusUI.startBorderFlash(2, null);
            return null;
        };

        workInput = new TextField(String.valueOf(timerSettings.workMinutes()));
        workInput.setPrefWidth(50);
        workInput.setAlignment(Pos.CENTER);
        workInput.setTextFormatter(new TextFormatter<>(digitsOnly));

        breakInput = new TextField(String.valueOf(timerSettings.breakMinutes()));
        breakInput.setPrefWidth(50);
        breakInput.setAlignment(Pos.CENTER);
        breakInput.setTextFormatter(new TextFormatter<>(digitsOnly));

        modeSelector = new ChoiceBox<>();
        modeSelector.getStyleClass().add("mode-selector");
        modeSelector.getItems().addAll("番茄鐘模式", "正向碼表");
        modeSelector.setValue("番茄鐘模式");

        modeSelector.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if ("正向碼表".equals(newVal)) {
                fadeOutInputArea();
                timerLabel.setText("00:00");
            } else {
                fadeInInputArea();
                updateTimerLabelFromWorkInput();
            }
        });
        workInput.setOnAction(e -> updateTimerLabelFromWorkInput());
        workInput.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                updateTimerLabelFromWorkInput();
            }
        });
        pokemonImageView = new ImageView();
        pokemonImageView.setFitHeight(200);
        pokemonImageView.setFitWidth(200);
        pokemonImageView.setPreserveRatio(true);

        String initPartnerId = this.gameManager.getCurrentPokemonId();
        if (initPartnerId != null && !initPartnerId.isEmpty()) {
            FocusUI.PokemonData initData = this.focusUI.getPokemonDataById(initPartnerId);
            if (initData != null) {
                int initStage = focusUI.getCurrentPokemonStage() > 0
                        ? focusUI.getCurrentPokemonStage()
                        : this.gameManager.getEvolutionStage(initPartnerId);
                java.io.File initFile = new java.io.File(
                        "res/pokemon/" + initData.getFolderName() + "/stage" + initStage + ".png");
                if (initFile.exists()) {
                    pokemonImageView.setImage(new javafx.scene.image.Image(initFile.toURI().toString()));
                    this.currentPartnerName = initData.getStageName(initStage);
                }
            }
        }

        xpBar = new ProgressBar(0);
        xpBar.setStyle("-fx-accent: #3498db;");

        xpInfoLabel = new Label("XP: 0 / 50 (等級 1)");
        xpInfoLabel.setText("XP: 0 / 50 (等級 1)");
        xpInfoLabel.setStyle("-fx-font-size: 14px;");

        VBox xpBox = new VBox(3, xpInfoLabel, xpBar);
        xpBox.setAlignment(Pos.CENTER);

        statusLabel = new Label("準備就緒");
        statusLabel.getStyleClass().add("statusLabel");

        // 2. 開始套用你設計的精美排版
        this.setAlignment(Pos.CENTER);
        this.setSpacing(10);
        this.getStyleClass().add("timer-layout"); // 建議未來把 padding 寫進 CSS

        Label workPre = new Label("專注");
        Label workPost = new Label("分");
        Label breakPre = new Label("休息");
        Label breakPost = new Label("分");
        Separator divider = new Separator(Orientation.VERTICAL);

        inputArea = new HBox(8, workPre, workInput, workPost, divider, breakPre, breakInput, breakPost);
        inputArea.setAlignment(Pos.CENTER);

        subjectSelector = new ChoiceBox<>();
        subjectSelector.getStyleClass().add("mode-selector");
        reloadSubjects(null);

        HBox selectorRow = new HBox(10, modeSelector, subjectSelector);
        selectorRow.setAlignment(Pos.CENTER);

        btnBox.setAlignment(Pos.CENTER);
        debugBtnBox.setAlignment(Pos.CENTER);

        settingsSection = new VBox(8, selectorRow, inputArea);
        settingsSection.setAlignment(Pos.CENTER);

        // 3. 把所有零件組裝起來 (就是你原本的寫法)
        this.getChildren().addAll(
                settingsSection,
                pokemonImageView,
                xpBox,
                statusLabel,
                timerLabel,
                btnBox,
                debugBtnBox
        );

        // 4. 掛載組員寫的引擎與按鈕事件
        this.engine = createFocusEngine();

        startBtn.setOnAction(e -> {
            try {
                if (!validateTimeInputs()) {
                    int newTime = Integer.parseInt(breakInput.getText()) * 2;
                    startBtn.setDisable(true);
                    workInput.setText(String.format("%d", newTime));
                    timerLabel.setText(String.format("%02d:00", newTime));
                    statusLabel.setText("時間設定錯誤: 專注時間至少是休息時間的兩倍\n已將專注時間設定為 " + newTime + " 分鐘");
                    focusUI.startBorderFlash(3, () -> {
                        startBtn.setDisable(false);
                        statusLabel.setText("準備就緒");
                    });
                    return;
                }

                if (SUBJECT_PLACEHOLDER.equals(subjectSelector.getValue())) {
                    statusLabel.getStyleClass().add("error");
                    statusLabel.setText("請先選擇科目！");
                    PauseTransition errDelay = new PauseTransition(Duration.seconds(2));
                    errDelay.setOnFinished(ev -> {
                        statusLabel.getStyleClass().remove("error");
                        statusLabel.setText("準備就緒");
                    });
                    errDelay.play();
                    focusUI.startBorderFlash(2, null);
                    return;
                }

                String selectedMode = modeSelector.getValue();
                int minutes = 0;

                // 提前檢查番茄鐘模式的輸入 (防呆：擋下 0 或負數)
                if (!"正向碼表".equals(selectedMode)) {
                    minutes = parsePositiveMinutes(workInput.getText());
                    int breakMinutes = parsePositiveMinutes(breakInput.getText());
                    saveTimerSettingsSafely(minutes, breakMinutes);
                }

                // 切換 UI 狀態
                startBtn.setVisible(false);
                startBtn.setManaged(false);
                pauseBtn.setVisible(true);
                pauseBtn.setManaged(true);
                stopBtn.setVisible(true);
                stopBtn.setManaged(true);

                //TODO Debug區域
                debugBtn10s.setVisible(true);
                debugBtn10s.setManaged(true);
                debugBtn1m.setVisible(true);
                debugBtn1m.setManaged(true);
                debugBtn5m.setVisible(true);
                debugBtn5m.setManaged(true);

                isPaused = false;
                pauseBtn.setText("暫停");
                lastTickSeconds = 0;

                fadeOutSection();
                VBox.setMargin(timerLabel, new Insets(0, 0, 15, 0));
                ScaleTransition scaleUp = new ScaleTransition(Duration.millis(350), timerLabel);
                scaleUp.setToX(1.35);
                scaleUp.setToY(1.35);
                scaleUp.setInterpolator(Interpolator.EASE_OUT);
                scaleUp.play();


                engine.setCurrentSubject(subjectSelector.getValue());

                // 根據模式啟動不同的引擎邏輯
                if ("正向碼表".equals(selectedMode)) {
                    stopBtn.setText("結束");
                    statusLabel.setText("正在與 " + currentPartnerName + " 一起冒險 (碼表模式) ...");
                    engine.startStopwatch(); // 呼叫組員的碼表引擎
                } else {
                    statusLabel.setText("正在與 " + currentPartnerName + " 一起冒險中...");
                    engine.start(minutes * 60); // 呼叫番茄鐘引擎倒數
                }
                userStatus = status.WORKING;
                focusUI.sessionActiveProperty().set(true);
            } catch (NumberFormatException ex) {
                statusLabel.setText("請輸入有效的數字！");
                resetUI();
            }
        });

        // 在 TimerView.java 裡面：
        pauseBtn.setOnAction(e -> {
            if (!isPaused) {
                engine.pause(); // 呼叫引擎的暫停
            } else {
                engine.resume(); // 呼叫引擎的繼續
            }
        });


        stopBtn.setOnAction(e -> {
            String selectedMode = modeSelector.getValue();
            if ("正向碼表".equals(selectedMode)) {
                int elapsedMinutes = lastTickSeconds / 60;
                engine.stop();
                engine = createFocusEngine();
                String currentId = gameManager.getCurrentPokemonId();
                gameManager.addFocusTime(elapsedMinutes, currentId);
                int showTime;
                if (elapsedMinutes > 0) {
                    showTime = 5;
                    focusUI.startBorderCountdown(showTime, () -> startBtn.setDisable(false));
                    statusLabel.setText("冒險結束！專注了 " + elapsedMinutes + " 分鐘，獲得 " + elapsedMinutes + " 枚專注幣！");
                } else {
                    showTime = 3;
                    focusUI.startBorderCountdown(showTime, () -> startBtn.setDisable(false));
                    statusLabel.setText("冒險結束！本次太短暫，未獲得專注幣。");
                }
                focusUI.refreshOnEnded();
                resetUI(showTime);
                startBtn.setDisable(true);
                userStatus = status.IDLEING;
                focusUI.sessionActiveProperty().set(false);
            } else {
                new MiniDialog.Builder(stopBtn.getScene().getWindow())
                        .type(MiniDialog.Type.DANGER)
                        .title("放棄冒險")
                        .message("現在放棄的話，將無法獲得任何專注幣與經驗值喔！")
                        .primaryBtn("繼續冒險", null)
                        .dangerBtn("確定放棄", () -> {
                            engine.shutdown();
                            engine = createFocusEngine();
                            statusLabel.setText("冒險已取消");
                            updateTimerLabelFromWorkInput();
                            startBtn.setDisable(true);
                            focusUI.startBorderCountdown(3, () -> startBtn.setDisable(false));
                            resetUI(3);
                            focusUI.sessionActiveProperty().set(false);
                        })
                        .show();
            }
        });
    }

    private FocusEngine createFocusEngine() {
        FocusEngine newEngine = new FocusEngine(this);
        newEngine.setDistractionUserNotifier(session -> Platform.runLater(() -> DistractedAlert.showIfNotShowing(newEngine, session)));
        return newEngine;
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

    private TimerSettings loadTimerSettingsSafely() {
        try {
            return timerSettingsRepository.loadSettings();
        } catch (RuntimeException ex) {
            System.err.println("讀取計時器設定失敗，使用預設值: " + ex.getMessage());
            return TimerSettings.defaults();
        }
    }

    private void saveTimerSettingsSafely(int workMinutes, int breakMinutes) {
        try {
            timerSettingsRepository.saveSettings(new TimerSettings(workMinutes, breakMinutes));
        } catch (RuntimeException ex) {
            statusLabel.setText("計時器設定儲存失敗，但本次冒險仍會開始。");
            System.err.println("儲存計時器設定失敗: " + ex.getMessage());
        }
    }

    private int parsePositiveMinutes(String text) {
        try {
            int value = Integer.parseInt(text.trim());
            return value > 0 ? value : -1;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private void updateTimerLabelFromWorkInput() {
        if ("正向碼表".equals(modeSelector.getValue()) || startBtn.isDisable()) {
            return;
        }

        int minutes = parsePositiveMinutes(workInput.getText());
        if (minutes > 0) {
            timerLabel.setText(String.format("%02d:00", minutes));
        }
    }

    private void resetUI() {
        resetUI(3.5, false);
    }

    private void resetUI(double setTime) {
        resetUI(setTime, false);
    }

    private void resetUI(boolean isBreakTime) {
        resetUI(5, isBreakTime);
    }

    private void resetUI(double setTime, boolean isBreakTime) {
        int min = parsePositiveMinutes(isBreakTime ? breakInput.getText() : workInput.getText());
        if (min <= 0) {
            min = isBreakTime ? TimerSettings.DEFAULT_BREAK_MINUTES : TimerSettings.DEFAULT_WORK_MINUTES;
            workInput.setText(String.valueOf(min));
        }
        int finalMin = min;
        if (isBreakTime) {
            startBtn.setVisible(false);
            startBtn.setManaged(false);
            pauseBtn.setVisible(false);
            pauseBtn.setManaged(true);
            stopBtn.setVisible(false);
            stopBtn.setManaged(true);

            //TODO Debug區域
            debugBtn10s.setVisible(true);
            debugBtn10s.setManaged(true);
            debugBtn1m.setVisible(true);
            debugBtn1m.setManaged(true);
            debugBtn5m.setVisible(true);
            debugBtn5m.setManaged(true);
        } else {
            startBtn.setVisible(true);
            startBtn.setManaged(true);
            pauseBtn.setVisible(false);
            pauseBtn.setManaged(false);
            pauseBtn.setText("暫停");
            stopBtn.setVisible(false);
            stopBtn.setManaged(false);
            isPaused = false;

            fadeInSection();
            VBox.setMargin(timerLabel, new Insets(0, 0, 0, 0));
            ScaleTransition scaleDown = new ScaleTransition(Duration.millis(350), timerLabel);
            scaleDown.setToX(1.0);
            scaleDown.setToY(1.0);
            scaleDown.setInterpolator(Interpolator.EASE_OUT);
            scaleDown.play();

            //TODO Debug區域
            debugBtn10s.setVisible(false);
            debugBtn10s.setManaged(false);
            debugBtn1m.setVisible(false);
            debugBtn1m.setManaged(false);
            debugBtn5m.setVisible(false);
            debugBtn5m.setManaged(false);
        }

        PauseTransition delay = new PauseTransition(Duration.seconds(setTime));
        delay.setOnFinished(event -> {
            if (isBreakTime) {
                statusLabel.setText("休息一下吧");
                timerLabel.setText(String.format("%02d:00", finalMin));
            } else {
                if ("正向碼表".equals(modeSelector.getValue())) {
                    timerLabel.setText(String.format("%02d:00", 0));  
                }
                statusLabel.setText("準備就緒");
            }
        });
        delay.play();
    }
    
    // ==========================================
    // 實作 FocusListener (接收引擎每秒的回傳)
    // ==========================================
    @Override
    public void onTick(int secondsRemaining) {
        Platform.runLater(() -> {
            lastTickSeconds = secondsRemaining;
            int m = secondsRemaining / 60;
            int s = secondsRemaining % 60;
            timerLabel.setText(String.format("%02d:%02d", m, s));
        });
    }

    @Override
    public void onFinished() {
        Platform.runLater(() -> {
            focusUI.bringToFront();
            timerLabel.setText(String.format("%02d:%02d", 0, 0));

            if (userStatus == status.WORKING) {
                int focusedMinutes = parsePositiveMinutes(workInput.getText());
                if (focusedMinutes <= 0) focusedMinutes = 25;
                statusLabel.setText("冒險結束！獲得 " + focusedMinutes + " 枚專注幣！");

                // 呼叫 GameManager 結算
                String currentId = gameManager.getCurrentPokemonId();
                gameManager.addFocusTime(focusedMinutes, currentId);
                focusUI.refreshOnEnded();

                resetUI(true);
                userStatus = status.CHILLING;
                int rawBreak = parsePositiveMinutes(breakInput.getText());
                final int breakMinutes = rawBreak > 0 ? rawBreak : 5;
                focusUI.startBorderCountdown(5, () -> engine.startBreak(breakMinutes * 60));
                // 這裡未來可以加一段更新經驗值條 (xpBar) 的邏輯
            } else {
                statusLabel.setText("休息時間已結束!準備繼續工作啦!");
                updateTimerLabelFromWorkInput();
                startBtn.setDisable(true);
                focusUI.startBorderCountdown(3, () -> startBtn.setDisable(false));
                resetUI(3);
                userStatus = status.IDLEING;
                focusUI.sessionActiveProperty().set(false);
            }
        });
    }

    @Override
    public void onPaused() {
        Platform.runLater(() -> {
            isPaused = true;
            pauseBtn.setText("繼續冒險");
            statusLabel.setText("計時已暫停，等你回來！");
        });
    }

    @Override
    public void onResumed() {
        Platform.runLater(() -> {
            isPaused = false;
            pauseBtn.setText("暫停");
            statusLabel.setText("冒險中，請保持專心！");
        });
    }

    @Override
    public void onIdleDetected(long idleTimeMillis) {
        Platform.runLater(() -> IdleAlert.showIfNotShowing(engine, idleTimeMillis));
    }

    public void refreshXpDisplay() {
        String id = gameManager.getCurrentPokemonId();
        int xp = gameManager.getPokemonXp(id);
        double progress;
        int nextGoal;
        int stage;
        int prevStageGoal;
        if (xp < STAGE_XP_REQUIREMENT[STAGE_1]) {
            stage = 1;
            nextGoal = STAGE_1_XP_REQUIREMENT;
            prevStageGoal = 0;
            progress = (double) xp / STAGE_XP_REQUIREMENT[STAGE_1];
        } else if (xp < STAGE_XP_REQUIREMENT[STAGE_2]) {
            stage = 2;
            nextGoal = STAGE_2_XP_REQUIREMENT;
            prevStageGoal= STAGE_XP_REQUIREMENT[STAGE_1];
            progress = (double)(xp - prevStageGoal) / STAGE_2_XP_REQUIREMENT;
        } else if (xp < STAGE_XP_REQUIREMENT[STAGE_3]) {
            stage = 3;
            nextGoal = STAGE_3_XP_REQUIREMENT;
            prevStageGoal = STAGE_XP_REQUIREMENT[STAGE_2];
            progress = (double)(xp - prevStageGoal) / STAGE_3_XP_REQUIREMENT;
        }else{
            stage = 4;
            nextGoal = STAGE_3_XP_REQUIREMENT;
            progress = 1.0;
            prevStageGoal = STAGE_XP_REQUIREMENT[STAGE_3];
        }
        final double progressFinal = progress;
        final String text = (stage == 4)
            ? "等級 MAX "
            : "XP: " + (xp - prevStageGoal) + " / " + nextGoal + " (等級 " + stage + ")";
        String folder = focusUI.getCurrentPokemonFolder();
        int evolutionStage = gameManager.getEvolutionStage(id);
        int displayStage = focusUI.getCurrentPokemonStage();
        java.io.File imgFile = new java.io.File("res/pokemon/" + folder + "/stage" + displayStage + ".png");
        boolean partnerChanged = !id.equals(previousPokemonId);
        boolean evolved = !partnerChanged && previousEvolutionStage > 0 && evolutionStage > previousEvolutionStage;
        previousEvolutionStage = evolutionStage;
        previousPokemonId = id;
        final String pokemonId = id;
        final int newEvolutionStage = evolutionStage;
        Platform.runLater(() -> {
            xpInfoLabel.setText(text);
            if (!imgFile.exists()) return;
            javafx.scene.image.Image newImage = new javafx.scene.image.Image(imgFile.toURI().toString());
            PauseTransition delay = new PauseTransition(Duration.seconds(1));
            delay.setOnFinished(e -> {
                if (evolved) {
                    playXpFillThenReset(progressFinal, newImage, pokemonId, newEvolutionStage);
                } else {
                    javafx.animation.Timeline tl = new javafx.animation.Timeline(
                        new javafx.animation.KeyFrame(Duration.millis(600),
                            new javafx.animation.KeyValue(
                                xpBar.progressProperty(), progressFinal,
                                javafx.animation.Interpolator.EASE_BOTH))
                    );
                    tl.play();
                    pokemonImageView.setImage(newImage);
                }
            });
            delay.play();
        });
    }

    private void playXpFillThenReset(double targetProgress, javafx.scene.image.Image newImage,
                                      String pokemonId, int newStage) {
        javafx.animation.Timeline fillToFull = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(Duration.millis(400),
                new javafx.animation.KeyValue(xpBar.progressProperty(), 1.0, Interpolator.EASE_IN))
        );
        fillToFull.setOnFinished(e -> {
            xpBar.setProgress(0);
            PauseTransition pause = new PauseTransition(Duration.millis(80));
            pause.setOnFinished(e2 -> {
                javafx.animation.Timeline fillToNew = new javafx.animation.Timeline(
                    new javafx.animation.KeyFrame(Duration.millis(600),
                        new javafx.animation.KeyValue(xpBar.progressProperty(), targetProgress, Interpolator.EASE_BOTH))
                );
                fillToNew.setOnFinished(e3 -> {
                    PauseTransition pause2 = new PauseTransition(Duration.millis(500));
                    pause2.setOnFinished(e4 -> {
                        pokemonImageView.setImage(newImage);
                        focusUI.showEvolutionUnlockDialog(pokemonId, newStage);
                    });
                    pause2.play();
                });
                fillToNew.play();
            });
            pause.play();
        });
        fillToFull.play();
    }

    private void fadeOutInputArea() {
        double h = inputArea.getHeight();
        if (h <= 0) h = inputArea.prefHeight(-1);
        inputAreaHeight = h;

        inputArea.setMinHeight(0);
        inputArea.setMaxHeight(h);
        inputArea.setPrefHeight(h);

        FadeTransition ft = new FadeTransition(Duration.millis(200), inputArea);
        ft.setFromValue(1.0);
        ft.setToValue(0.0);

        double fh = h;
        Timeline heightAnim = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(inputArea.prefHeightProperty(), fh)),
                new KeyFrame(Duration.millis(220),
                        new KeyValue(inputArea.prefHeightProperty(), 0, Interpolator.EASE_BOTH)));

        ParallelTransition pt = new ParallelTransition(ft, heightAnim);
        pt.setOnFinished(e -> {
            inputArea.setVisible(false);
            inputArea.setManaged(false);
            inputArea.setPrefHeight(-1);
            inputArea.setMinHeight(-1);
            inputArea.setMaxHeight(Double.MAX_VALUE);
        });
        pt.play();
    }

    private void fadeInInputArea() {
        double targetH = inputAreaHeight > 0 ? inputAreaHeight : inputArea.prefHeight(-1);

        inputArea.setMinHeight(0);
        inputArea.setMaxHeight(targetH);
        inputArea.setPrefHeight(0);
        inputArea.setOpacity(0.0);
        inputArea.setVisible(true);
        inputArea.setManaged(true);

        FadeTransition ft = new FadeTransition(Duration.millis(200), inputArea);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);

        Timeline heightAnim = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(inputArea.prefHeightProperty(), 0)),
                new KeyFrame(Duration.millis(220),
                        new KeyValue(inputArea.prefHeightProperty(), targetH, Interpolator.EASE_BOTH)));

        ParallelTransition pt = new ParallelTransition(ft, heightAnim);
        pt.setOnFinished(e -> {
            inputArea.setPrefHeight(-1);
            inputArea.setMinHeight(-1);
            inputArea.setMaxHeight(Double.MAX_VALUE);
        });
        pt.play();
    }

    private void fadeOutSection() {
        double h = settingsSection.getHeight();
        if (h <= 0) h = settingsSection.prefHeight(-1);
        settingsSectionHeight = h;

        settingsSection.setMinHeight(0);
        settingsSection.setMaxHeight(h);
        settingsSection.setPrefHeight(h);

        FadeTransition ft = new FadeTransition(Duration.millis(250), settingsSection);
        ft.setFromValue(1.0);
        ft.setToValue(0.0);

        Timeline heightAnim = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(settingsSection.prefHeightProperty(), h)),
                new KeyFrame(Duration.millis(280),
                        new KeyValue(settingsSection.prefHeightProperty(), 0, Interpolator.EASE_BOTH)));

        ParallelTransition pt = new ParallelTransition(ft, heightAnim);
        pt.setOnFinished(e -> {
            settingsSection.setVisible(false);
            settingsSection.setManaged(false);
            settingsSection.setPrefHeight(-1);
            settingsSection.setMinHeight(-1);
            settingsSection.setMaxHeight(Double.MAX_VALUE);
        });
        pt.play();
    }

    private void fadeInSection() {
        double targetH = settingsSectionHeight > 0 ? settingsSectionHeight
                : settingsSection.prefHeight(-1);

        settingsSection.setMinHeight(0);
        settingsSection.setMaxHeight(targetH);
        settingsSection.setPrefHeight(0);
        settingsSection.setOpacity(0.0);
        settingsSection.setVisible(true);
        settingsSection.setManaged(true);

        FadeTransition ft = new FadeTransition(Duration.millis(250), settingsSection);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);

        Timeline heightAnim = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(settingsSection.prefHeightProperty(), 0)),
                new KeyFrame(Duration.millis(280),
                        new KeyValue(settingsSection.prefHeightProperty(), targetH, Interpolator.EASE_BOTH)));

        ParallelTransition pt = new ParallelTransition(ft, heightAnim);
        pt.setOnFinished(e -> {
            settingsSection.setPrefHeight(-1);
            settingsSection.setMinHeight(-1);
            settingsSection.setMaxHeight(Double.MAX_VALUE);
        });
        pt.play();
    }

    private static final String SUBJECT_PLACEHOLDER = "選擇科目";

    private void reloadSubjects(String selectValue) {
        String current = selectValue != null ? selectValue
                : (subjectSelector.getValue() != null ? subjectSelector.getValue() : SUBJECT_PLACEHOLDER);
        java.util.List<String> items = new java.util.ArrayList<>();
        items.add(SUBJECT_PLACEHOLDER);
        items.addAll(subjectRepo.findAll());
        subjectSelector.getItems().setAll(items);
        subjectSelector.setValue(items.contains(current) ? current : SUBJECT_PLACEHOLDER);
    }

}
