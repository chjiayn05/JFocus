module jfocus {
    requires javafx.controls;
    requires transitive javafx.graphics;
    requires javafx.fxml;
    requires com.google.gson;
    requires com.sun.jna;
    requires com.sun.jna.platform;
    requires java.desktop;
    requires org.apache.opennlp.tools;
    requires transitive java.sql;
    requires jieba.analysis;

    // --- 將所有 opens 合併成這一行 ---
    opens jfocus.ui to com.google.gson, javafx.graphics, javafx.fxml;
    exports jfocus.main;
    exports jfocus.notification;
    exports jfocus.ui;
}
