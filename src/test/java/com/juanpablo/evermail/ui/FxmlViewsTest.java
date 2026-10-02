package com.juanpablo.evermail.ui;

import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class FxmlViewsTest {
    @BeforeAll static void initializeFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.countDown(); }); }
        catch (IllegalStateException alreadyStarted) { ready.countDown(); }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
    }
    private static <T> T fx(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work);
        Platform.runLater(task); return task.get(30, TimeUnit.SECONDS);
    }

    @Test void allScreensLoadAndReflowWithoutHorizontalOverflow() throws Exception {
        for (String name : List.of("loading", "loaded", "login", "main", "reader", "logout")) {
            for (int[] size : List.of(new int[]{1280,720}, new int[]{960,540}, new int[]{600,900}, new int[]{360,800})) {
                fx(() -> {
                    ScrollPane root = FXMLLoader.load(getClass().getResource("/com/juanpablo/evermail/fxml/" + PreviewData.screen(name)));
                    PreviewData.populate(root);
                    Scene scene = new Scene(root, size[0], size[1]);
                    root.resize(size[0], size[1]);
                    for (int i = 0; i < 5; i++) { root.applyCss(); root.layout(); }
                    assertTrue(root.getContent().getLayoutBounds().getWidth() <= root.getViewportBounds().getWidth() + 1, name);
                    verifyChildren((Parent) root.getContent(), name);
                    if (root.getContent() instanceof ResponsiveGrid grid) {
                        assertEquals(size[0] < 680 ? 1 : name.equals("main") && size[0] >= 1100 ? 3 : size[0] >= 760 || name.equals("login") ? 2 : 1,
                                grid.getColumnConstraints().size(), name);
                    }
                    save(root.snapshot(null, null), name + "-" + size[0] + ".png");
                    if (name.equals("main") && size[0] == 1280) {
                        for (String selector : List.of("#sendButton", "#loadMoreButton", "#logoutButton", "#inboxButton")) {
                            Button button = (Button) root.lookup(selector);
                            var normal = button.getBackground().getFills().getFirst().getFill();
                            button.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("hover"), true);
                            button.applyCss();
                            assertNotEquals(normal, button.getBackground().getFills().getFirst().getFill(), selector);
                        }
                        save(root.snapshot(null, null), "main-hover-1280.png");
                    }
                    // Scroll through the narrow layout too, so lower sections are visually reviewable.
                    if (size[0] == 360 && (name.equals("main") || name.equals("reader"))) {
                        root.setVvalue(1); root.layout();
                        save(root.snapshot(null, null), name + "-360-bottom.png");
                    }
                    scene.setRoot(new StackPane());
                    return null;
                });
            }
        }
    }
    private static void verifyChildren(Parent parent, String screen) {
        for (Node child : parent.getChildrenUnmodifiable()) {
            if (!child.isManaged() || !child.isVisible()) continue;
            if (parent instanceof HBox || parent instanceof VBox || parent instanceof GridPane || parent instanceof FlowPane) {
                assertTrue(child.getBoundsInParent().getMaxX() <= parent.getLayoutBounds().getWidth() + 2,
                        screen + ": horizontal overflow in " + parent.getId() + " / " + child.getId());
            }
            // Virtualized controls manage clipping themselves; inspect the authored layout only.
            if (child instanceof Pane pane) verifyChildren(pane, screen);
        }
    }
    private static void save(WritableImage image, String name) throws Exception {
        Path dir = Path.of("build", "ui-previews"); Files.createDirectories(dir);
        int w = (int)image.getWidth(), h = (int)image.getHeight();
        BufferedImage output = new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[w*h];
        image.getPixelReader().getPixels(0,0,w,h,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,w);
        output.setRGB(0,0,w,h,pixels,0,w); ImageIO.write(output,"png",dir.resolve(name).toFile());
    }

    @Test void readerPreviewSwitchesBetweenTextAndFormat() throws Exception {
        fx(() -> {
            Parent root = FXMLLoader.load(getClass().getResource("/com/juanpablo/evermail/fxml/Reader-Screen.fxml"));
            PreviewData.populate(root);
            root = (Parent) ((ScrollPane) root).getContent();
            StackPane host = (StackPane)root.lookup("#bodyHost");
            ((Button)root.lookup("#textButton")).fire();
            assertInstanceOf(TextArea.class, host.getChildren().getFirst());
            ((Button)root.lookup("#formattedButton")).fire();
            assertInstanceOf(ScrollPane.class, host.getChildren().getFirst());
            return null;
        });
    }
}
