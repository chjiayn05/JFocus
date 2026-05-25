package jfocus.ui;

import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.Cursor;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.animation.TranslateTransition;
import javafx.util.Duration;

import jfocus.ai.distraction.DistractionHandlingMode;
import jfocus.ai.distraction.JdbcDistractionModeRepository;
import jfocus.dashboard.DashboardDataManager;
import jfocus.dashboard.JdbcDashboardDataManager;
import jfocus.dashboard.MockDashboardDataManager;
import jfocus.dashboard.model.*;
import jfocus.db.DatabaseCore;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;

public class StatsView extends VBox {

    private final GameManager gameManager;
    private final TimerView timerView;
    private final FocusUI mainApp;
    private final DashboardDataManager dataManager;

    // 狀態變數
    private LocalDate selectedDailyDate = LocalDate.now();
    private YearMonth currentMonth = YearMonth.now();
    private LocalDate currentWeeklyDate = LocalDate.now();
    private YearMonth currentMonthlyMonth = YearMonth.now();
    private String currentViewType = "Daily";

    // 固定的學科配色調色盤
    private static final String[] PALETTE = {
            "#3498db", // 藍色
            "#e74c3c", // 紅色
            "#2ecc71", // 綠色
            "#f1c40f", // 黃色
            "#9b59b6", // 紫色
            "#e67e22", // 橘色
            "#1abc9c", // 青色
            "#a86df2" // 亮紫
    };

    private final Map<String, String> subjectColorMap = new HashMap<>();
    private int colorIndex = 0;

    // UI 內容容器
    private final VBox scrollContent;

    public StatsView(GameManager gameManager, TimerView timerView, FocusUI mainApp) {
        this.gameManager = gameManager;
        this.timerView = timerView;
        this.mainApp = mainApp;
        this.dataManager = new MockDashboardDataManager();
        // this.dataManager = new JdbcDashboardDataManager();

        this.setSpacing(10);
        this.setPadding(new Insets(10));
        this.setAlignment(Pos.TOP_CENTER);

        // 1. 頂部按鈕 (每日、每週、每月)
        HBox navBar = new HBox(12);
        navBar.setAlignment(Pos.CENTER);
        navBar.setPadding(new Insets(10, 0, 10, 0));

        Button btnDaily = new Button("每日分析");
        Button btnWeekly = new Button("每週趨勢");
        Button btnMonthly = new Button("每月統計");

        btnDaily.setPrefWidth(110);
        btnWeekly.setPrefWidth(110);
        btnMonthly.setPrefWidth(110);

        navBar.getChildren().addAll(btnDaily, btnWeekly, btnMonthly);

        // 2. 主滾動視圖
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(false);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        scrollContent = new VBox(20);
        scrollContent.setPadding(new Insets(10, 5, 10, 5));
        scrollContent.setAlignment(Pos.TOP_CENTER);
        scrollPane.setContent(scrollContent);

        // 3. 底部調試折疊區
        TitledPane debugPane = createDebugCollapsibleSection();

        this.getChildren().addAll(navBar, scrollPane, debugPane);

        // 4. 事件綁定
        btnDaily.setOnAction(e -> {
            updateNavButtons(btnDaily, btnWeekly, btnMonthly);
            showDailyView();
        });
        btnWeekly.setOnAction(e -> {
            updateNavButtons(btnWeekly, btnDaily, btnMonthly);
            showWeeklyView();
        });
        btnMonthly.setOnAction(e -> {
            updateNavButtons(btnMonthly, btnDaily, btnWeekly);
            showMonthlyView();
        });

        // 預設選中每日分析
        updateNavButtons(btnDaily, btnWeekly, btnMonthly);
        showDailyView();
    }

    private void updateNavButtons(Button activeBtn, Button btn1, Button btn2) {
        // activeBtn.setStyle("-fx-background-color: #FFCB05; -fx-text-fill: #1a1a1a; -fx-background-radius: 20; -fx-font-weight: bold;");
        // btn1.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #888888; -fx-background-radius: 20; -fx-font-weight: bold;");
        // btn2.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #888888; -fx-background-radius: 20; -fx-font-weight: bold;");
        activeBtn.setStyle("-fx-opacity: 1;");
        btn1.setStyle("-fx-opacity: 0.5;");
        btn2.setStyle("-fx-opacity: 0.5;");
    }

    private void clearSubjectColors() {
        subjectColorMap.clear();
        colorIndex = 0;
    }

    public void refreshCurrentView() {
        clearSubjectColors();
        if ("Daily".equals(currentViewType)) {
            showDailyView();
        } else if ("Weekly".equals(currentViewType)) {
            showWeeklyView();
        } else if ("Monthly".equals(currentViewType)) {
            showMonthlyView();
        }
    }

    private String[] getCurrentPalette() {
        String activeTheme = "";
        Scene scene = getScene();
        if (scene != null) {
            for (String sheet : scene.getStylesheets()) {
                if (sheet.contains("PokemonDark")) activeTheme = "Dark";
                else if (sheet.contains("PokemonLight")) activeTheme = "Light";
                else if (sheet.contains("PokemonPurple")) activeTheme = "Purple";
                else if (sheet.contains("PokemonRed")) activeTheme = "Red";
            }
        }
        
        switch (activeTheme) {
            case "Dark":
                return new String[]{"#333333", "#4d4d4d", "#666666", "#808080"};
            case "Light":
                return new String[]{"#808080", "#aaaaaa", "#dcdcdc", "#f0f0f0"};
            case "Purple":
                return new String[]{"#4b2e6e", "#6d3ea3", "#8c52d9", "#a86df2"};
            case "Red":
            default:
                return new String[]{"#6e2e2e", "#a33e3e", "#d95252", "#f26d6d"};
        }
    }

