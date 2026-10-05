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
            for (int[] size : List.of(new int[]{1920,1080}, new int[]{1280,720}, new int[]{960,540}, new int[]{760,730}, new int[]{600,900}, new int[]{360,800})) {
                fx(() -> {
                    ScrollPane root = FXMLLoader.load(getClass().getResource("/com/juanpablo/evermail/fxml/" + PreviewData.screen(name)));
                    PreviewData.populate(root);
                    Scene scene = new Scene(root, size[0], size[1]);
                    root.resize(size[0], size[1]);
                    for (int i = 0; i < 5; i++) { root.applyCss(); root.layout(); }
                    assertTrue(root.getContent().getLayoutBounds().getWidth() <= root.getViewportBounds().getWidth() + 1, name);
                    verifyChildren((Parent) root.getContent(), name);
                    if (root.getContent() instanceof ResponsiveGrid grid) {
                        double width = grid.getWidth();
                        assertEquals(name.equals("login") ? (width >= 900 ? 2 : 1)
                                        : name.equals("main") && width >= 1100 ? 3 : width >= 760 ? 2 : 1,
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

    @Test void resizingExistingViewsRestoresDesktopLayout() throws Exception {
        for (String name : List.of("login", "main", "reader", "logout")) {
            fx(() -> {
                ScrollPane root = FXMLLoader.load(getClass().getResource(
                        "/com/juanpablo/evermail/fxml/" + PreviewData.screen(name)));
                PreviewData.populate(root);
                Scene scene = new Scene(root);
                for (int width : new int[]{1920, 360, 960, 600, 1920}) {
                    root.resize(width, 1080);
                    for (int i = 0; i < 6; i++) { root.applyCss(); root.layout(); }
                    verifyChildren((Parent) root.getContent(), name + " resized to " + width);
                    if (name.equals("reader")) {
                        ReaderDetails details = (ReaderDetails) root.lookup(".reader-details");
                        assertEquals(details.getWidth() < 650 ? 1 : 2,
                                details.getColumnConstraints().size());
                    }
                    if (name.equals("logout")) {
                        for (Node node : root.lookupAll(".logout-copy")) {
                            Label label = (Label) node;
                            assertTrue(label.getHeight() + 1 >= label.prefHeight(label.getWidth()),
                                    "Logout copy must remain fully readable at " + width);
                        }
                    }
                }
                scene.setRoot(new StackPane());
                return null;
            });
        }
    }

    @Test void loginCardKeepsContentHeightAcrossWindowSizes() throws Exception {
        fx(() -> {
            ScrollPane root = FXMLLoader.load(getClass().getResource(
                    "/com/juanpablo/evermail/fxml/Login-Screen.fxml"));
            Scene scene = new Scene(root);
            for (int[] size : List.of(new int[]{960,540}, new int[]{1280,720},
                    new int[]{1920,1080}, new int[]{900,700}, new int[]{760,730},
                    new int[]{360,800}, new int[]{960,360}, new int[]{960,540})) {
                root.resize(size[0], size[1]);
                for (int i = 0; i < 6; i++) { root.applyCss(); root.layout(); }
                LoginCard card = (LoginCard) root.lookup(".login-card");
                assertEquals(Region.USE_PREF_SIZE, card.getMaxHeight());
                assertTrue(card.getWidth() <= 326.5);
                double expected = Math.max(card.minHeight(card.getWidth()), card.prefHeight(card.getWidth()));
                assertEquals(expected, card.getHeight(), 1, "Card must not stretch to viewport height");
                ResponsiveGrid grid = (ResponsiveGrid) root.getContent();
                if (grid.getColumnConstraints().size() == 2) {
                    double center = grid.getInsets().getTop()
                            + (grid.getHeight() - grid.getInsets().getTop() - grid.getInsets().getBottom()) / 2;
                    assertEquals(center, card.getLayoutY() + card.getHeight() / 2, 1,
                            "Login card must stay vertically centered in desktop layout");
                }
                for (Node child : card.getChildren()) {
                    if (!child.isManaged()) continue;
                    assertTrue(child.getBoundsInParent().getMaxY() <= card.getHeight() + 1,
                            "Login content must fit inside card");
                    if (child instanceof Label label && label.isWrapText()) {
                        assertTrue(label.getHeight() + 1 >= label.prefHeight(label.getWidth()),
                                "Login text must not be clipped: " + label.getText());
                    }
                }
                verifyChildren((Parent) root.getContent(), "login");
            }
            scene.setRoot(new StackPane());
            return null;
        });
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
