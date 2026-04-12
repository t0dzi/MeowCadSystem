package org.example.view;

import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.example.export.DxfExporter;
import org.example.export.DxfImporter;
import org.example.model.*;

import java.io.File;

/*
 * Верхняя панель инструментов. Содержит кнопки для сохранения, импорта, удаления, 
 * выбора стиля, редактирования стилей, зума, панорамирования, вращения и привязок.
 */

public class ToolbarView {

    private final CadModel model;
    private final CameraModel camera;
    private final CanvasPainter painter;
    private final Canvas canvas;
    private final Stage stage;
    private final StyleManager styleManager;

    private HBox zigzagOptionsBox;
    private Spinner<Integer> zigzagCountSpinner;

    private MenuButton snapMenuButton;

    public ToolbarView(CadModel model, CameraModel camera, CanvasPainter painter, Canvas canvas,
            StyleManager styleManager, Stage stage) {
        this.model = model;
        this.camera = camera;
        this.painter = painter;
        this.canvas = canvas;
        this.styleManager = styleManager;
        this.stage = stage;
    }

    public ToolBar createToolBar() {
        Button saveButton = new Button("💾 Сохранить");
        saveButton.setTooltip(new Tooltip("Сохранить чертёж в DXF"));
        saveButton.setOnAction(e -> showSaveDialog());

        Button importButton = new Button("📂 Импорт");
        importButton.setTooltip(new Tooltip("Импортировать чертёж из DXF"));
        importButton.setOnAction(e -> showImportDialog());

        Button deleteButton = new Button("Удалить");
        deleteButton.setOnAction(e -> {
            Primitive selected = model.getSelectedPrimitive();
            if (selected != null) {
                model.removePrimitive(selected);
                model.setSelectedPrimitive(null);
                painter.redrawAll();
            }
        });

        Button deleteAllButton = new Button("Удалить всё");
        deleteAllButton.setOnAction(e -> {
            model.clearAll();
            painter.redrawAll();
        });

        ComboBox<LineStyle> styleComboBox = new ComboBox<>(styleManager.getStyles());
        styleComboBox.setPrefWidth(200);
        styleComboBox.setCellFactory(lv -> new StyleListCell());
        styleComboBox.setButtonCell(new StyleListCell());

        zigzagOptionsBox = createZigzagOptionsBox();
        zigzagOptionsBox.setVisible(false);
        zigzagOptionsBox.setManaged(false);

        styleComboBox.valueProperty().bindBidirectional(model.currentStyleProperty());

        styleComboBox.setOnAction(e -> {
            LineStyle selectedStyle = styleComboBox.getValue();

            updateZigzagOptionsVisibility(selectedStyle);

            Primitive selected = model.getSelectedPrimitive();
            if (selected != null && selectedStyle != null && selected.getLineStyle() != selectedStyle) {
                selected.setLineStyle(selectedStyle);
                painter.redrawAll();
            }
        });

        model.selectedPrimitiveProperty().addListener((obs, oldPrim, newPrim) -> {
            if (newPrim != null && newPrim.getLineStyle() != null) {
                styleComboBox.setValue(newPrim.getLineStyle());
                updateZigzagOptionsVisibility(newPrim.getLineStyle());
            }
        });

        if (model.getCurrentStyle() == null && !styleManager.getStyles().isEmpty()) {
            styleComboBox.getSelectionModel().selectFirst();
        }

        updateZigzagOptionsVisibility(model.getCurrentStyle());

        Button editStylesBtn = new Button("⚙");
        editStylesBtn.setTooltip(new Tooltip("Редактор стилей"));
        editStylesBtn.setOnAction(e -> {
            StyleEditorDialog dialog = new StyleEditorDialog(styleManager);
            dialog.setOnUpdate(painter::redrawAll);
            dialog.show();
        });

        Button zoomInButton = new Button("Лупа +");
        zoomInButton.setOnAction(e -> zoomAroundCenter(1.2));

        Button zoomOutButton = new Button("Лупа -");
        zoomOutButton.setOnAction(e -> zoomAroundCenter(1 / 1.2));

        Button fitAllButton = new Button("Показать всё");
        fitAllButton.setOnAction(e -> fitAllContent());

        Button rotateLeftBtn = new Button("⟲");
        rotateLeftBtn.setOnAction(e -> rotateAroundCenter(15));

        Button rotateRightBtn = new Button("⟳");
        rotateRightBtn.setOnAction(e -> rotateAroundCenter(-15));

        Button resetViewBtn = new Button("Сброс вида");
        resetViewBtn.setOnAction(e -> camera.reset());

        HBox snapBox = createSnapPanel();

        return new ToolBar(
                saveButton, importButton,
                new Separator(),
                deleteButton, deleteAllButton,
                new Separator(),
                styleComboBox, editStylesBtn, zigzagOptionsBox,
                new Separator(),
                zoomInButton, zoomOutButton, fitAllButton,
                new Separator(),
                rotateLeftBtn, rotateRightBtn, resetViewBtn,
                new Separator(),
                snapBox);
    }

