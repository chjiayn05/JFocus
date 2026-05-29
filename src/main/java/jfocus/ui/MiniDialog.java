package jfocus.ui;

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Reusable small modal dialog with theme CSS support.
 *
 * Usage:
 *   new MiniDialog.Builder(ownerWindow)
 *       .type(MiniDialog.Type.DANGER)
 *       .title("放棄冒險")
 *       .message("現在放棄將無法獲得任何專注幣與經驗值！")
 *       .primaryBtn("繼續冒險", null)
 *       .dangerBtn("確定放棄", () -> doAbandon())
 *       .show();
 */
public class MiniDialog {

    public enum Type {
        DANGER ("#c0392b", "!"),
        WARNING("#e67e22", "?"),
        INFO   ("#2980b9", "i"),
        SUCCESS("#27ae60", "✓");

        final String color;
        final String symbol;
        Type(String color, String symbol) { this.color = color; this.symbol = symbol; }
    }

    private static final double PANEL_WIDTH = 350;
    private static final double PANEL_HEIGHT_ESTIMATE = 175;

    private final Stage stage;
    private final Window owner;

    private MiniDialog(Builder b) {
        this.owner = b.owner;
        stage = new Stage();
        if (b.owner != null) stage.initOwner(b.owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initStyle(StageStyle.DECORATED);
        stage.setResizable(false);
        if (b.title != null) stage.setTitle(b.title);

        VBox panel = buildPanel(b);
        Scene scene = new Scene(panel, PANEL_WIDTH, -1);
        if (!FocusUI.activeStylesheets.isEmpty()) {
            scene.getStylesheets().addAll(FocusUI.activeStylesheets);
        }
        stage.setScene(scene);
        centerOnOwner();
    }

    public void show() {
        // pre-position before show so title bar appears at correct location
        if (owner != null) {
            stage.setX(owner.getX() + (owner.getWidth()  - PANEL_WIDTH)          / 2);
            stage.setY(owner.getY() + (owner.getHeight() - PANEL_HEIGHT_ESTIMATE) / 2);
        }
        stage.setOpacity(0);
        stage.show();
    }

    private VBox buildPanel(Builder b) {
        VBox panel = new VBox();
        panel.getStyleClass().add("mini-dialog-panel");

        // ── body row: icon badge + text ──
        HBox body = new HBox(18);
        body.setPadding(new Insets(24, 24, 16, 24));
        body.setAlignment(Pos.TOP_LEFT);

        if (b.type != null) {
            body.getChildren().add(buildBadge(b.type));
        }

        VBox textCol = new VBox(6);
        textCol.setAlignment(Pos.TOP_LEFT);

        if (b.title != null) {
            Label titleLbl = new Label(b.title);
            titleLbl.setWrapText(true);
            titleLbl.setMaxWidth(PANEL_WIDTH - 100);
            titleLbl.getStyleClass().add("mini-dialog-title");
            textCol.getChildren().add(titleLbl);
        }
        if (b.message != null) {
            Label msgLbl = new Label(b.message);
            msgLbl.setWrapText(true);
            msgLbl.setMaxWidth(PANEL_WIDTH - 100);
            msgLbl.getStyleClass().add("mini-dialog-message");
            textCol.getChildren().add(msgLbl);
        }
        body.getChildren().add(textCol);
        panel.getChildren().add(body);

        // ── divider ──
        Region divider = new Region();
        divider.setMaxWidth(Double.MAX_VALUE);
        divider.getStyleClass().add("mini-dialog-divider");
        panel.getChildren().add(divider);

        // ── button row ──
        HBox btnRow = new HBox(10);
        btnRow.setPadding(new Insets(14, 25, 18, 20));
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        for (ButtonSpec spec : b.buttons) {
            Button btn = new Button(spec.label);
            //btn.setMinHeight(25);
            btn.setMinWidth(40);
            btn.getStyleClass().add(spec.danger ? "alert-btn-danger" : "alert-btn-primary");
            Runnable action = spec.action;
            btn.setOnAction(e -> {
                stage.close();
                if (action != null) action.run();
            });
            btnRow.getChildren().add(btn);
        }
        panel.getChildren().add(btnRow);
        return panel;
    }

    private StackPane buildBadge(Type type) {
        Region circle = new Region();
        circle.setPrefSize(40, 40);
        circle.setMinSize(40, 40);
        circle.setMaxSize(40, 40);
        circle.setStyle(
            "-fx-background-color: " + type.color + ";" +
            "-fx-background-radius: 20;"
        );

        Label sym = new Label(type.symbol);
        sym.setStyle(
            "-fx-font-size: 20px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: white;"
        );

        StackPane badge = new StackPane(circle, sym);
        badge.setPrefSize(44, 44);
        badge.setMinSize(44, 44);
        badge.setMaxSize(44, 44);
        return badge;
    }

    private void centerOnOwner() {
        stage.setOnShown(e -> {
            if (owner != null) {
                stage.setX(owner.getX() + (owner.getWidth()  - stage.getWidth())  / 2);
                stage.setY(owner.getY() + (owner.getHeight() - stage.getHeight()) / 2);
            }
            stage.setOpacity(1);
            FadeTransition fade = new FadeTransition(Duration.millis(120), stage.getScene().getRoot());
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.play();
        });
    }

    // ── Builder ──────────────────────────────────────────────

    public static class Builder {
        private final Window owner;
        private Type type;
        private String title;
        private String message;
        private final java.util.List<ButtonSpec> buttons = new java.util.ArrayList<>();

        public Builder(Window owner) { this.owner = owner; }

        public Builder type(Type type)       { this.type    = type;    return this; }
        public Builder title(String title)   { this.title   = title;   return this; }
        public Builder message(String msg)   { this.message = msg;     return this; }

        public Builder primaryBtn(String label, Runnable action) {
            buttons.add(new ButtonSpec(label, action, false));
            return this;
        }

        public Builder dangerBtn(String label, Runnable action) {
            buttons.add(new ButtonSpec(label, action, true));
            return this;
        }

        public MiniDialog build() { return new MiniDialog(this); }
        public void show()        { build().show(); }
    }

    private record ButtonSpec(String label, Runnable action, boolean danger) {}
}
