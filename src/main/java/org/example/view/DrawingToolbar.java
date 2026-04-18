package org.example.view;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polyline;
import org.example.model.DrawingState;
import org.example.model.DrawingState.CreationMethod;
import org.example.model.DrawingState.Tool;
import org.example.model.Polygon;
import org.example.model.Rectangle;

public class DrawingToolbar {

    private final DrawingState drawingState;
    private ToggleGroup toolGroup;
    private Label methodLabel;
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
        ToggleButton dimensionBtn = createToolButton(Tool.DIMENSION);

        selectBtn.setSelected(true);

        methodLabel = new Label("РЎРїРѕСЃРѕР±:");

        methodComboBox = new ComboBox<>();
        methodComboBox.setPrefWidth(260);
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
            if (selected != null && selected != drawingState.getCreationMethod()) {
                drawingState.setCreationMethod(selected);
                updateInputPanel();
            }
        });

        drawingState.currentToolProperty().addListener((obs, oldTool, newTool) -> {
            updateMethodComboBox();
            updateOptionsPanel();
            updateInputPanel();
        });

        drawingState.creationMethodProperty().addListener((obs, oldMethod, newMethod) -> {
            if (newMethod != null && methodComboBox.getValue() != newMethod) {
                methodComboBox.setValue(newMethod);
            }
            updateOptionsPanel();
            updateInputPanel();
        });

        return new ToolBar(
                new Label("Рисование:"),
                selectBtn, 
                new Separator(Orientation.VERTICAL),
                segmentBtn, circleBtn, arcBtn, rectBtn, ellipseBtn, polygonBtn, splineBtn, dimensionBtn,
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
        
        if (tool == Tool.SELECT || tool == Tool.DIMENSION) {
            methodComboBox.setVisible(false);
            return;
        }
        
        var methods = drawingState.getAvailableCreationMethods();
        methodComboBox.getItems().setAll(methods);
        
        if (!methods.isEmpty()) {
            CreationMethod selectedMethod = drawingState.getCreationMethod();
            methodComboBox.setValue(methods.contains(selectedMethod) ? selectedMethod : methods.get(0));
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
            case DIMENSION -> {
                hasOptions = true;
                optionsPanel.getChildren().add(createDimensionOptions());
            }
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

    private VBox createDimensionOptions() {
        Label typeLabel = new Label("Тип размера:");

        ToggleGroup group = new ToggleGroup();
        HBox linearRow = new HBox(8,
                createDimensionMethodButton("Гориз.", CreationMethod.DIMENSION_LINEAR_HORIZONTAL, group),
                createDimensionMethodButton("Вертик.", CreationMethod.DIMENSION_LINEAR_VERTICAL, group),
                createDimensionMethodButton("Выровн.", CreationMethod.DIMENSION_LINEAR_ALIGNED, group));
        linearRow.setAlignment(Pos.CENTER_LEFT);

        HBox radialRow = new HBox(8,
                createDimensionMethodButton("Радиус", CreationMethod.DIMENSION_RADIUS, group),
                createDimensionMethodButton("Диаметр", CreationMethod.DIMENSION_DIAMETER, group),
                createDimensionMethodButton("Угол", CreationMethod.DIMENSION_ANGLE, group));
        radialRow.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(6, typeLabel, linearRow, radialRow);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private ToggleButton createDimensionMethodButton(String text, CreationMethod method, ToggleGroup group) {
        ToggleButton button = new ToggleButton(text);
        button.setToggleGroup(group);
        button.setSelected(drawingState.getCreationMethod() == method);
        button.setOnAction(e -> {
            if (!button.isSelected()) {
                button.setSelected(true);
                return;
            }
            if (drawingState.getCreationMethod() != method) {
                drawingState.setCreationMethod(method);
            }
        });
        return button;
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
        ToggleButton btn = new ToggleButton();
        btn.setToggleGroup(toolGroup);
        btn.setTooltip(new Tooltip(tool.getDisplayName()));
        btn.setPrefSize(42, 36);
        btn.setGraphic(createToolGraphic(tool));
        btn.setStyle("-fx-padding: 4;");
        
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

    private Node createToolGraphic(Tool tool) {
        Group icon = switch (tool) {
            case SELECT -> createSelectIcon();
            case SEGMENT -> createSegmentIcon();
            case CIRCLE -> createCircleIcon();
            case ARC -> createArcIcon();
            case RECTANGLE -> createRectangleIcon();
            case ELLIPSE -> createEllipseIcon();
            case POLYGON -> createPolygonIcon();
            case SPLINE -> createSplineIcon();
            case DIMENSION -> createDimensionIcon();
        };

        StackPane pane = new StackPane(icon);
        pane.setMinSize(22, 22);
        pane.setPrefSize(22, 22);
        pane.setMaxSize(22, 22);
        return pane;
    }

    private Group createSelectIcon() {
        Polyline arrow = new Polyline(
                5.0, 3.0,
                5.0, 18.0,
                9.0, 14.0,
                11.5, 20.0,
                14.0, 18.8,
                11.6, 12.8,
                17.0, 12.8
        );
        styleOutline(arrow);
        return new Group(arrow);
    }

    private Group createSegmentIcon() {
        Line line = new Line(4, 17, 18, 5);
        line.setStrokeWidth(2.2);
        line.setStroke(Color.web("#2f2f2f"));
        Circle p1 = new Circle(4, 17, 1.7, Color.web("#2f2f2f"));
        Circle p2 = new Circle(18, 5, 1.7, Color.web("#2f2f2f"));
        return new Group(line, p1, p2);
    }

    private Group createCircleIcon() {
        Circle circle = new Circle(11, 11, 7);
        styleOutline(circle);
        return new Group(circle);
    }

    private Group createArcIcon() {
        Arc arc = new Arc(11, 11, 7, 7, 25, 255);
        arc.setType(ArcType.OPEN);
        styleOutline(arc);
        Line tick1 = new Line(16.9, 7.3, 19.0, 5.4);
        Line tick2 = new Line(5.8, 17.2, 3.9, 19.0);
        tick1.setStrokeWidth(1.7);
        tick2.setStrokeWidth(1.7);
        tick1.setStroke(Color.web("#2f2f2f"));
        tick2.setStroke(Color.web("#2f2f2f"));
        return new Group(arc, tick1, tick2);
    }

    private Group createRectangleIcon() {
        javafx.scene.shape.Rectangle rect = new javafx.scene.shape.Rectangle(4.5, 5.5, 13, 11);
        styleOutline(rect);
        return new Group(rect);
    }

    private Group createEllipseIcon() {
        Ellipse ellipse = new Ellipse(11, 11, 7.5, 5.2);
        styleOutline(ellipse);
        return new Group(ellipse);
    }

    private Group createPolygonIcon() {
        javafx.scene.shape.Polygon polygon = new javafx.scene.shape.Polygon(
                11.0, 3.8,
                17.8, 8.0,
                15.4, 17.8,
                6.6, 17.8,
                4.2, 8.0
        );
        styleOutline(polygon);
        return new Group(polygon);
    }

    private Group createSplineIcon() {
        Polyline spline = new Polyline(
                3.5, 14.0,
                6.0, 8.0,
                9.5, 10.0,
                13.0, 5.2,
                18.5, 13.6
        );
        styleOutline(spline);
        Circle p1 = new Circle(3.5, 14.0, 1.2, Color.web("#2f2f2f"));
        Circle p2 = new Circle(9.5, 10.0, 1.2, Color.web("#2f2f2f"));
        Circle p3 = new Circle(18.5, 13.6, 1.2, Color.web("#2f2f2f"));
        return new Group(spline, p1, p2, p3);
    }

    private Group createDimensionIcon() {
        Line extLeft = new Line(5, 5, 5, 17);
        Line extRight = new Line(17, 5, 17, 17);
        Line dim = new Line(5, 11, 17, 11);
        Line arr1a = new Line(8, 9, 5, 11);
        Line arr1b = new Line(8, 13, 5, 11);
        Line arr2a = new Line(14, 9, 17, 11);
        Line arr2b = new Line(14, 13, 17, 11);
        Line textLine = new Line(9, 6.5, 13, 6.5);
        for (Line line : new Line[] { extLeft, extRight, dim, arr1a, arr1b, arr2a, arr2b, textLine }) {
            line.setStroke(Color.web("#2f2f2f"));
            line.setStrokeWidth(line == textLine ? 1.3 : 1.7);
        }
        return new Group(extLeft, extRight, dim, arr1a, arr1b, arr2a, arr2b, textLine);
    }

    private void styleOutline(javafx.scene.shape.Shape shape) {
        shape.setFill(Color.TRANSPARENT);
        shape.setStroke(Color.web("#2f2f2f"));
        shape.setStrokeWidth(1.8);
    }
}
