package com.juanpablo.evermail.ui;

import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.scene.control.Button;
import javafx.util.Duration;

/** Subtle, interruptible feedback; CSS defines the hover/focus/pressed colors. */
public final class AnimatedButton extends Button {
    private final ScaleTransition motion = new ScaleTransition(Duration.millis(130), this);

    public AnimatedButton() {
        motion.setInterpolator(Interpolator.EASE_OUT);
        hoverProperty().addListener((o, before, after) -> animate());
        pressedProperty().addListener((o, before, after) -> animate());
        disabledProperty().addListener((o, before, after) -> animate());
        sceneProperty().addListener((o, before, after) -> {
            if (after == null) { motion.stop(); setScaleX(1); setScaleY(1); }
        });
    }

    private void animate() {
        motion.stop();
        // Opt-out for users who prefer reduced motion; hover colors remain available.
        double scale = !isDisabled() && !Boolean.getBoolean("evermail.reduceMotion")
                ? isPressed() ? .97 : isHover() ? 1.015 : 1 : 1;
        if (getScene() == null || Boolean.getBoolean("evermail.reduceMotion")) {
            setScaleX(scale); setScaleY(scale); return;
        }
        motion.setToX(scale); motion.setToY(scale); motion.playFromStart();
    }
}
