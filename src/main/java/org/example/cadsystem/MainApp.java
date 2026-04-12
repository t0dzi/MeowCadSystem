package org.example.cadsystem;

import javafx.application.Application;
import javafx.beans.InvalidationListener;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.example.controller.CanvasController;
import org.example.model.AppSettings;
import org.example.model.CadModel;
import org.example.model.CameraModel;
import org.example.model.DrawingState;
import org.example.model.StyleManager;
import org.example.view.CanvasPainter;
import org.example.view.DrawingToolbar;
import org.example.view.SettingsView;
import org.example.view.ToolbarView;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("CadMeowSystem");

        CadModel model = new CadModel();
        AppSettings settings = new AppSettings();
        CameraModel camera = new CameraModel();
        DrawingState drawingState = new DrawingState();

        StyleManager styleManager = new StyleManager();
        model.setCurrentStyle(styleManager.getDefaultStyle());

        Canvas canvas = new Canvas(800, 600);

        Pane canvasContainer = new Pane(canvas);
        canvas.widthProperty().bind(canvasContainer.widthProperty());
        canvas.heightProperty().bind(canvasContainer.heightProperty());

        Label infoLabel = new Label("Готово");
        CanvasPainter painter = new CanvasPainter(canvas.getGraphicsContext2D(), model, settings, camera);
        painter.setDrawingState(drawingState);

        SettingsView settingsView = new SettingsView(model, settings, painter, styleManager);
        ToolbarView toolbarView = new ToolbarView(model, camera, painter, canvas, styleManager, primaryStage);
        DrawingToolbar drawingToolbar = new DrawingToolbar(drawingState);

        ScrollPane settingsScroll = new ScrollPane(settingsView.createView());
        settingsScroll.setFitToWidth(true);
        settingsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        settingsScroll.setStyle("-fx-background-color: transparent; -fx-background: #f0f0f0;");

        VBox topPanel = new VBox();
        topPanel.getChildren().addAll(
                drawingToolbar.createToolBar(),
                drawingToolbar.createOptionsPanel(),
                toolbarView.createToolBar());

        BorderPane root = new BorderPane();
        root.setCenter(canvasContainer);
        root.setTop(topPanel);
        root.setBottom(infoLabel);
        root.setRight(settingsScroll);

        CanvasController controller = new CanvasController(canvas, model, painter, infoLabel, camera, drawingState);

        setupListeners(painter, settings, camera, drawingState);

        InvalidationListener sizeListener = e -> painter.redrawAll();
        canvas.widthProperty().addListener(sizeListener);
        canvas.heightProperty().addListener(sizeListener);

        painter.redrawAll();

        Scene scene = new Scene(root, 1000, 700);

        scene.setOnKeyPressed(controller::onKeyPressed);

        primaryStage.setScene(scene);
        primaryStage.setMaximized(true);
        primaryStage.show();
    }

    private void setupListeners(CanvasPainter painter, AppSettings settings,
            CameraModel camera, DrawingState drawingState) {
        InvalidationListener redrawListener = e -> {
            if (!camera.isBatching()) {
                painter.redrawAll();
            }
        };

        settings.gridStepProperty().addListener(redrawListener);
        settings.backgroundColorProperty().addListener(redrawListener);
        settings.gridColorProperty().addListener(redrawListener);
        settings.segmentColorProperty().addListener(redrawListener);
        settings.angleUnitProperty().addListener(redrawListener);

        camera.xProperty().addListener(redrawListener);
        camera.yProperty().addListener(redrawListener);
        camera.scaleProperty().addListener(redrawListener);
        camera.angleProperty().addListener(redrawListener);

        drawingState.currentToolProperty().addListener(redrawListener);
        drawingState.polygonSidesProperty().addListener(redrawListener);
        drawingState.polygonTypeProperty().addListener(redrawListener);
        drawingState.rectangleCornerTypeProperty().addListener(redrawListener);
        drawingState.rectangleCornerRadiusProperty().addListener(redrawListener);
    }

    public static void main(String[] args) {
        launch(args);
    }
}

