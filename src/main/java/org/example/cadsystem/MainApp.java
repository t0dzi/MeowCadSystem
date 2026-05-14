package org.example.cadsystem;

import javafx.application.Application;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.InvalidationListener;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.example.export.DxfExporter;
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

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MainApp extends Application {

    private static final DateTimeFormatter AUTOSAVE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

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
        canvas.setFocusTraversable(true);

        Pane canvasContainer = new Pane(canvas);
        canvas.widthProperty().bind(canvasContainer.widthProperty());
        canvas.heightProperty().bind(canvasContainer.heightProperty());

        Label infoLabel = new Label("Готово");
        infoLabel.setWrapText(true);
        infoLabel.setMaxWidth(Double.MAX_VALUE);
        infoLabel.setStyle("-fx-padding: 8 12; -fx-font-size: 12px; -fx-background-color: #fafafa; -fx-border-color: #d0d0d0; -fx-border-width: 1 0 0 0;");
        CanvasPainter painter = new CanvasPainter(canvas.getGraphicsContext2D(), model, settings, camera);
        painter.setDrawingState(drawingState);

        SettingsView settingsView = new SettingsView(model, settings, painter, styleManager);
        ToolbarView toolbarView = new ToolbarView(model, camera, painter, canvas, styleManager, primaryStage);
        DrawingToolbar drawingToolbar = new DrawingToolbar(drawingState);

        ScrollPane settingsScroll = new ScrollPane(settingsView.createView());
        settingsScroll.setFitToWidth(true);
        settingsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        settingsScroll.setStyle("-fx-background-color: transparent; -fx-background: #f0f0f0;");
        settingsScroll.setPrefWidth(430);

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
        primaryStage.setOnCloseRequest(event -> {
            if (!confirmClose(primaryStage, model)) {
                event.consume();
            }
        });
        primaryStage.show();
        startAutoSave(model);
        canvas.requestFocus();
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

    private void startAutoSave(CadModel model) {
        Timeline timeline = new Timeline(new KeyFrame(Duration.minutes(15), event -> autoSave(model)));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void autoSave(CadModel model) {
        if (model.getPrimitives().isEmpty()) {
            return;
        }

        File directory = new File(System.getProperty("user.home"), ".cadsystem_autosave");
        if (!directory.exists() && !directory.mkdirs()) {
            return;
        }

        String fileName = "autosave_" + LocalDateTime.now().format(AUTOSAVE_FORMAT) + ".dxf";
        File file = new File(directory, fileName);
        try {
            saveDxf(model, file);
        } catch (IOException ignored) {
            // Автосохранение не должно прерывать работу пользователя.
        }
    }

    private boolean confirmClose(Stage stage, CadModel model) {
        if (!model.isDirty()) {
            return true;
        }

        ButtonType save = new ButtonType("Сохранить", ButtonBar.ButtonData.YES);
        ButtonType dontSave = new ButtonType("Не сохранять", ButtonBar.ButtonData.NO);
        ButtonType cancel = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "", save, dontSave, cancel);
        alert.setTitle("Закрытие чертежа");
        alert.setHeaderText("Сохранить изменения перед выходом?");
        alert.setContentText("Если не сохранить, последние изменения останутся только в автокопиях, если они успели создаться.");

        ButtonType result = alert.showAndWait().orElse(cancel);
        if (result == cancel) {
            return false;
        }
        if (result == save) {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Сохранить чертёж");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("DXF файл (*.dxf)", "*.dxf"),
                    new FileChooser.ExtensionFilter("Все файлы", "*.*"));
            fileChooser.setInitialFileName("drawing.dxf");
            File file = fileChooser.showSaveDialog(stage);
            if (file == null) {
                return false;
            }
            try {
                saveDxf(model, file);
                model.markSaved();
            } catch (IOException ex) {
                Alert error = new Alert(Alert.AlertType.ERROR);
                error.setTitle("Ошибка сохранения");
                error.setHeaderText("Не удалось сохранить файл");
                error.setContentText(ex.getMessage());
                error.showAndWait();
                return false;
            }
        }
        return true;
    }

    private void saveDxf(CadModel model, File file) throws IOException {
        DxfExporter exporter = new DxfExporter(
                DxfExporter.DxfVersion.R2007,
                DxfExporter.DxfUnits.MILLIMETERS);
        exporter.export(model, file);
    }
}
