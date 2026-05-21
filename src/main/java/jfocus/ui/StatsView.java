package jfocus.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

// 讓這個類別直接繼承 VBox，它本身就是一個 UI 元件
public class StatsView extends VBox {
    
    private GameManager gameManager;

    // 建構子：把需要的資料傳進來
    public StatsView(GameManager gameManager) {
        this.gameManager = gameManager;
        
        this.setPadding(new Insets(20));
        this.setSpacing(20);
        this.setAlignment(Pos.CENTER);
        
        // 呼叫建立畫面的方法
        buildUI();
    }

    private void buildUI() {
        // --- 1. 頂部總覽數據 ---
        Label titleLabel = new Label("📈 專注數據統計");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        Label totalTimeLabel = new Label("總專注時間： " + gameManager.getTotalXP() + " 分鐘");
        totalTimeLabel.getStyleClass().add("type-tag"); 
        totalTimeLabel.setStyle("-fx-background-color: #3b82f6; -fx-font-size: 16px;");

        // --- 2. 圓餅圖：各科目專注比例 (預設假資料) ---
        PieChart pieChart = new PieChart();
        pieChart.getData().addAll(
            new PieChart.Data("資料結構", 120),
            new PieChart.Data("計算機組織", 85),
            new PieChart.Data("線性代數", 45),
            new PieChart.Data("英文", 30)
        );
        pieChart.setTitle("各科目專注分佈");
        pieChart.setPrefHeight(300);

        // --- 3. 長條圖：近五天專注時數 (預設假資料) ---
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
        barChart.setTitle("近五日專注趨勢 (分鐘)");
        
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("專注時間");
        series.getData().add(new XYChart.Data<>("一", 45));
        series.getData().add(new XYChart.Data<>("二", 120));
        series.getData().add(new XYChart.Data<>("三", 60));
        series.getData().add(new XYChart.Data<>("四", 90));
        series.getData().add(new XYChart.Data<>("五", 150));
        
        barChart.getData().add(series);
        barChart.setPrefHeight(300);

        // 將圖表橫向排列 (如果畫面夠寬)，或維持 VBox 直向排列
        HBox chartsBox = new HBox(20, pieChart, barChart);
        chartsBox.setAlignment(Pos.CENTER);

        // 把所有元件加到這個 VBox (StatsView) 裡面
        this.getChildren().addAll(titleLabel, totalTimeLabel, chartsBox);
    }
    
    // 未來可以寫一個 refresh() 方法，讓 FocusUI 呼叫它來更新圖表
    public void refreshStats() {
        // 從資料庫撈最新資料重新繪製圖表...
    }
}