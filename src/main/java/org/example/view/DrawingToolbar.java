package org.example.view;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.example.model.DrawingState;
import org.example.model.DrawingState.CreationMethod;
import org.example.model.DrawingState.Tool;
import org.example.model.Polygon;
import org.example.model.Rectangle;

public class DrawingToolbar {

    private final DrawingState drawingState;
    private ToggleGroup toolGroup;
    private ComboBox<CreationMethod> methodComboBox;
    private VBox optionsPanel;
    private HBox inputPanel;

    public DrawingToolbar(DrawingState drawingState) {
        this.drawingState = drawingState;
    }

    public ToolBar createToolBar() {
        toolGroup = new ToggleGroup();

        ToggleButton selectBtn = createToolButton(Tool.SELECT);
        ToggleButton segmentBtn = createToolButton(Tool.SEGMENT);
        ToggleButton circleBtn = createToolButton(Tool.CIRCLE);
        ToggleButton arcBtn = createToolButton(Tool.ARC);
        ToggleButton rectBtn = createToolButton(Tool.RECTANGLE);
        ToggleButton ellipseBtn = createToolButton(Tool.ELLIPSE);
        ToggleButton polygonBtn = createToolButton(Tool.POLYGON);
        ToggleButton splineBtn = createToolButton(Tool.SPLINE);

        selectBtn.setSelected(true);

        methodComboBox = new ComboBox<>();
        methodComboBox.setPrefWidth(180);
        methodComboBox.setVisible(false);
        
        methodComboBox.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(CreationMethod item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getDisplayName());
            }
        });
        methodComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(CreationMethod item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getDisplayName());
            }
        });
        
        methodComboBox.setOnAction(e -> {
            CreationMethod selected = methodComboBox.getValue();
            if (selected != null) {
                drawingState.setCreationMethod(selected);
                updateInputPanel();
            }
        });

        drawingState.currentToolProperty().addListener((obs, oldTool, newTool) -> {
            updateMethodComboBox();
            updateOptionsPanel();
            updateInputPanel();
        });

        return new ToolBar(
                new Label("Рисование:"),
                selectBtn, 
                new Separator(Orientation.VERTICAL),
                segmentBtn, circleBtn, arcBtn, rectBtn, ellipseBtn, polygonBtn, splineBtn,
                new Separator(Orientation.VERTICAL),
                new Label("Способ:"),
                methodComboBox
        );
    }

    public VBox createOptionsPanel() {
        optionsPanel = new VBox(10);
        optionsPanel.setPadding(new Insets(10));
        optionsPanel.setStyle("-fx-background-color: #e8e8e8; -fx-border-color: #ccc; -fx-border-width: 0 0 1 0;");
        optionsPanel.setVisible(false);
        optionsPanel.setManaged(false);
        
        inputPanel = new HBox(15);
        inputPanel.setAlignment(Pos.CENTER_LEFT);
        inputPanel.setPadding(new Insets(5, 0, 5, 0));
        
        updateOptionsPanel();
        
        return optionsPanel;
    }

    private void updateMethodComboBox() {
        Tool tool = drawingState.getCurrentTool();
        
        if (tool == Tool.SELECT) {
            methodComboBox.setVisible(false);
            return;
        }
        
        var methods = drawingState.getAvailableCreationMethods();
        methodComboBox.getItems().setAll(methods);
        
        if (!methods.isEmpty()) {
            methodComboBox.setValue(methods.get(0));
            methodComboBox.setVisible(true);
        } else {
            methodComboBox.setVisible(false);
        }
    }

    private void updateOptionsPanel() {
        if (optionsPanel == null) return;
        
        optionsPanel.getChildren().clear();
        Tool tool = drawingState.getCurrentTool();
        
        boolean hasOptions = false;
        
        updateInputPanel();
        if (!inputPanel.getChildren().isEmpty()) {
            optionsPanel.getChildren().add(inputPanel);
            hasOptions = true;
        }
        
        switch (tool) {
            case POLYGON -> {
                hasOptions = true;
                optionsPanel.getChildren().add(createPolygonOptions());
            }
            case RECTANGLE -> {
                hasOptions = true;
                optionsPanel.getChildren().add(createRectangleOptions());
            }
            default -> {}
        }
        
        optionsPanel.setVisible(hasOptions);
        optionsPanel.setManaged(hasOptions);
    }

    private void updateInputPanel() {
        if (inputPanel == null) return;
        inputPanel.getChildren().clear();
        
        Tool tool = drawingState.getCurrentTool();
        CreationMethod method = drawingState.getCreationMethod();
        
        if (tool == Tool.SELECT || method == null) return;
        
        switch (tool) {
            case SEGMENT -> {
                inputPanel.getChildren().addAll(
                    createLabeledSpinner("Длина:", drawingState.inputLengthProperty(), 0, 10000, 100),
                    createLabeledSpinner("Угол (°):", drawingState.inputAngleProperty(), -360, 360, 45)
                );
            }
            case CIRCLE -> {
                // Показываем и радиус, и диаметр (они синхронизированы)
                inputPanel.getChildren().addAll(
                    createLabeledSpinner("Радиус:", drawingState.inputRadiusProperty(), 0, 10000, 50),
                    createLabeledSpinner("Диаметр:", drawingState.inputDiameterProperty(), 0, 20000, 100)
                );
            }
            case ELLIPSE -> {
                inputPanel.getChildren().addAll(
                    createLabeledSpinner("Большая ось:", drawingState.inputSemiMajorAxisProperty(), 0, 10000, 100),
                    createLabeledSpinner("Малая ось:", drawingState.inputSemiMinorAxisProperty(), 0, 10000, 50)
                );
            }
            case RECTANGLE -> {
                inputPanel.getChildren().addAll(
                    createLabeledSpinner("Ширина:", drawingState.inputWidthProperty(), 0, 10000, 100),
                    createLabeledSpinner("Высота:", drawingState.inputHeightProperty(), 0, 10000, 100)
                );
            }
            case POLYGON -> {
                inputPanel.getChildren().add(
                    createLabeledSpinner("Радиус:", drawingState.inputRadiusProperty(), 0, 10000, 50)
                );
            }
            default -> {}
        }
        
        if (!inputPanel.getChildren().isEmpty()) {
            Button applyBtn = new Button("Применить");
            applyBtn.setOnAction(e -> drawingState.setUseManualInput(true));
            inputPanel.getChildren().add(applyBtn);
        }
    }

    private HBox createLabeledSpinner(String label, javafx.beans.property.DoubleProperty property, 
                                       double min, double max, double initial) {
        Label lbl = new Label(label);
        Spinner<Double> spinner = new Spinner<>(min, max, property.get() > 0 ? property.get() : initial, 1.0);
        spinner.setEditable(true);
        spinner.setPrefWidth(100);
        
        spinner.getValueFactory().valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) property.set(newVal);
        });
        property.addListener((obs, oldVal, newVal) -> {
            spinner.getValueFactory().setValue(newVal.doubleValue());
        });
        
        HBox box = new HBox(5, lbl, spinner);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private HBox createPolygonOptions() {
        Label sidesLabel = new Label("Кол-во сторон:");
        
        Spinner<Integer> sidesSpinner = new Spinner<>(3, 50, drawingState.getPolygonSides());
        sidesSpinner.setEditable(true);
        sidesSpinner.setPrefWidth(70);
        sidesSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            drawingState.setPolygonSides(newVal);
        });
        
        Label typeLabel = new Label("Тип:");
        
        ComboBox<Polygon.InscriptionType> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll(Polygon.InscriptionType.values());
        typeCombo.setValue(drawingState.getPolygonType());
        typeCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Polygon.InscriptionType item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getDisplayName());
            }
        });
        typeCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Polygon.InscriptionType item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getDisplayName());
            }
        });
        typeCombo.setOnAction(e -> drawingState.setPolygonType(typeCombo.getValue()));
        
        Label hintLabel = new Label("(Вписанный - вершины на окружности, Описанный - стороны касаются)");
        hintLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #666;");
        
        HBox box = new HBox(10, sidesLabel, sidesSpinner, typeLabel, typeCombo, hintLabel);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private HBox createRectangleOptions() {
        Label cornerLabel = new Label("Тип углов:");
        
        ComboBox<Rectangle.CornerType> cornerTypeCombo = new ComboBox<>();
        cornerTypeCombo.getItems().addAll(Rectangle.CornerType.values());
        cornerTypeCombo.setValue(drawingState.getRectangleCornerType());
        cornerTypeCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Rectangle.CornerType item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(switch (item) {
                        case SHARP -> "Острые";
                        case ROUNDED -> "Скруглённые";
                        case CHAMFERED -> "Фаски";
                    });
                }
            }
        });
        cornerTypeCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Rectangle.CornerType item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(switch (item) {
                        case SHARP -> "Острые";
                        case ROUNDED -> "Скруглённые";
                        case CHAMFERED -> "Фаски";
                    });
                }
            }
        });
        cornerTypeCombo.setOnAction(e -> drawingState.setRectangleCornerType(cornerTypeCombo.getValue()));
        
        Label radiusLabel = new Label("Радиус:");
        
        Spinner<Double> radiusSpinner = new Spinner<>(0.0, 1000.0, drawingState.getRectangleCornerRadius(), 5.0);
        radiusSpinner.setEditable(true);
        radiusSpinner.setPrefWidth(80);
        radiusSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            drawingState.setRectangleCornerRadius(newVal);
        });
        
        radiusLabel.visibleProperty().bind(
            cornerTypeCombo.valueProperty().isNotEqualTo(Rectangle.CornerType.SHARP)
        );
        radiusSpinner.visibleProperty().bind(
            cornerTypeCombo.valueProperty().isNotEqualTo(Rectangle.CornerType.SHARP)
        );
        
        HBox box = new HBox(10, cornerLabel, cornerTypeCombo, radiusLabel, radiusSpinner);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private ToggleButton createToolButton(Tool tool) {
        ToggleButton btn = new ToggleButton(tool.getIcon());
        btn.setToggleGroup(toolGroup);
        btn.setTooltip(new Tooltip(tool.getDisplayName()));
        btn.setPrefSize(36, 36);
        btn.setStyle("-fx-font-size: 16px;");
        
        btn.setOnAction(e -> {
            if (btn.isSelected()) {
                drawingState.setCurrentTool(tool);
            } else {
                btn.setSelected(true);
            }
        });
        
        drawingState.currentToolProperty().addListener((obs, oldTool, newTool) -> {
            btn.setSelected(newTool == tool);
        });
        
        return btn;
    }
}

