package jfocus.ui;

import javafx.animation.TranslateTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.scene.Cursor;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

public class ToggleSwitch extends StackPane {

    private static final PseudoClass SELECTED_PC = PseudoClass.getPseudoClass("selected");
    private static final double W = 46, H = 28, THUMB = 22, PAD = 4;
    private static final double THUMB_OFF = -(W / 2 - THUMB / 2 - PAD);
    private static final double THUMB_ON  =  (W / 2 - THUMB / 2 - PAD);

    private final BooleanProperty selected = new SimpleBooleanProperty(false) {
        @Override protected void invalidated() {
            pseudoClassStateChanged(SELECTED_PC, get());
            animateThumb(get());
        }
    };

    private final Region thumb = new Region();

    public ToggleSwitch() {
        Region track = new Region();
        track.getStyleClass().add("toggle-track");
        track.setPrefSize(W, H);
        track.setMaxSize(W, H);

        thumb.getStyleClass().add("toggle-thumb");
        thumb.setPrefSize(THUMB, THUMB);
        thumb.setMaxSize(THUMB, THUMB);
        thumb.setTranslateX(THUMB_OFF);

        getStyleClass().add("toggle-switch");
        setPrefSize(W, H);
        setMaxSize(W, H);
        setCursor(Cursor.HAND);
        getChildren().addAll(track, thumb);

        setOnMouseClicked(e -> setSelected(!isSelected()));
    }

    private void animateThumb(boolean on) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(150), thumb);
        tt.setToX(on ? THUMB_ON : THUMB_OFF);
        tt.play();
    }

    public boolean isSelected()              { return selected.get(); }
    public void setSelected(boolean val)     { selected.set(val); }
    public BooleanProperty selectedProperty(){ return selected; }
}