    private HBox createSnapPanel() {
        SnapManager snapManager = model.getSnapManager();

        ToggleButton snapToggle = new ToggleButton("🎯 Привязки");
        snapToggle.setSelected(snapManager.isSnapEnabled());
        snapToggle.setTooltip(new Tooltip("Включить/выключить привязки (F3)"));
        snapToggle.selectedProperty().bindBidirectional(snapManager.snapEnabledProperty());

        snapMenuButton = new MenuButton("▼");
        snapMenuButton.setTooltip(new Tooltip("Настройки привязок"));

        CheckMenuItem endpointItem = createSnapMenuItem("Конец", SnapType.ENDPOINT, snapManager);
        CheckMenuItem midpointItem = createSnapMenuItem("Середина", SnapType.MIDPOINT, snapManager);
        CheckMenuItem centerItem = createSnapMenuItem("Центр", SnapType.CENTER, snapManager);

        CheckMenuItem intersectionItem = createSnapMenuItem("Пересечение", SnapType.INTERSECTION, snapManager);
        CheckMenuItem perpendicularItem = createSnapMenuItem("Перпендикуляр", SnapType.PERPENDICULAR, snapManager);
        CheckMenuItem tangentItem = createSnapMenuItem("Касательная", SnapType.TANGENT, snapManager);

        snapMenuButton.getItems().addAll(
                new MenuItem("— Обязательные —"),
                endpointItem, midpointItem, centerItem,
                new SeparatorMenuItem(),
                new MenuItem("— Дополнительные —"),
                intersectionItem, perpendicularItem, tangentItem);

        snapMenuButton.getItems().get(0).setDisable(true);
        snapMenuButton.getItems().get(5).setDisable(true);

        HBox box = new HBox(3, snapToggle, snapMenuButton);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private CheckMenuItem createSnapMenuItem(String name, SnapType type, SnapManager snapManager) {
        CheckMenuItem item = new CheckMenuItem(type.getSymbol() + " " + name);
        item.setSelected(snapManager.isSnapTypeEnabled(type));
        item.setOnAction(e -> {
            snapManager.enableSnapType(type, item.isSelected());
            painter.redrawAll();
        });
        return item;
    }

    private HBox createZigzagOptionsBox() {
        Label label = new Label("Кол-во:");

        zigzagCountSpinner = new Spinner<>(1, 20, 3);
        zigzagCountSpinner.setEditable(true);
        zigzagCountSpinner.setPrefWidth(70);
        zigzagCountSpinner.setTooltip(new Tooltip("Количество изломов (зигзагов)"));

        zigzagCountSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                updateZigzagCount(newVal);
            }
        });

        HBox box = new HBox(5, label, zigzagCountSpinner);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private void updateZigzagOptionsVisibility(LineStyle style) {
        boolean isZigzag = style != null && style.getType() == LineType.ZIGZAG;
        zigzagOptionsBox.setVisible(isZigzag);
        zigzagOptionsBox.setManaged(isZigzag);

        if (isZigzag) {
            double[] params = style.getDashPattern();
            int count = 3; // По умолчанию
            if (params != null && params.length >= 3) {
                count = (int) params[2];
            }
            zigzagCountSpinner.getValueFactory().setValue(count);
        }
    }

    private void updateZigzagCount(int count) {
        LineStyle currentStyle = model.getCurrentStyle();
        if (currentStyle != null && currentStyle.getType() == LineType.ZIGZAG) {
            double[] params = currentStyle.getDashPattern();
            double height = 12.0;
            double width = 10.0;

            if (params != null && params.length >= 2) {
                height = params[0];
                width = params[1];
            }

            // Обновляем паттерн с новым количеством
            currentStyle.setDashPattern(new double[] { height, width, count });
            painter.redrawAll();
        }
    }

    private void zoomAroundCenter(double factor) {
        double centerX = canvas.getWidth() / 2;
        double centerY = canvas.getHeight() / 2;
        Point centerWorld = painter.toWorld(centerX, centerY);

        double newScale = camera.getScale() * factor;
        if (newScale < 0.05)
            newScale = 0.05;
        if (newScale > 50.0)
            newScale = 50.0;

        camera.beginUpdate();
        camera.setScale(newScale);

        double rad = Math.toRadians(camera.getAngle());
        double rotX = centerWorld.getX() * Math.cos(rad) - centerWorld.getY() * Math.sin(rad);
        double rotY = centerWorld.getX() * Math.sin(rad) + centerWorld.getY() * Math.cos(rad);

        camera.setX(-rotX * newScale);
        camera.setY(-rotY * newScale);
        camera.endUpdate();
        painter.redrawAll();
    }

    private void rotateAroundCenter(double angleDelta) {
        double centerX = canvas.getWidth() / 2;
        double centerY = canvas.getHeight() / 2;
        Point worldCenter = painter.toWorld(centerX, centerY);

        camera.beginUpdate();
        camera.setAngle(camera.getAngle() + angleDelta);

        double rad = Math.toRadians(camera.getAngle());
        double s = camera.getScale();

        double rotX = worldCenter.getX() * Math.cos(rad) - worldCenter.getY() * Math.sin(rad);
        double rotY = worldCenter.getX() * Math.sin(rad) + worldCenter.getY() * Math.cos(rad);

        camera.setX(-rotX * s);
        camera.setY(-rotY * s);
        camera.endUpdate();
        painter.redrawAll();
    }

    private void fitAllContent() {
        if (model.getPrimitives().isEmpty()) {
            camera.reset();
            return;
        }

        double rad = Math.toRadians(camera.getAngle());
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);

        double minRx = Double.MAX_VALUE, maxRx = -Double.MAX_VALUE;
        double minRy = Double.MAX_VALUE, maxRy = -Double.MAX_VALUE;

        // Используем bounding box каждого примитива
        for (Primitive p : model.getPrimitives()) {
            double[] bbox = p.getBoundingBox();

            // Четыре угла bounding box
            double[][] corners = {
                    { bbox[0], bbox[1] },
                    { bbox[2], bbox[1] },
                    { bbox[2], bbox[3] },
                    { bbox[0], bbox[3] }
            };

            for (double[] corner : corners) {
                double rx = corner[0] * cos - corner[1] * sin;
                double ry = corner[0] * sin + corner[1] * cos;

                minRx = Math.min(minRx, rx);
                maxRx = Math.max(maxRx, rx);
                minRy = Math.min(minRy, ry);
                maxRy = Math.max(maxRy, ry);
            }
        }

        double contentWidth = maxRx - minRx;
        double contentHeight = maxRy - minRy;
        double centerRx = (minRx + maxRx) / 2.0;
        double centerRy = (minRy + maxRy) / 2.0;

        if (contentWidth < 1)
            contentWidth = 100;
        if (contentHeight < 1)
            contentHeight = 100;

        double padding = 1.1;
        double scaleX = canvas.getWidth() / (contentWidth * padding);
        double scaleY = canvas.getHeight() / (contentHeight * padding);
        double newScale = Math.min(scaleX, scaleY);

        camera.setScale(newScale);
        camera.setX(-centerRx * newScale);
        camera.setY(-centerRy * newScale);
        painter.redrawAll();
    }

    /**
     * Показывает диалог сохранения файла с выбором формата DXF.
     */
    private void showSaveDialog() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Сохранить чертёж");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("DXF файл (*.dxf)", "*.dxf"),
                new FileChooser.ExtensionFilter("Все файлы", "*.*"));
        fileChooser.setInitialFileName("drawing.dxf");

        File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            try {
                DxfExporter exporter = new DxfExporter(
                        DxfExporter.DxfVersion.R12,
                        DxfExporter.DxfUnits.MILLIMETERS);
                exporter.export(model, file);

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Сохранение");
                alert.setHeaderText(null);
                alert.setContentText("Чертёж сохранён: " + file.getName());
                alert.showAndWait();
            } catch (Exception ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Ошибка сохранения");
                alert.setHeaderText("Не удалось сохранить файл");
                alert.setContentText(ex.getMessage());
                alert.showAndWait();
            }
        }
    }

    /**
     * Показывает диалог импорта DXF-файла.
     */
    private void showImportDialog() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Импортировать чертёж");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("DXF файл (*.dxf)", "*.dxf"),
                new FileChooser.ExtensionFilter("Все файлы", "*.*"));

        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            try {
                DxfImporter importer = new DxfImporter(styleManager);
                DxfImporter.ImportResult importResult = importer.importFile(file);

                for (Layer layer : importResult.getLayers()) {
                    model.addLayer(layer);
                }

                // Добавляем импортированные примитивы, сохраняя их слой из DXF
                for (Primitive p : importResult.getPrimitives()) {
                    model.addImportedPrimitive(p);
                }

                painter.redrawAll();
                fitAllContent();

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Импорт");
                alert.setHeaderText(null);
                String msg = String.format(
                        "Импортировано: %d объектов, %d слоёв",
                        importResult.getTotalImported(),
                        importResult.getLayers().size());
                if (importResult.getSkippedEntities() > 0) {
                    msg += String.format("\nПропущено: %d (неподдерживаемые типы)",
                            importResult.getSkippedEntities());
                }
                java.util.Map<String, Long> typeCounts = importResult.getPrimitives().stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                p -> p.getType().getDisplayName(),
                                java.util.stream.Collectors.counting()));
                if (!typeCounts.isEmpty()) {
                    msg += "\n\nПо типам:";
                    for (var entry : typeCounts.entrySet()) {
                        msg += "\n  " + entry.getKey() + ": " + entry.getValue();
                    }
                }
                alert.setContentText(msg);
                alert.showAndWait();
            } catch (Exception ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Ошибка импорта");
                alert.setHeaderText("Не удалось импортировать файл");
                alert.setContentText(ex.getMessage());
                alert.showAndWait();
            }
        }
    }
}

