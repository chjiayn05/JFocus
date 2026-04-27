module jfocus {
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.fxml;
    requires com.google.gson;

    // --- 將所有 opens 合併成這一行 ---
    opens jfocus.ui to com.google.gson, javafx.graphics, javafx.fxml;

    exports jfocus.ui;
}