    private String getSubjectColor(String subject) {
        if (subjectColorMap.containsKey(subject)) {
            return subjectColorMap.get(subject);
        }
        String[] palette = getCurrentPalette();
        String color = palette[colorIndex % palette.length];
        subjectColorMap.put(subject, color);
        colorIndex++;
        return color;
    }

    private String hexToRgba(String hex, double opacity) {
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() == 6) {
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            return String.format("rgba(%d, %d, %d, %.2f)", r, g, b, opacity);
        }
        return "rgba(52, 152, 219, " + opacity + ")";
    }

    // 每日分析視圖
    private void showDailyView() {
        currentViewType = "Daily";
        scrollContent.getChildren().clear();
        clearSubjectColors();

        // 1. 月曆導航列
        HBox calHeader = new HBox(20);
        calHeader.setAlignment(Pos.CENTER);
        Button btnPrevMonth = new Button("◀");
        btnPrevMonth.getStyleClass().add("circular-btn");
        Button btnNextMonth = new Button("▶");
        btnNextMonth.getStyleClass().add("circular-btn");
        Label lblMonth = new Label(currentMonth.getYear() + "年" + currentMonth.getMonthValue() + "月");
        lblMonth.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        calHeader.getChildren().addAll(btnPrevMonth, lblMonth, btnNextMonth);

        // 2. 月曆網格
        GridPane gridCalendar = new GridPane();
        gridCalendar.setHgap(0);
        gridCalendar.setVgap(0);
        gridCalendar.setSnapToPixel(false);
        gridCalendar.setAlignment(Pos.CENTER);
        // gridCalendar.setStyle("-fx-background-color: rgba(0,0,0,0.1); -fx-padding: 5; -fx-background-radius: 8;");

        refreshCalendarGrid(gridCalendar, lblMonth);

        btnPrevMonth.setOnAction(e -> {
            currentMonth = currentMonth.minusMonths(1);
            selectedDailyDate = currentMonth.atDay(1);
            refreshCalendarGrid(gridCalendar, lblMonth);
            refreshDailyDetails();
        });

        btnNextMonth.setOnAction(e -> {
            currentMonth = currentMonth.plusMonths(1);
            selectedDailyDate = currentMonth.atDay(1);
            refreshCalendarGrid(gridCalendar, lblMonth);
            refreshDailyDetails();
        });

        // 3. 詳細數據容器
        VBox dailyDetailsContainer = new VBox(20);
        dailyDetailsContainer.setAlignment(Pos.TOP_CENTER);

        scrollContent.getChildren().addAll(calHeader, gridCalendar, dailyDetailsContainer);

        // 載入詳細資訊
        loadDailyDetailsInto(dailyDetailsContainer);
    }

    private void refreshCalendarGrid(GridPane grid, Label lblMonth) {
        grid.getChildren().clear();
        grid.getColumnConstraints().clear();
        for (int col = 0; col < 7; col++) {
            grid.getColumnConstraints().add(new ColumnConstraints(48));
        }
        lblMonth.setText(currentMonth.getYear() + "年" + currentMonth.getMonthValue() + "月");

        // 星期標頭 (週一為 column 0, 週日為 column 6)
        String[] headers = {"一", "二", "三", "四", "五", "六", "日"};
        for (int col = 0; col < 7; col++) {
            Label label = new Label(headers[col]);
            label.setStyle("-fx-font-weight: bold; -fx-text-fill: #888888; -fx-font-size: 11px; -fx-padding: 5 0 5 0;");
            grid.add(label, col, 0);
            GridPane.setHalignment(label, HPos.CENTER);
        }

        LocalDate firstDay = currentMonth.atDay(1);
        int dayOfWeekVal = firstDay.getDayOfWeek().getValue();
        int startColumn = dayOfWeekVal - 1;

        // 計算網格的起點日期 (可能屬於上個月)
        LocalDate startDate = firstDay.minusDays(startColumn);

        LocalDate lastDay = currentMonth.atEndOfMonth();
        int dayOfWeekOfLast = lastDay.getDayOfWeek().getValue(); // 1 to 7

        // 計算月曆所需格數
        int totalDaysNeeded = startColumn + lastDay.getDayOfMonth();
        int paddingDays = (7 - dayOfWeekOfLast) % 7;
        int totalCells = totalDaysNeeded + paddingDays;

        // 預快取前月、當月、後月的數據
        YearMonth prevMonth = currentMonth.minusMonths(1);
        YearMonth nextMonth = currentMonth.plusMonths(1);
        Map<LocalDate, Long> currentMonthData = dataManager.getMonthlyCalendarData(currentMonth);
        Map<LocalDate, Long> prevMonthData = dataManager.getMonthlyCalendarData(prevMonth);
        Map<LocalDate, Long> nextMonthData = dataManager.getMonthlyCalendarData(nextMonth);

        Button activeCellButton = null;

        for (int i = 0; i < totalCells; i++) {
            LocalDate date = startDate.plusDays(i);
            int row = 1 + i / 7;
            int col = i % 7;

            // 取得該日時間
            long seconds = 0;
            YearMonth cellMonth = YearMonth.from(date);
            if (cellMonth.equals(currentMonth)) {
                seconds = currentMonthData.getOrDefault(date, 0L);
            } else if (cellMonth.equals(prevMonth)) {
                seconds = prevMonthData.getOrDefault(date, 0L);
            } else if (cellMonth.equals(nextMonth)) {
                seconds = nextMonthData.getOrDefault(date, 0L);
            }

            Button cell = new Button();

            // 強制設定固定大小，避免格子高低凸出
            cell.setMinSize(48, 48);
            cell.setMaxSize(48, 48);
            cell.setPrefSize(48, 48);
            cell.setPadding(Insets.EMPTY);
            cell.setAlignment(Pos.CENTER);
            cell.getStyleClass().add("heatmap-cell");

            // 熱度背景顏色分級
            String colorClass = "heatmap-cell-0";
            if (seconds > 0) {
                if (seconds <= 7200) colorClass = "heatmap-cell-1";
                else if (seconds <= 14400) colorClass = "heatmap-cell-2";
                else if (seconds <= 21600) colorClass = "heatmap-cell-3";
                else colorClass = "heatmap-cell-4";
            }
            cell.getStyleClass().add(colorClass);

            // 當日沒有學習的話就留空 不用寫時間
            String timeStr = formatCalendarDuration(seconds);
            cell.setText(date.getDayOfMonth() + "\n" + timeStr);
            cell.setStyle("-fx-text-alignment: center; -fx-line-spacing: -2; -fx-padding: 0; -fx-font-size: 9px;");

            // 非當月日期設置 0.5 不透明度
            if (!cellMonth.equals(currentMonth)) {
                cell.setOpacity(0.5);
            }

            // 邊框與選中判定
            if (date.equals(selectedDailyDate)) {
                cell.getStyleClass().add("heatmap-cell-active");
                activeCellButton = cell;
            } else {
                cell.getStyleClass().add("heatmap-cell-inactive");
            }

            cell.setOnAction(e -> {
                selectedDailyDate = date;
                if (!cellMonth.equals(currentMonth)) {
                    currentMonth = cellMonth;
                    refreshCalendarGrid(grid, lblMonth);
                } else {
                    refreshCalendarGrid(grid, lblMonth);
                }
                refreshDailyDetails();
            });

            grid.add(cell, col, row);
        }
    }

    private void refreshDailyDetails() {
        if (scrollContent.getChildren().size() >= 3 && scrollContent.getChildren().get(2) instanceof VBox) {
            VBox container = (VBox) scrollContent.getChildren().get(2);
            loadDailyDetailsInto(container);
        }
    }

    private void loadDailyDetailsInto(VBox container) {
        container.getChildren().clear();

        DailyDetailData data = dataManager.getDailyDetail(selectedDailyDate);

        // 標題與日期
        Label lblDate = new Label(selectedDailyDate.toString());
        lblDate.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        container.getChildren().add(lblDate);

        // 數值卡片 HBox
        HBox cardsBox = new HBox(10);
        cardsBox.setAlignment(Pos.CENTER);

        VBox cardFocus = createStatsCard("總專注時間", formatSeconds(data.getTotalFocusSeconds()));
        VBox cardDistract = createStatsCard("分心時間", formatSeconds(data.getDistractionSeconds()));
        VBox cardScore = createStatsCard("專注分數", String.format("%.0f分", data.getFocusScore() * 100));

        cardsBox.getChildren().addAll(cardFocus, cardDistract, cardScore);
        container.getChildren().add(cardsBox);

        // 評語卡片
        String dynamicComment = FocusCommentGenerator.getComment(
                data.getFocusScore(),
                data.getTotalFocusSeconds(),
                data.getDistractionSeconds(),
                selectedDailyDate
        );
        VBox cardComment = createCommentCard(dynamicComment);
        container.getChildren().add(cardComment);

        // PieChart + 右側圖例
        if (data.getSubjectTimes().isEmpty()) {
            container.getChildren().add(new Label("本日無科目專注數據"));
        } else {
            HBox chartContainer = new HBox(15);
            chartContainer.setAlignment(Pos.CENTER);
            chartContainer.setPadding(new Insets(10));

            PieChart pieChart = new PieChart();
            pieChart.setTitle("科目專注分佈");
            pieChart.setPrefHeight(180);
            pieChart.setPrefWidth(220);
            pieChart.setLegendVisible(false);
            pieChart.setLabelsVisible(false);

            double totalSec = 0;
            for (SubjectTime st : data.getSubjectTimes()) {
                totalSec += st.getDurationSeconds();
            }

            VBox legendBox = new VBox(8);
            legendBox.setAlignment(Pos.CENTER_LEFT);
            legendBox.setPadding(new Insets(10));

            for (SubjectTime st : data.getSubjectTimes()) {
                double pct = totalSec == 0 ? 0 : (double) st.getDurationSeconds() / totalSec * 100;

                PieChart.Data slice = new PieChart.Data(String.format("%.1f%%", pct), st.getDurationSeconds());
                String color = getSubjectColor(st.getSubject());
                slice.nodeProperty().addListener((obs, oldNode, newNode) -> {
                    if (newNode != null) {
                        newNode.setStyle("-fx-pie-color: " + color + "; -fx-fill: " + color + "; -fx-stroke: transparent; -fx-stroke-width: 0px;");
                    }
                });
                pieChart.getData().add(slice);

                // 右側圖例
                HBox legendItem = new HBox(8);
                legendItem.setAlignment(Pos.CENTER_LEFT);

                Circle colorIndicator = new Circle(6);
                colorIndicator.setFill(Color.web(color));

                Label descLbl = new Label(st.getSubject() + ": " + formatSeconds(st.getDurationSeconds()));
                descLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");

                legendItem.getChildren().addAll(colorIndicator, descLbl);
                legendBox.getChildren().add(legendItem);
            }

            chartContainer.getChildren().addAll(pieChart, legendBox);
            container.getChildren().add(chartContainer);
        }

        // 專注時間軸
        Label lblTimelineTitle = new Label("專注時間軸");
        lblTimelineTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        VBox timelineBox = new VBox(0);
        timelineBox.setAlignment(Pos.TOP_LEFT);
        timelineBox.setPadding(new Insets(10, 20, 10, 20));

        if (data.getTimelineEvents().isEmpty()) {
            timelineBox.getChildren().add(new Label("本日無時間軸數據"));
        } else {
            List<TimelineEvent> events = data.getTimelineEvents();
            for (int i = 0; i < events.size(); i++) {
                TimelineEvent event = events.get(i);
                HBox row = new HBox(15);
                row.setAlignment(Pos.CENTER_LEFT);

                VBox lineBox = new VBox();
                lineBox.setAlignment(Pos.CENTER);
                lineBox.setPrefWidth(20);

                Rectangle topLine = new Rectangle(2, 15);
                topLine.setFill(Color.GRAY);
                if (i == 0) {
                    topLine.setVisible(false);
                }

                Circle dot = new Circle(6);
                dot.setFill(Color.web(getSubjectColor(event.getSubject())));

                Rectangle bottomLine = new Rectangle(2, 15);
                bottomLine.setFill(Color.GRAY);
                if (i == events.size() - 1) {
                    bottomLine.setVisible(false);
                }

                lineBox.getChildren().addAll(topLine, dot, bottomLine);

                HBox infoRow = new HBox(15);
                infoRow.setAlignment(Pos.CENTER_LEFT);

                String timeStr = event.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")) + " - " +
                        event.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm"));
                Label timeLbl = new Label(timeStr);
                timeLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
                timeLbl.setPrefWidth(90);

                Label subjectLbl = new Label(event.getSubject());
                String subjectColor = getSubjectColor(event.getSubject());
                subjectLbl.setStyle("-fx-background-color: " + hexToRgba(subjectColor, 0.15) + "; -fx-text-fill: "
                        + subjectColor
                        + "; -fx-padding: 3 8; -fx-background-radius: 8; -fx-font-size: 11px; -fx-font-weight: bold;");
                subjectLbl.setPrefWidth(80);
                subjectLbl.setAlignment(Pos.CENTER);

                long durationSec = java.time.Duration.between(event.getStartTime(), event.getEndTime()).getSeconds();
                Label durationLbl = new Label("(" + formatSeconds(durationSec) + ")");
                durationLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #888888;");

                infoRow.getChildren().addAll(timeLbl, subjectLbl, durationLbl);
                row.getChildren().addAll(lineBox, infoRow);
                timelineBox.getChildren().add(row);
            }
        }
        container.getChildren().addAll(lblTimelineTitle, timelineBox);

        // 分心軟體排行榜
        Label lblDistractTitle = new Label("分心軟體排行榜");
        lblDistractTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        container.getChildren().add(lblDistractTitle);

        if (data.getTopDistractions().isEmpty()) {
            container.getChildren().add(new Label("本日無分心記錄"));
        } else {
            VBox distractLeaderboard = createDistractionLeaderboard(data.getTopDistractions());
            container.getChildren().add(distractLeaderboard);
        }
    }

    // 每週趨勢視圖
    private void showWeeklyView() {
        currentViewType = "Weekly";
        scrollContent.getChildren().clear();
        clearSubjectColors();

        // 週導航列
        HBox weekHeader = new HBox(20);
        weekHeader.setAlignment(Pos.CENTER);
        Button btnPrevWeek = new Button("◀");
        btnPrevWeek.getStyleClass().add("circular-btn");
        Button btnNextWeek = new Button("▶");
        btnNextWeek.getStyleClass().add("circular-btn");
        Label lblWeek = new Label();
        weekHeader.getChildren().addAll(btnPrevWeek, lblWeek, btnNextWeek);

        VBox weeklyDetailsContainer = new VBox(20);
        weeklyDetailsContainer.setAlignment(Pos.TOP_CENTER);

        scrollContent.getChildren().addAll(weekHeader, weeklyDetailsContainer);

        Runnable updateWeekLabel = () -> {
            LocalDate startOfWeek = currentWeeklyDate.minusDays(currentWeeklyDate.getDayOfWeek().getValue() - 1);
            LocalDate endOfWeek = startOfWeek.plusDays(6);
            String weekRangeStr = String.format("%d / Week %d\n(%s - %s)",
                    startOfWeek.getYear(),
                    startOfWeek.get(WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear()),
                    startOfWeek.format(DateTimeFormatter.ofPattern("MM/dd")),
                    endOfWeek.format(DateTimeFormatter.ofPattern("MM/dd")));
            lblWeek.setText(weekRangeStr);
            lblWeek.setStyle("-fx-text-alignment: center; -fx-font-size: 13px; -fx-font-weight: bold;");
        };

        updateWeekLabel.run();

        btnPrevWeek.setOnAction(e -> {
            currentWeeklyDate = currentWeeklyDate.minusDays(7);
            updateWeekLabel.run();
            loadWeeklyDetailsInto(weeklyDetailsContainer);
        });

        btnNextWeek.setOnAction(e -> {
            currentWeeklyDate = currentWeeklyDate.plusDays(7);
            updateWeekLabel.run();
            loadWeeklyDetailsInto(weeklyDetailsContainer);
        });

        loadWeeklyDetailsInto(weeklyDetailsContainer);
    }

    private void loadWeeklyDetailsInto(VBox container) {
        container.getChildren().clear();

        WeeklyDetailData data = dataManager.getWeeklyDetail(currentWeeklyDate);

        // 數據洞察卡片
        VBox cardInsight = createWeeklyInsightCard(data);
        container.getChildren().add(cardInsight);

        // 數值卡片
        HBox cardsBox = new HBox(10);
        cardsBox.setAlignment(Pos.CENTER);

        VBox cardFocus = createStatsCard("本週專注時間", formatSeconds(data.getTotalFocusSeconds()));
        VBox cardDistract = createStatsCard("本週分心時間", formatSeconds(data.getDistractionSeconds()));
        VBox cardAvg = createStatsCard("日平均專注", formatSeconds(data.getDailyAverageSeconds()));

        cardsBox.getChildren().addAll(cardFocus, cardDistract, cardAvg);
        container.getChildren().add(cardsBox);

        // 每日專注趨勢
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel(null);
        StackedBarChart<String, Number> barChart = new StackedBarChart<>(xAxis, yAxis);
        barChart.setTitle("本週專注趨勢");
        barChart.setPrefHeight(240);
        barChart.setLegendSide(Side.BOTTOM);

        Set<String> subjects = new LinkedHashSet<>();
        for (DailyChartData dayData : data.getDailyCharts()) {
            for (SubjectTime st : dayData.getSubjectTimes()) {
                subjects.add(st.getSubject());
            }
        }

        for (String subject : subjects) {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(subject);

            for (DailyChartData dayData : data.getDailyCharts()) {
                double hours = 0.0;
                for (SubjectTime st : dayData.getSubjectTimes()) {
                    if (st.getSubject().equals(subject)) {
                        hours = st.getDurationSeconds() / 3600.0;
                        break;
                    }
                }
                String dayName = dayData.getDate().format(DateTimeFormatter.ofPattern("MM/dd"));
                series.getData().add(new XYChart.Data<>(dayName, hours));
            }
            barChart.getData().add(series);
        }
        container.getChildren().add(barChart);

        // 顏色同步
        Platform.runLater(() -> {
            // 1. 同步長條顏色
            for (XYChart.Series<String, Number> series : barChart.getData()) {
                String color = getSubjectColor(series.getName());
                for (XYChart.Data<String, Number> item : series.getData()) {
                    Node node = item.getNode();
                    if (node != null) {
                        node.setStyle("-fx-bar-fill: " + color + ";");
                    }
                }
            }
            // 2. 同步圖例 (Legend) 的符號顏色
            for (Node node : barChart.lookupAll(".chart-legend-item")) {
                if (node instanceof Label) {
                    Label label = (Label) node;
                    String seriesName = label.getText();
                    String color = getSubjectColor(seriesName);
                    Node symbol = label.getGraphic();
                    if (symbol != null) {
                        symbol.setStyle("-fx-background-color: " + color + "; -fx-bar-fill: " + color + ";");
                    }
                }
            }
        });

        // 科目整體比例
        HBox chartContainer = new HBox(15);
        chartContainer.setAlignment(Pos.CENTER);
        chartContainer.setPadding(new Insets(10));

        PieChart pieChart = new PieChart();
        pieChart.setTitle("專注科目比例");
        pieChart.setPrefHeight(180);
        pieChart.setPrefWidth(220);
        pieChart.setLegendVisible(false);
        pieChart.setLabelsVisible(false);

        double totalSec = 0;
        for (SubjectTime st : data.getOverallSubjectTimes()) {
            totalSec += st.getDurationSeconds();
        }

        VBox legendBox = new VBox(8);
        legendBox.setAlignment(Pos.CENTER_LEFT);
        legendBox.setPadding(new Insets(10));

        for (SubjectTime st : data.getOverallSubjectTimes()) {
            double pct = totalSec == 0 ? 0 : (double) st.getDurationSeconds() / totalSec * 100;
            PieChart.Data slice = new PieChart.Data(String.format("%.1f%%", pct), st.getDurationSeconds());
            String color = getSubjectColor(st.getSubject());
            slice.nodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode != null) {
                    newNode.setStyle("-fx-pie-color: " + color + "; -fx-fill: " + color + "; -fx-stroke: transparent; -fx-stroke-width: 0px;");
                }
            });
            pieChart.getData().add(slice);

            HBox legendItem = new HBox(8);
            legendItem.setAlignment(Pos.CENTER_LEFT);
            Circle colorIndicator = new Circle(6);
            colorIndicator.setFill(Color.web(color));
            Label descLbl = new Label(st.getSubject() + ": " + formatSeconds(st.getDurationSeconds()));
            descLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
            legendItem.getChildren().addAll(colorIndicator, descLbl);
            legendBox.getChildren().add(legendItem);
        }

        chartContainer.getChildren().addAll(pieChart, legendBox);
        container.getChildren().add(chartContainer);

        // 分心軟體排行榜
        Label lblDistractTitle = new Label("本週分心排行榜");
        lblDistractTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        container.getChildren().add(lblDistractTitle);

        if (data.getTopDistractions().isEmpty()) {
            container.getChildren().add(new Label("本週無分心記錄"));
        } else {
            VBox distractLeaderboard = createDistractionLeaderboard(data.getTopDistractions());
            container.getChildren().add(distractLeaderboard);
        }
    }

    // 每月統計視圖
    private void showMonthlyView() {
        currentViewType = "Monthly";
        scrollContent.getChildren().clear();
        clearSubjectColors();

        // 月導航列
        HBox monthHeader = new HBox(20);
        monthHeader.setAlignment(Pos.CENTER);
        Button btnPrevMonth = new Button("◀");
        btnPrevMonth.getStyleClass().add("circular-btn");
        Button btnNextMonth = new Button("▶");
        btnNextMonth.getStyleClass().add("circular-btn");
        Label lblMonth = new Label(currentMonthlyMonth.format(DateTimeFormatter.ofPattern("yyyy/MM")));
        lblMonth.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        monthHeader.getChildren().addAll(btnPrevMonth, lblMonth, btnNextMonth);

        VBox monthlyDetailsContainer = new VBox(20);
        monthlyDetailsContainer.setAlignment(Pos.TOP_CENTER);

        scrollContent.getChildren().addAll(monthHeader, monthlyDetailsContainer);

        btnPrevMonth.setOnAction(e -> {
            currentMonthlyMonth = currentMonthlyMonth.minusMonths(1);
            lblMonth.setText(currentMonthlyMonth.format(DateTimeFormatter.ofPattern("yyyy年MM月")));
            loadMonthlyDetailsInto(monthlyDetailsContainer);
        });

        btnNextMonth.setOnAction(e -> {
            currentMonthlyMonth = currentMonthlyMonth.plusMonths(1);
            lblMonth.setText(currentMonthlyMonth.format(DateTimeFormatter.ofPattern("yyyy年MM月")));
            loadMonthlyDetailsInto(monthlyDetailsContainer);
        });

        loadMonthlyDetailsInto(monthlyDetailsContainer);
    }

    private void loadMonthlyDetailsInto(VBox container) {
        container.getChildren().clear();

        MonthlyDetailData data = dataManager.getMonthlyDetail(currentMonthlyMonth);

        // 數據洞察卡片
        VBox cardInsight = createMonthlyInsightCard(data);
        container.getChildren().add(cardInsight);

        // 數值卡片
        HBox cardsBox = new HBox(10);
        cardsBox.setAlignment(Pos.CENTER);

        VBox cardFocus = createStatsCard("本月專注時間", formatSeconds(data.getTotalFocusSeconds()));
        VBox cardDistract = createStatsCard("本月分心時間", formatSeconds(data.getDistractionSeconds()));
        VBox cardAvg = createStatsCard("日平均專注", formatSeconds(data.getDailyAverageSeconds()));

        cardsBox.getChildren().addAll(cardFocus, cardDistract, cardAvg);
        container.getChildren().add(cardsBox);

        // 月專注趨勢
        NumberAxis xAxis = new NumberAxis();
        xAxis.setAutoRanging(false);
        xAxis.setLowerBound(0);
        xAxis.setUpperBound(currentMonthlyMonth.lengthOfMonth());
        xAxis.setTickUnit(5);
        xAxis.setTickLabelFormatter(new javafx.util.StringConverter<Number>() {
            @Override
            public String toString(Number object) {
                int val = object.intValue();
                if (val == 0) return "";
                return String.valueOf(val);
            }

            @Override
            public Number fromString(String string) {
                return Integer.parseInt(string);
            }
        });

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel(null);

        LineChart<Number, Number> lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setTitle("月專注趨勢");
        lineChart.setPrefHeight(240);
        lineChart.setLegendVisible(false);
        lineChart.getStyleClass().add("monthly-trend-line");

        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("專注時數");

        for (DailyChartData dayData : data.getDailyCharts()) {
            double hours = dayData.getTotalFocusSeconds() / 3600.0;
            int dayVal = dayData.getDate().getDayOfMonth();
            series.getData().add(new XYChart.Data<>(dayVal, hours));
        }
        lineChart.getData().add(series);
        container.getChildren().add(lineChart);

        // 科目整體比例
        HBox chartContainer = new HBox(15);
        chartContainer.setAlignment(Pos.CENTER);
        chartContainer.setPadding(new Insets(10));

        PieChart pieChart = new PieChart();
        pieChart.setTitle("科目整體比例");
        pieChart.setPrefHeight(180);
        pieChart.setPrefWidth(220);
        pieChart.setLegendVisible(false);
        pieChart.setLabelsVisible(false);

        double totalSec = 0;
        for (SubjectTime st : data.getOverallSubjectTimes()) {
            totalSec += st.getDurationSeconds();
        }

        VBox legendBox = new VBox(8);
        legendBox.setAlignment(Pos.CENTER_LEFT);
        legendBox.setPadding(new Insets(10));

        for (SubjectTime st : data.getOverallSubjectTimes()) {
            double pct = totalSec == 0 ? 0 : (double) st.getDurationSeconds() / totalSec * 100;
            PieChart.Data slice = new PieChart.Data(String.format("%.1f%%", pct), st.getDurationSeconds());
            String color = getSubjectColor(st.getSubject());
            slice.nodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode != null) {
                    newNode.setStyle("-fx-pie-color: " + color + "; -fx-fill: " + color + "; -fx-stroke: transparent; -fx-stroke-width: 0px;");
                }
            });
            pieChart.getData().add(slice);

            HBox legendItem = new HBox(8);
            legendItem.setAlignment(Pos.CENTER_LEFT);
            Circle colorIndicator = new Circle(6);
            colorIndicator.setFill(Color.web(color));
            Label descLbl = new Label(st.getSubject() + ": " + formatSeconds(st.getDurationSeconds()));
            descLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
            legendItem.getChildren().addAll(colorIndicator, descLbl);
            legendBox.getChildren().add(legendItem);
        }

        chartContainer.getChildren().addAll(pieChart, legendBox);
        container.getChildren().add(chartContainer);

        // 3. 分心排行榜
        Label lblDistractTitle = new Label("本月分心排行榜");
        lblDistractTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        container.getChildren().add(lblDistractTitle);

        if (data.getTopDistractions().isEmpty()) {
            container.getChildren().add(new Label("本月無分心記錄"));
        } else {
            VBox distractLeaderboard = createDistractionLeaderboard(data.getTopDistractions());
            container.getChildren().add(distractLeaderboard);
        }
    }

    // 輔助 UI 元件
    private VBox createStatsCard(String title, String value) {
        VBox card = new VBox(6);
        card.getStyleClass().add("stats-card");
        card.setPrefWidth(140);
        card.setAlignment(Pos.CENTER);

        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("stats-card-title");
        titleLbl.setStyle("-fx-font-size: 11px;");

        Label valueLbl = new Label(value);
        valueLbl.getStyleClass().add("stats-card-value");
        valueLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        card.getChildren().addAll(titleLbl, valueLbl);
        return card;
    }

    private VBox createCommentCard(String comment) {
        VBox card = new VBox(6);
        card.getStyleClass().add("stats-card");
        card.setPrefWidth(430);
        card.setAlignment(Pos.CENTER_LEFT);

        Label titleLbl = new Label("每日專注點評");
        titleLbl.getStyleClass().add("stats-card-title");
        titleLbl.setStyle("-fx-font-size: 12px;");

        Label valueLbl = new Label(comment);
        valueLbl.getStyleClass().add("stats-card-comment");
        valueLbl.setWrapText(true);
        valueLbl.setStyle("-fx-font-size: 12px; -fx-line-spacing: 3;");

        card.getChildren().addAll(titleLbl, valueLbl);
        return card;
    }

    private VBox createWeeklyInsightCard(WeeklyDetailData data) {
        VBox card = new VBox(8);
        card.getStyleClass().add("stats-card");
        card.setPrefWidth(430);
        card.setPadding(new Insets(12));
        card.setAlignment(Pos.CENTER_LEFT);

        Label titleLbl = new Label("本週專注洞察與大師評語");
        titleLbl.getStyleClass().add("stats-card-title");
        titleLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        // 尋找專注最久學科
        String topSubject = "無";
        long maxSec = 0;
        for (SubjectTime st : data.getOverallSubjectTimes()) {
            if (st.getDurationSeconds() > maxSec) {
                maxSec = st.getDurationSeconds();
                topSubject = st.getSubject();
            }
        }

        // 計算分心佔比
        double distRatio = data.getTotalFocusSeconds() == 0 ? 0
                : (double) data.getDistractionSeconds() / data.getTotalFocusSeconds() * 100;

        // 評定稱號 (結合時長與分心比，以小時與比率為依據)
        String rank = "精靈球專注者";
        String iconName = "Poké_Ball.png";
        long focusSecs = data.getTotalFocusSeconds();
        if (focusSecs >= 54000 && distRatio < 10.0) {       // >= 15小時 且 分心率 < 10%
            rank = "大師球專注大師";
            iconName = "Master_Ball.png";
        } else if (focusSecs >= 36000 && distRatio < 15.0) { // >= 10小時 且 分心率 < 15%
            rank = "高級球專注大師";
            iconName = "Ultra_Ball.png";
        } else if (focusSecs >= 18000 && distRatio < 25.0) { // >= 5小時 且 分心率 < 25%
            rank = "超級球專注者";
            iconName = "Great_Ball.png";
        }

        Label fldDistRatio = new Label("分心佔比: " + String.format("%.1f%%", distRatio));
        fldDistRatio.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");

        Label fldRank = new Label("本週稱號: " + rank);
        fldRank.getStyleClass().add("rank-comment");

        try {
            javafx.scene.image.ImageView iconView = new javafx.scene.image.ImageView(
                new javafx.scene.image.Image("file:res/poke_ball/" + iconName)
            );
            iconView.setFitWidth(16);
            iconView.setFitHeight(16);
            iconView.setPreserveRatio(true);
            fldRank.setGraphic(iconView);
            fldRank.setGraphicTextGap(6);
        } catch (Exception e) {
            System.err.println("無法載入稱號圖示: " + e.getMessage());
        }

        double totalFocusHours = data.getTotalFocusSeconds() / 3600.0;
        String comment = FocusCommentGenerator.getWeeklyComment(distRatio, totalFocusHours, topSubject, this.currentWeeklyDate);
        Label commentLbl = new Label(comment);
        commentLbl.setWrapText(true);
        commentLbl.setStyle("-fx-font-size: 12px; -fx-line-spacing: 2; -fx-text-fill: #bdc3c7;");

        card.getChildren().addAll(titleLbl, fldDistRatio, fldRank, new Separator(), commentLbl);
        return card;
    }

    private VBox createMonthlyInsightCard(MonthlyDetailData data) {
        VBox card = new VBox(8);
        card.getStyleClass().add("stats-card");
        card.setPrefWidth(430);
        card.setPadding(new Insets(12));
        card.setAlignment(Pos.CENTER_LEFT);

        Label titleLbl = new Label("本月統計分析與大師成就");
        titleLbl.getStyleClass().add("stats-card-title");
        titleLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        String topSubject = "無";
        long maxSec = 0;
        for (SubjectTime st : data.getOverallSubjectTimes()) {
            if (st.getDurationSeconds() > maxSec) {
                maxSec = st.getDurationSeconds();
                topSubject = st.getSubject();
            }
        }

        double distRatio = data.getTotalFocusSeconds() == 0 ? 0 : 
            (double) data.getDistractionSeconds() / data.getTotalFocusSeconds() * 100;

        // 評定月度稱號 (結合時長與分心比)
        String rank = "精靈球級別專注者";
        String iconName = "Poké_Ball.png";
        long focusSecs = data.getTotalFocusSeconds();
        if (focusSecs >= 216000 && distRatio < 10.0) {       // >= 60小時 且 分心率 < 10%
            rank = "大師球級別領袖";
            iconName = "Master_Ball.png";
        } else if (focusSecs >= 144000 && distRatio < 15.0) { // >= 40小時 且 分心率 < 15%
            rank = "高級球級別專注者";
            iconName = "Ultra_Ball.png";
        } else if (focusSecs >= 72000 && distRatio < 25.0) {  // >= 20小時 且 分心率 < 25%
            rank = "超級球級別專注者";
            iconName = "Great_Ball.png";
        }

        Label fldDistRatio = new Label("月平均分心率: " + String.format("%.1f%%", distRatio));
        fldDistRatio.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");

        Label fldRank = new Label("月度榮譽稱號: " + rank);
        fldRank.getStyleClass().add("rank-comment");

        try {
            javafx.scene.image.ImageView iconView = new javafx.scene.image.ImageView(
                new javafx.scene.image.Image("file:res/poke_ball/" + iconName)
            );
            iconView.setFitWidth(16);
            iconView.setFitHeight(16);
            iconView.setPreserveRatio(true);
            fldRank.setGraphic(iconView);
            fldRank.setGraphicTextGap(6);
        } catch (Exception e) {
            System.err.println("無法載入稱號圖示: " + e.getMessage());
        }

        double totalFocusHours = data.getTotalFocusSeconds() / 3600.0;
        String comment = FocusCommentGenerator.getMonthlyComment(distRatio, totalFocusHours, topSubject, this.currentMonthlyMonth.atDay(1));
        Label commentLbl = new Label(comment);
        commentLbl.setWrapText(true);
        commentLbl.setStyle("-fx-font-size: 12px; -fx-line-spacing: 2; -fx-text-fill: #bdc3c7;");

        card.getChildren().addAll(titleLbl, fldDistRatio, fldRank, new Separator(), commentLbl);
        return card;
    }

    private VBox createDistractionLeaderboard(List<DistractionApp> apps) {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(5, 10, 5, 10));

        long maxSeconds = 1;
        for (DistractionApp app : apps) {
            if (app.getDurationSeconds() > maxSeconds) {
                maxSeconds = app.getDurationSeconds();
            }
        }

        for (DistractionApp app : apps) {
            VBox item = new VBox(4);
            HBox header = new HBox();
            header.setAlignment(Pos.CENTER_LEFT);

            Label nameLbl = new Label(app.getAppName());
            nameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label durationLbl = new Label(formatSeconds(app.getDurationSeconds()));
            durationLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #888888;");

            header.getChildren().addAll(nameLbl, spacer, durationLbl);

            ProgressBar bar = new ProgressBar();
            bar.setMaxWidth(Double.MAX_VALUE);
            double progress = (double) app.getDurationSeconds() / maxSeconds;
            bar.setProgress(progress);
            bar.setStyle("-fx-accent: #e74c3c;");

            item.getChildren().addAll(header, bar);
            vbox.getChildren().add(item);
        }
        return vbox;
    }

    private String formatSeconds(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    private String formatSecondsShort(long seconds) {
        return formatSeconds(seconds);
    }

    private String formatCalendarDuration(long seconds) {
        if (seconds <= 0) {
            return "";
        }
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        return String.format("%02d:%02d", h, m);
    }

    // 調試與模式設定折疊區
    private TitledPane createDebugCollapsibleSection() {
        TitledPane debugPane = new TitledPane();
        debugPane.setText("偵測與調試設定");
        debugPane.setExpanded(false); // 預設折疊起來

        VBox debugLayout = new VBox(15);
        debugLayout.setPadding(new Insets(15));
        debugLayout.setAlignment(Pos.CENTER);

        // A. 分心模式設定
        JdbcDistractionModeRepository modeRepository = new JdbcDistractionModeRepository(new DatabaseCore());
        DistractionHandlingMode currentMode = modeRepository.loadMode(DistractionHandlingMode.WARN_USER);

        Label modeStatusLabel = new Label("目前分心處理模式: " + currentMode.name());
        modeStatusLabel.setStyle("-fx-font-size: 12px;");

        ToggleButton modeSwitch = createDistractionModeSwitch(currentMode, modeStatusLabel, modeRepository);
        Label warnLabel = new Label("提醒");
        Label closeLabel = new Label("關閉");
        HBox modeSwitchRow = new HBox(12, warnLabel, modeSwitch, closeLabel);
        modeSwitchRow.setAlignment(Pos.CENTER);

        // B. 模擬增加資源
        Button btnAdd = new Button("DEBUG: 增加資源 200");
        Button btnAdd1 = new Button("DEBUG: 增加資源 100");
        Button btnAdd2 = new Button("DEBUG: 增加資源 50");

        btnAdd.setOnAction(e -> {
            gameManager.addFocusTime(200, mainApp.getCurrentPokemonId());
            mainApp.refreshCurrencyLabels();
            mainApp.refreshXpDisplay();
            mainApp.refreshPokedexGrid();
            mainApp.saveUserProgressSafely();
        });

        btnAdd1.setOnAction(e -> {
            gameManager.addFocusTime(100, mainApp.getCurrentPokemonId());
            mainApp.refreshCurrencyLabels();
            mainApp.refreshXpDisplay();
            mainApp.refreshPokedexGrid();
            mainApp.saveUserProgressSafely();
        });

        btnAdd2.setOnAction(e -> {
            gameManager.addFocusTime(50, mainApp.getCurrentPokemonId());
            mainApp.refreshCurrencyLabels();
            mainApp.refreshXpDisplay();
            mainApp.refreshPokedexGrid();
            mainApp.saveUserProgressSafely();
        });

        HBox btnBox = new HBox(10, btnAdd, btnAdd1, btnAdd2);
        btnBox.setAlignment(Pos.CENTER);

        debugLayout.getChildren().addAll(modeStatusLabel, modeSwitchRow, new Separator(), btnBox);
        debugPane.setContent(debugLayout);

        return debugPane;
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