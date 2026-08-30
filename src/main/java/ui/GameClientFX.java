package ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ui.util.FontLoader;

/**
 * JavaFX launcher for the Detective Game GUI client. This class initializes the JavaFX application,
 * loads the FXML, and passes control to the MainController.
 */
public class GameClientFX extends Application {

  private static final org.slf4j.Logger logger =
      org.slf4j.LoggerFactory.getLogger(GameClientFX.class);

  public static void main(String[] args) {
    launch(args);
  }

  @Override
  public void start(Stage primaryStage) throws Exception {
    // Register brand typefaces (DESIGN.md §3) before any Scene resolves CSS.
    FontLoader.loadAll();

    // Load FXML
    FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
    Parent root = loader.load();
    MainController mainController = loader.getController();

    // Pass command-line arguments to the controller
    mainController.setHostServices(getHostServices());
    mainController.setLaunchArgs(getParameters().getRaw());

    // Set up scene
    Scene scene = new Scene(root, 1280, 800);

    // Load CSS if available
    String cssPath =
        getClass().getResource("/css/detective-theme.css") != null
            ? getClass().getResource("/css/detective-theme.css").toExternalForm()
            : null;
    if (cssPath != null) {
      scene.getStylesheets().add(cssPath);
    }

    // Apply the persisted light/dark theme (MENU_DESIGN #6) now that the scene carries the base
    // theme — dark mode is an override layer added on top (DESIGN.md §8).
    mainController.applySavedTheme();

    // Set up stage
    primaryStage.setTitle(ui.i18n.L10n.t("app.title"));
    primaryStage.setScene(scene);
    ui.util.AppIcon.applyTo(primaryStage); // deerstalker in the title bar / taskbar

    // DESIGN.md §4: the scene reflows gracefully from ~1024×720 up to full-screen; below that
    // envelope nothing is supported, so the window cannot be dragged smaller.
    primaryStage.setMinWidth(1024);
    primaryStage.setMinHeight(720);

    // The MainController will now handle the shutdown logic.
    primaryStage.setOnCloseRequest(
        event -> {
          mainController.shutdown();
          event.consume(); // Prevent the window from closing immediately
        });

    // Show the stage
    primaryStage.show();

    logger.debug("========================================");
    logger.debug("  Detective Game JavaFX Client Started");
    logger.debug("========================================");
  }

  @Override
  public void stop() throws Exception {
    // Shutdown is now handled by the MainController's onCloseRequest logic.
    // This method is kept for lifecycle completeness but doesn't need to do
    // anything.
    super.stop();
  }
}
