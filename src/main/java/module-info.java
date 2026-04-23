module jfocus {
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.fxml;
    requires org.apache.opennlp.tools;
    requires transitive java.sql;
    requires jieba.analysis;

    opens jfocus.ui to javafx.graphics, javafx.fxml;

    exports jfocus.ai;
    exports jfocus.activity;
    exports jfocus.io;
    exports jfocus.db;
    exports jfocus.ui;
}