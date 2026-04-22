module jfocus {
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.fxml;

    // 開放這個 package 給 JavaFX 執行環境讀取
    opens jfocus.ui to javafx.graphics, javafx.fxml;
}