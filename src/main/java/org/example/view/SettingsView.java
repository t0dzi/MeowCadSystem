package org.example.view;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.util.converter.NumberStringConverter;
import org.example.model.*;


public class SettingsView {

    private final CadModel model;
    private final AppSettings settings;
    private final CanvasPainter painter;
    private final StyleManager styleManager;

    private VBox editPropertiesContainer;

    public SettingsView(CadModel model, AppSettings settings, CanvasPainter painter, StyleManager styleManager) {
        this.model = model;
        this.settings = settings;
        this.painter = painter;
        this.styleManager = styleManager;
    }

    public VBox createView() {
        VBox inputSection = createInputSection();
        VBox displaySection = createDisplaySection();
        VBox propertySection = createPropertySectionV2();
        VBox editSection = createEditSection();

        VBox mainPanel = new VBox(15,
                editSection, new Separator(),
                propertySection, new Separator(),
                inputSection, new Separator(),
                displaySection);
        mainPanel.setPadding(new Insets(10));
        mainPanel.setStyle("-fx-background-color: #f0f0f0;");
        mainPanel.setPrefWidth(400);
        mainPanel.setMinWidth(400);

        return mainPanel;
    }

    private VBox createEditSection() {
        Label header = new Label("Редактирование");
        header.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        Label typeLabel = new Label("Выберите примитив для редактирования");
        typeLabel.setStyle("-fx-font-style: italic;");

        editPropertiesContainer = new VBox(8);
        editPropertiesContainer.setStyle("-fx-padding: 5 0 0 0;");

        model.selectedPrimitiveProperty().addListener((obs, oldPrim, newPrim) -> {
            editPropertiesContainer.getChildren().clear();

            if (newPrim != null) {
                typeLabel.setText("▶ " + newPrim.getType().getDisplayName());
                typeLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2196F3;");
                buildEditControls(newPrim);
            } else {
                typeLabel.setText("Выберите примитив для редактирования");
                typeLabel.setStyle("-fx-font-style: italic;");
            }
        });

        return new VBox(8, header, typeLabel, editPropertiesContainer);
    }

    private void buildEditControls(Primitive primitive) {
        switch (primitive.getType()) {
            case POINT -> { }
            case SEGMENT -> buildSegmentControls((Segment) primitive);
            case CIRCLE -> buildCircleControls((Circle) primitive);
            case ARC -> buildArcControls((Arc) primitive);
            case RECTANGLE -> buildRectangleControls((Rectangle) primitive);
            case ELLIPSE -> buildEllipseControls((Ellipse) primitive);
            case POLYGON -> buildPolygonControls((Polygon) primitive);
            case SPLINE -> buildSplineControls((Spline) primitive);
            case LINEAR_DIMENSION, RADIAL_DIMENSION, ANGULAR_DIMENSION -> buildDimensionControlsV5((DimensionPrimitive) primitive);
        }

        buildLineStyleControls(primitive);
    }

    private void buildLineStyleControls(Primitive primitive) {
        LineStyle style = primitive.getLineStyle();
        if (style == null)
            return;

        LineType type = style.getType();
        if (type != LineType.WAVY && type != LineType.ZIGZAG)
            return;

        Separator sep = new Separator();
        sep.setPadding(new Insets(5, 0, 5, 0));

        Label header = new Label("Параметры линии");
        header.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        GridPane grid = createGrid();
        int row = 0;

        if (type == LineType.WAVY) {
            TextField amplitudeField = createNumberField(style.getWaveAmplitude());
            TextField wavelengthField = createNumberField(style.getWaveLength());

            grid.add(new Label("Высота волны:"), 0, row);
            grid.add(amplitudeField, 1, row++);
            grid.add(new Label("Длина волны:"), 0, row);
            grid.add(wavelengthField, 1, row++);

            Button applyBtn = createApplyButton("Применить стиль", () -> {
                try {
                    style.setWaveAmplitude(parseDouble(amplitudeField));
                    style.setWaveLength(parseDouble(wavelengthField));
                    painter.redrawAll();
                } catch (NumberFormatException e) {
                    showError("Некорректные значения");
                }
            });

            editPropertiesContainer.getChildren().addAll(sep, header, grid, applyBtn);

        } else if (type == LineType.ZIGZAG) {
            double[] params = style.getDashPattern();
            double zigHeight = (params != null && params.length >= 1) ? params[0] : 8.0;
            double zigWidth = (params != null && params.length >= 2) ? params[1] : 16.0;
            int zigCount = (params != null && params.length >= 3) ? (int) params[2] : 3;

            TextField heightField = createNumberField(zigHeight);
            TextField widthField = createNumberField(zigWidth);
            Spinner<Integer> countSpinner = new Spinner<>(1, 50, zigCount);
            countSpinner.setEditable(true);
            countSpinner.setMaxWidth(Double.MAX_VALUE);

            grid.add(new Label("Высота зигзага:"), 0, row);
            grid.add(heightField, 1, row++);
            grid.add(new Label("Ширина зигзага:"), 0, row);
            grid.add(widthField, 1, row++);
            grid.add(new Label("Кол-во зигзагов:"), 0, row);
            grid.add(countSpinner, 1, row++);

            Button applyBtn = createApplyButton("Применить стиль", () -> {
                try {
                    double h = parseDouble(heightField);
                    double w = parseDouble(widthField);
                    int c = countSpinner.getValue();
                    style.setDashPattern(new double[] { h, w, c });
                    painter.redrawAll();
                } catch (NumberFormatException e) {
                    showError("Некорректные значения");
                }
            });

            editPropertiesContainer.getChildren().addAll(sep, header, grid, applyBtn);
        }
    }

    private void buildSegmentControls(Segment segment) {
        GridPane grid = createGrid();

        TextField x1 = createNumberField(segment.getStartPoint().getX());
        TextField y1 = createNumberField(segment.getStartPoint().getY());
        TextField x2 = createNumberField(segment.getEndPoint().getX());
        TextField y2 = createNumberField(segment.getEndPoint().getY());

        grid.add(new Label("Начало X:"), 0, 0);
        grid.add(x1, 1, 0);
        grid.add(new Label("Начало Y:"), 0, 1);
        grid.add(y1, 1, 1);
        grid.add(new Label("Конец X:"), 0, 2);
        grid.add(x2, 1, 2);
        grid.add(new Label("Конец Y:"), 0, 3);
        grid.add(y2, 1, 3);

        Label lengthLabel = new Label();
        Label angleLabel = new Label();
        updateSegmentInfo(segment, lengthLabel, angleLabel);

        grid.add(new Label("Длина:"), 0, 4);
        grid.add(lengthLabel, 1, 4);
        grid.add(new Label("Угол:"), 0, 5);
        grid.add(angleLabel, 1, 5);

        Button applyBtn = createApplyButton("Применить", () -> {
            try {
                // Пересоздаём отрезок с новыми координатами через контрольные точки
                segment.moveControlPoint(0, new Point(parseDouble(x1), parseDouble(y1)));
                segment.moveControlPoint(1, new Point(parseDouble(x2), parseDouble(y2)));
                updateSegmentInfo(segment, lengthLabel, angleLabel);
                painter.redrawAll();
            } catch (NumberFormatException e) {
                showError("Некорректные значения");
            }
        });

        editPropertiesContainer.getChildren().addAll(grid, applyBtn);
    }

    private void updateSegmentInfo(Segment segment, Label lengthLabel, Label angleLabel) {
        double dx = segment.getEndPoint().getX() - segment.getStartPoint().getX();
        double dy = segment.getEndPoint().getY() - segment.getStartPoint().getY();
        double length = Math.sqrt(dx * dx + dy * dy);
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        lengthLabel.setText(String.format("%.2f", length));
        angleLabel.setText(String.format("%.2f°", angle));
    }

    private void buildCircleControls(Circle circle) {
        GridPane grid = createGrid();

        TextField cx = createNumberField(circle.getCenter().getX());
        TextField cy = createNumberField(circle.getCenter().getY());
        TextField radiusField = createNumberField(circle.getRadius());

        // Переключатель Радиус/Диаметр
        ToggleButton radiusDiameterToggle = new ToggleButton("Радиус");
        radiusDiameterToggle.setMaxWidth(Double.MAX_VALUE);
        radiusDiameterToggle.setStyle("-fx-font-size: 11px;");

        Label radiusLabel = new Label("Радиус:");

        radiusDiameterToggle.setOnAction(e -> {
            boolean isDiameter = radiusDiameterToggle.isSelected();
            radiusDiameterToggle.setText(isDiameter ? "Диаметр" : "Радиус");
            radiusLabel.setText(isDiameter ? "Диаметр:" : "Радиус:");

            double currentValue = parseDouble(radiusField);
            if (isDiameter) {
                radiusField.setText(formatNumber(currentValue * 2));
            } else {
                radiusField.setText(formatNumber(currentValue / 2));
            }
        });

        grid.add(new Label("Центр X:"), 0, 0);
        grid.add(cx, 1, 0);
        grid.add(new Label("Центр Y:"), 0, 1);
        grid.add(cy, 1, 1);
        grid.add(radiusLabel, 0, 2);
        grid.add(radiusField, 1, 2);
        grid.add(new Label("Режим:"), 0, 3);
        grid.add(radiusDiameterToggle, 1, 3);

        Button applyBtn = createApplyButton("Применить", () -> {
            try {
                circle.setCenter(new Point(parseDouble(cx), parseDouble(cy)));
                double value = parseDouble(radiusField);
                if (radiusDiameterToggle.isSelected()) {
                    circle.setRadius(value / 2);
                } else {
                    circle.setRadius(value);
                }
                painter.redrawAll();
            } catch (NumberFormatException e) {
                showError("Некорректные значения");
            }
        });

        editPropertiesContainer.getChildren().addAll(grid, applyBtn);
    }

    private void buildArcControls(Arc arc) {
        GridPane grid = createGrid();

        TextField cx = createNumberField(arc.getCenter().getX());
        TextField cy = createNumberField(arc.getCenter().getY());
        TextField radius = createNumberField(arc.getRadius());
        TextField startAngle = createNumberField(Math.toDegrees(arc.getStartAngle()));
        double endAngleDeg = Math.toDegrees(arc.getStartAngle() + arc.getSweepAngle());
        TextField endAngle = createNumberField(endAngleDeg);

        grid.add(new Label("Центр X:"), 0, 0);
        grid.add(cx, 1, 0);
        grid.add(new Label("Центр Y:"), 0, 1);
        grid.add(cy, 1, 1);
        grid.add(new Label("Радиус:"), 0, 2);
        grid.add(radius, 1, 2);
        grid.add(new Label("Нач. угол (°):"), 0, 3);
        grid.add(startAngle, 1, 3);
        grid.add(new Label("Кон. угол (°):"), 0, 4);
        grid.add(endAngle, 1, 4);

        // Длина дуги
        Label arcLengthLabel = new Label(String.format("%.2f", arc.getArcLength()));
        grid.add(new Label("Длина дуги:"), 0, 5);
        grid.add(arcLengthLabel, 1, 5);

        Button applyBtn = createApplyButton("Применить", () -> {
            try {
                arc.setCenter(new Point(parseDouble(cx), parseDouble(cy)));
                arc.setRadius(parseDouble(radius));
                arc.setStartAngleDegrees(parseDouble(startAngle));
                arc.setEndAngleDegrees(parseDouble(endAngle));
                arcLengthLabel.setText(String.format("%.2f", arc.getArcLength()));
                painter.redrawAll();
            } catch (NumberFormatException e) {
                showError("Некорректные значения");
            }
        });

        editPropertiesContainer.getChildren().addAll(grid, applyBtn);
    }

    private void buildRectangleControls(Rectangle rect) {
        GridPane grid = createGrid();

        TextField cx = createNumberField(rect.getCenter().getX());
        TextField cy = createNumberField(rect.getCenter().getY());
        TextField width = createNumberField(rect.getWidth());
        TextField height = createNumberField(rect.getHeight());
        TextField cornerRadius = createNumberField(rect.getCornerRadius());

        ComboBox<Rectangle.CornerType> cornerTypeCombo = new ComboBox<>();
        cornerTypeCombo.getItems().addAll(Rectangle.CornerType.values());
        cornerTypeCombo.setValue(rect.getCornerType());
        cornerTypeCombo.setMaxWidth(Double.MAX_VALUE);

        grid.add(new Label("Центр X:"), 0, 0);
        grid.add(cx, 1, 0);
        grid.add(new Label("Центр Y:"), 0, 1);
        grid.add(cy, 1, 1);
        grid.add(new Label("Ширина:"), 0, 2);
        grid.add(width, 1, 2);
        grid.add(new Label("Высота:"), 0, 3);
        grid.add(height, 1, 3);
        grid.add(new Label("Тип углов:"), 0, 4);
        grid.add(cornerTypeCombo, 1, 4);
        grid.add(new Label("Радиус/Фаска:"), 0, 5);
        grid.add(cornerRadius, 1, 5);

        Label perimeterLabel = new Label(String.format("%.2f", rect.getPerimeter()));
        Label areaLabel = new Label(String.format("%.2f", rect.getArea()));
        grid.add(new Label("Периметр:"), 0, 6);
        grid.add(perimeterLabel, 1, 6);
        grid.add(new Label("Площадь:"), 0, 7);
        grid.add(areaLabel, 1, 7);

        Button applyBtn = createApplyButton("Применить", () -> {
            try {
                rect.setCenter(new Point(parseDouble(cx), parseDouble(cy)));
                rect.setWidth(parseDouble(width));
                rect.setHeight(parseDouble(height));
                rect.setCornerType(cornerTypeCombo.getValue());
                rect.setCornerRadius(parseDouble(cornerRadius));
                perimeterLabel.setText(String.format("%.2f", rect.getPerimeter()));
                areaLabel.setText(String.format("%.2f", rect.getArea()));
                painter.redrawAll();
            } catch (NumberFormatException e) {
                showError("Некорректные значения");
            }
        });

        editPropertiesContainer.getChildren().addAll(grid, applyBtn);
    }

    private void buildEllipseControls(Ellipse ellipse) {
        GridPane grid = createGrid();

        TextField cx = createNumberField(ellipse.getCenter().getX());
        TextField cy = createNumberField(ellipse.getCenter().getY());
        TextField majorAxis = createNumberField(ellipse.getSemiMajorAxis());
        TextField minorAxis = createNumberField(ellipse.getSemiMinorAxis());
        TextField rotation = createNumberField(Math.toDegrees(ellipse.getRotation()));

        grid.add(new Label("Центр X:"), 0, 0);
        grid.add(cx, 1, 0);
        grid.add(new Label("Центр Y:"), 0, 1);
        grid.add(cy, 1, 1);
        grid.add(new Label("Большая ось:"), 0, 2);
        grid.add(majorAxis, 1, 2);
        grid.add(new Label("Малая ось:"), 0, 3);
        grid.add(minorAxis, 1, 3);
        grid.add(new Label("Поворот (°):"), 0, 4);
        grid.add(rotation, 1, 4);

        Button applyBtn = createApplyButton("Применить", () -> {
            try {
                ellipse.setCenter(new Point(parseDouble(cx), parseDouble(cy)));
                ellipse.setSemiMajorAxis(parseDouble(majorAxis));
                ellipse.setSemiMinorAxis(parseDouble(minorAxis));
                ellipse.setRotation(Math.toRadians(parseDouble(rotation)));
                painter.redrawAll();
            } catch (NumberFormatException e) {
                showError("Некорректные значения");
            }
        });

        editPropertiesContainer.getChildren().addAll(grid, applyBtn);
    }

    private void buildPolygonControls(Polygon polygon) {
        GridPane grid = createGrid();

        TextField cx = createNumberField(polygon.getCenter().getX());
        TextField cy = createNumberField(polygon.getCenter().getY());
        TextField radius = createNumberField(polygon.getRadius());
        TextField rotation = createNumberField(Math.toDegrees(polygon.getRotation()));

        Spinner<Integer> sidesSpinner = new Spinner<>(3, 50, polygon.getSides());
        sidesSpinner.setEditable(true);
        sidesSpinner.setPrefWidth(100);

        ComboBox<Polygon.InscriptionType> inscriptionCombo = new ComboBox<>();
        inscriptionCombo.getItems().addAll(Polygon.InscriptionType.values());
        inscriptionCombo.setValue(polygon.getInscriptionType());
        inscriptionCombo.setMaxWidth(Double.MAX_VALUE);

        grid.add(new Label("Центр X:"), 0, 0);
        grid.add(cx, 1, 0);
        grid.add(new Label("Центр Y:"), 0, 1);
        grid.add(cy, 1, 1);
        grid.add(new Label("Радиус:"), 0, 2);
        grid.add(radius, 1, 2);
        grid.add(new Label("Поворот (°):"), 0, 3);
        grid.add(rotation, 1, 3);
        grid.add(new Label("Кол-во сторон:"), 0, 4);
        grid.add(sidesSpinner, 1, 4);
        grid.add(new Label("Тип:"), 0, 5);
        grid.add(inscriptionCombo, 1, 5);

        Label perimeterLabel = new Label(String.format("%.2f", polygon.getPerimeter()));
        Label areaLabel = new Label(String.format("%.2f", polygon.getArea()));
        Label sideLengthLabel = new Label(String.format("%.2f", polygon.getSideLength()));

        grid.add(new Label("Длина стороны:"), 0, 6);
        grid.add(sideLengthLabel, 1, 6);
        grid.add(new Label("Периметр:"), 0, 7);
        grid.add(perimeterLabel, 1, 7);
        grid.add(new Label("Площадь:"), 0, 8);
        grid.add(areaLabel, 1, 8);

        Button applyBtn = createApplyButton("Применить", () -> {
            try {
                polygon.setCenter(new Point(parseDouble(cx), parseDouble(cy)));
                polygon.setRadius(parseDouble(radius));
                polygon.setRotation(Math.toRadians(parseDouble(rotation)));
                polygon.setSides(sidesSpinner.getValue());
                polygon.setInscriptionType(inscriptionCombo.getValue());
                perimeterLabel.setText(String.format("%.2f", polygon.getPerimeter()));
                areaLabel.setText(String.format("%.2f", polygon.getArea()));
                sideLengthLabel.setText(String.format("%.2f", polygon.getSideLength()));
                painter.redrawAll();
            } catch (NumberFormatException e) {
                showError("Некорректные значения");
            }
        });

        editPropertiesContainer.getChildren().addAll(grid, applyBtn);
    }

    private void buildSplineControls(Spline spline) {
        Label info = new Label("Контрольные точки: " + spline.getControlPoints().size());

        GridPane headerGrid = createGrid();

        CheckBox closedCheck = new CheckBox("Замкнутый (соединить концы)");
        closedCheck.setSelected(spline.isClosed());
        closedCheck.setOnAction(e -> {
            spline.setClosed(closedCheck.isSelected());
            painter.redrawAll();
        });

        Spinner<Double> tensionSpinner = new Spinner<>(0.0, 1.0, spline.getTension(), 0.1);
        tensionSpinner.setEditable(true);
        tensionSpinner.setPrefWidth(80);
        tensionSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            spline.setTension(newVal);
            painter.redrawAll();
        });

        headerGrid.add(new Label("Натяжение:"), 0, 0);
        headerGrid.add(tensionSpinner, 1, 0);
        headerGrid.add(closedCheck, 0, 1, 2, 1);

        VBox pointsBox = new VBox(3);

        Runnable rebuildPointsList = () -> {
            pointsBox.getChildren().clear();
            int pointCount = spline.getPointCount();

            for (int i = 0; i < pointCount; i++) {
                final int index = i;
                Point p = spline.getControlPoint(i);

                HBox row = new HBox(3);
                row.setAlignment(Pos.CENTER_LEFT);

                Label numLabel = new Label((i + 1) + ":");
                numLabel.setMinWidth(20);

                // Поля координат
                TextField xField = createNumberField(p.getX());
                TextField yField = createNumberField(p.getY());
                xField.setPrefWidth(60);
                yField.setPrefWidth(60);

                xField.setOnAction(e -> {
                    try {
                        Point oldP = spline.getControlPoint(index);
                        spline.setControlPoint(index, new Point(parseDouble(xField), oldP.getY()));
                        painter.redrawAll();
                    } catch (NumberFormatException ex) {
                    }
                });
                yField.setOnAction(e -> {
                    try {
                        Point oldP = spline.getControlPoint(index);
                        spline.setControlPoint(index, new Point(oldP.getX(), parseDouble(yField)));
                        painter.redrawAll();
                    } catch (NumberFormatException ex) {
                    }
                });

                Button deleteBtn = new Button("×");
                deleteBtn.setStyle(
                        "-fx-font-size: 10; -fx-padding: 2 5 2 5; -fx-background-color: #ff6b6b; -fx-text-fill: white;");
                deleteBtn.setTooltip(new Tooltip("Удалить точку"));
                deleteBtn.setDisable(pointCount <= 2); // Минимум 2 точки
                deleteBtn.setOnAction(e -> {
                    if (spline.removeControlPoint(index)) {
                        info.setText("Контрольные точки: " + spline.getPointCount());
                        rebuildSplinePointsList(spline, pointsBox, info, painter);
                        painter.redrawAll();
                    }
                });

                Button insertBtn = new Button("+");
                insertBtn.setStyle(
                        "-fx-font-size: 10; -fx-padding: 2 5 2 5; -fx-background-color: #51cf66; -fx-text-fill: white;");
                insertBtn.setTooltip(new Tooltip("Вставить точку после"));
                insertBtn.setOnAction(e -> {
                    Point current = spline.getControlPoint(index);
                    Point next;
                    if (index + 1 < spline.getPointCount()) {
                        next = spline.getControlPoint(index + 1);
                    } else if (spline.isClosed()) {
                        next = spline.getControlPoint(0);
                    } else {
                        next = new Point(current.getX() + 50, current.getY() + 50);
                    }
                    Point newPoint = new Point(
                            (current.getX() + next.getX()) / 2,
                            (current.getY() + next.getY()) / 2);
                    spline.insertControlPoint(index + 1, newPoint);
                    info.setText("Контрольные точки: " + spline.getPointCount());
                    rebuildSplinePointsList(spline, pointsBox, info, painter);
                    painter.redrawAll();
                });

                row.getChildren().addAll(numLabel, new Label("X"), xField, new Label("Y"), yField, deleteBtn,
                        insertBtn);
                pointsBox.getChildren().add(row);
            }
        };

        rebuildPointsList.run();

        ScrollPane scrollPane = new ScrollPane(pointsBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(150);
        scrollPane.setMaxHeight(200);

        Button addPointBtn = new Button("+ Добавить точку в конец");
        addPointBtn.setStyle("-fx-background-color: #339af0; -fx-text-fill: white;");
        addPointBtn.setMaxWidth(Double.MAX_VALUE);
        addPointBtn.setOnAction(e -> {
            Point lastPoint = spline.getControlPoint(spline.getPointCount() - 1);
            Point newPoint = new Point(lastPoint.getX() + 50, lastPoint.getY());
            spline.addControlPoint(newPoint);
            info.setText("Контрольные точки: " + spline.getPointCount());
            rebuildSplinePointsList(spline, pointsBox, info, painter);
            painter.redrawAll();
        });

        Label lengthLabel = new Label(String.format("Длина: %.2f", spline.getLength()));

        Label hintLabel = new Label("× - удалить точку, + - вставить после");
        hintLabel.setStyle("-fx-font-size: 10; -fx-text-fill: gray;");

        editPropertiesContainer.getChildren().addAll(
                info, headerGrid, scrollPane, addPointBtn, lengthLabel, hintLabel);
    }

    private void buildDimensionControls(DimensionPrimitive dimension) {
        GridPane grid = createGrid();

        TextField textField = new TextField(dimension.getTextOverride());
        TextField textHeightField = createNumberField(dimension.getTextHeight());
        TextField arrowSizeField = createNumberField(dimension.getArrowSize());
        TextField extOffsetField = createNumberField(dimension.getExtensionLineOffset());
        TextField extOvershootField = createNumberField(dimension.getExtensionLineOvershoot());
        Label measuredValueLabel = new Label(dimension.getDisplayText());

        grid.add(new Label("Значение:"), 0, 0);
        grid.add(measuredValueLabel, 1, 0);
        grid.add(new Label("Переопределение:"), 0, 1);
        grid.add(textField, 1, 1);
        grid.add(new Label("Высота текста:"), 0, 2);
        grid.add(textHeightField, 1, 2);
        grid.add(new Label("Размер стрелки:"), 0, 3);
        grid.add(arrowSizeField, 1, 3);
        grid.add(new Label("Смещение выносных:"), 0, 4);
        grid.add(extOffsetField, 1, 4);
        grid.add(new Label("Выступ выносных:"), 0, 5);
        grid.add(extOvershootField, 1, 5);

        CheckBox filledCheck = new CheckBox("Закрашенные стрелки");
        filledCheck.setSelected(dimension.isFilledArrows());

        Button applyBtn = createApplyButton("Применить", () -> {
            try {
                dimension.setTextOverride(textField.getText());
                dimension.setTextHeight(parseDouble(textHeightField));
                dimension.setArrowSize(parseDouble(arrowSizeField));
                dimension.setExtensionLineOffset(parseDouble(extOffsetField));
                dimension.setExtensionLineOvershoot(parseDouble(extOvershootField));
                dimension.setFilledArrows(filledCheck.isSelected());
                measuredValueLabel.setText(dimension.getDisplayText());
                painter.redrawAll();
            } catch (NumberFormatException e) {
                showError("Некорректные значения");
            }
        });

        editPropertiesContainer.getChildren().addAll(grid, filledCheck, applyBtn);
    }

    private void buildDimensionControlsV2(DimensionPrimitive dimension) {
        GridPane grid = createGrid();

        TextField textField = new TextField(dimension.getTextOverride());
        TextField textHeightField = createNumberField(dimension.getTextHeight());
        TextField arrowSizeField = createNumberField(dimension.getArrowSize());
        TextField extOffsetField = createNumberField(dimension.getExtensionLineOffset());
        TextField extOvershootField = createNumberField(dimension.getExtensionLineOvershoot());
        TextField textGapField = createNumberField(dimension.getTextGap());
        Label measuredValueLabel = new Label(dimension.getDisplayText());

        ToggleGroup placementGroup = new ToggleGroup();
        RadioButton aboveButton = new RadioButton("Над");
        RadioButton onButton = new RadioButton("На");
        RadioButton belowButton = new RadioButton("Под");
        aboveButton.setToggleGroup(placementGroup);
        onButton.setToggleGroup(placementGroup);
        belowButton.setToggleGroup(placementGroup);
        switch (dimension.getTextPlacement()) {
            case ABOVE_LINE -> aboveButton.setSelected(true);
            case ON_LINE -> onButton.setSelected(true);
            case BELOW_LINE -> belowButton.setSelected(true);
        }
        HBox placementButtons = new HBox(8, aboveButton, onButton, belowButton);

        ComboBox<DimensionPrimitive.FontVariant> fontCombo = new ComboBox<>();
        fontCombo.getItems().addAll(DimensionPrimitive.FontVariant.values());
        fontCombo.setValue(dimension.getFontVariant());
        fontCombo.setMaxWidth(Double.MAX_VALUE);
        fontCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(DimensionPrimitive.FontVariant item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getDisplayName());
            }
        });
        fontCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(DimensionPrimitive.FontVariant item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getDisplayName());
            }
        });

        ComboBox<RadialDimension.ShelfSide> shelfCombo = null;
        if (dimension instanceof RadialDimension radialDimension) {
            shelfCombo = new ComboBox<>();
            shelfCombo.getItems().addAll(RadialDimension.ShelfSide.values());
            shelfCombo.setValue(radialDimension.getShelfSide());
            shelfCombo.setMaxWidth(Double.MAX_VALUE);
            shelfCombo.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(RadialDimension.ShelfSide item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getDisplayName());
                }
            });
            shelfCombo.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(RadialDimension.ShelfSide item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getDisplayName());
                }
            });
        }

        int row = 0;
        grid.add(new Label("Значение:"), 0, row);
        grid.add(measuredValueLabel, 1, row++);
        grid.add(new Label("Переопределение:"), 0, row);
        grid.add(textField, 1, row++);
        grid.add(new Label("Положение текста:"), 0, row);
        grid.add(placementButtons, 1, row++);
        grid.add(new Label("Шрифт:"), 0, row);
        grid.add(fontCombo, 1, row++);
        grid.add(new Label("Высота текста:"), 0, row);
        grid.add(textHeightField, 1, row++);
        grid.add(new Label("Смещение текста над линией:"), 0, row);
        grid.add(textGapField, 1, row++);
        grid.add(new Label("Размер стрелки:"), 0, row);
        grid.add(arrowSizeField, 1, row++);
        grid.add(new Label("Смещение выносных:"), 0, row);
        grid.add(extOffsetField, 1, row++);
        grid.add(new Label("Выступ выносных:"), 0, row);
        grid.add(extOvershootField, 1, row++);

        if (shelfCombo != null) {
            grid.add(new Label("Полка:"), 0, row);
            grid.add(shelfCombo, 1, row++);
        }

        CheckBox filledCheck = new CheckBox("Закрашенные стрелки");
        filledCheck.setSelected(dimension.isFilledArrows());

        ComboBox<RadialDimension.ShelfSide> finalShelfCombo = shelfCombo;
        Button applyBtn = createApplyButton("Применить", () -> {
            try {
                dimension.setTextOverride(textField.getText());
                DimensionPrimitive.TextPlacement selectedPlacement = aboveButton.isSelected()
                        ? DimensionPrimitive.TextPlacement.ABOVE_LINE
                        : belowButton.isSelected()
                        ? DimensionPrimitive.TextPlacement.BELOW_LINE
                        : DimensionPrimitive.TextPlacement.ON_LINE;
                dimension.setTextPlacement(selectedPlacement);
                dimension.setFontVariant(fontCombo.getValue());
                dimension.setTextHeight(parseDouble(textHeightField));
                dimension.setTextGap(parseDouble(textGapField));
                dimension.setArrowSize(parseDouble(arrowSizeField));
                dimension.setExtensionLineOffset(parseDouble(extOffsetField));
                dimension.setExtensionLineOvershoot(parseDouble(extOvershootField));
                dimension.setFilledArrows(filledCheck.isSelected());
                if (dimension instanceof RadialDimension radialDimension && finalShelfCombo != null) {
                    radialDimension.setShelfSide(finalShelfCombo.getValue());
                }
                measuredValueLabel.setText(dimension.getDisplayText());
                painter.redrawAll();
            } catch (NumberFormatException e) {
                showError("Некорректные значения");
            }
        });

        editPropertiesContainer.getChildren().addAll(grid, filledCheck, applyBtn);
    }

    private void buildDimensionControlsV3(DimensionPrimitive dimension) {
        GridPane grid = createGrid();

        TextField textField = new TextField(dimension.getTextOverride());
        TextField textHeightField = createNumberField(dimension.getTextHeight());
        TextField arrowSizeField = createNumberField(dimension.getArrowSize());
        TextField extOffsetField = createNumberField(dimension.getExtensionLineOffset());
        TextField extOvershootField = createNumberField(dimension.getExtensionLineOvershoot());
        TextField textGapField = createNumberField(dimension.getTextGap());
        Label measuredValueLabel = new Label(dimension.getDisplayText());

        ToggleGroup placementGroup = new ToggleGroup();
        RadioButton aboveButton = new RadioButton("Над");
        RadioButton onButton = new RadioButton("На");
        RadioButton belowButton = new RadioButton("Под");
        aboveButton.setToggleGroup(placementGroup);
        onButton.setToggleGroup(placementGroup);
        belowButton.setToggleGroup(placementGroup);
        switch (dimension.getTextPlacement()) {
            case ABOVE_LINE -> aboveButton.setSelected(true);
            case ON_LINE -> onButton.setSelected(true);
            case BELOW_LINE -> belowButton.setSelected(true);
        }
        HBox placementButtons = new HBox(8, aboveButton, onButton, belowButton);

        ComboBox<DimensionPrimitive.FontVariant> fontCombo = new ComboBox<>();
        fontCombo.getItems().addAll(DimensionPrimitive.FontVariant.values());
        fontCombo.setValue(dimension.getFontVariant());
        fontCombo.setMaxWidth(Double.MAX_VALUE);
        fontCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(DimensionPrimitive.FontVariant item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getDisplayName());
            }
        });
        fontCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(DimensionPrimitive.FontVariant item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getDisplayName());
            }
        });

        ComboBox<RadialDimension.ShelfSide> shelfCombo = null;
        if (dimension instanceof RadialDimension radialDimension) {
            shelfCombo = new ComboBox<>();
            shelfCombo.getItems().addAll(RadialDimension.ShelfSide.values());
            shelfCombo.setValue(radialDimension.getShelfSide());
            shelfCombo.setMaxWidth(Double.MAX_VALUE);
            shelfCombo.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(RadialDimension.ShelfSide item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getDisplayName());
                }
            });
            shelfCombo.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(RadialDimension.ShelfSide item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getDisplayName());
                }
            });
        }

        int row = 0;
        grid.add(new Label("Значение:"), 0, row);
        grid.add(measuredValueLabel, 1, row++);
        grid.add(new Label("Переопределение:"), 0, row);
        grid.add(textField, 1, row++);
        grid.add(new Label("Положение текста:"), 0, row);
        grid.add(placementButtons, 1, row++);
        grid.add(new Label("Шрифт:"), 0, row);
        grid.add(fontCombo, 1, row++);
        grid.add(new Label("Высота текста:"), 0, row);
        grid.add(textHeightField, 1, row++);
        grid.add(new Label("Смещение текста:"), 0, row);
        grid.add(textGapField, 1, row++);
        grid.add(new Label("Размер стрелки:"), 0, row);
        grid.add(arrowSizeField, 1, row++);
        grid.add(new Label("Смещение выносных:"), 0, row);
        grid.add(extOffsetField, 1, row++);
        grid.add(new Label("Выступ выносных:"), 0, row);
        grid.add(extOvershootField, 1, row++);

        if (shelfCombo != null) {
            grid.add(new Label("Полка:"), 0, row);
            grid.add(shelfCombo, 1, row++);
        }

        CheckBox filledCheck = new CheckBox("Закрашенные стрелки");
        filledCheck.setSelected(dimension.isFilledArrows());

        ComboBox<RadialDimension.ShelfSide> finalShelfCombo = shelfCombo;
        Button applyBtn = createApplyButton("Применить", () -> {
            try {
                DimensionPrimitive.TextPlacement selectedPlacement = aboveButton.isSelected()
                        ? DimensionPrimitive.TextPlacement.ABOVE_LINE
                        : belowButton.isSelected()
                        ? DimensionPrimitive.TextPlacement.BELOW_LINE
                        : DimensionPrimitive.TextPlacement.ON_LINE;
                dimension.setTextOverride(textField.getText());
                dimension.setTextPlacement(selectedPlacement);
                dimension.setFontVariant(fontCombo.getValue());
                dimension.setTextHeight(parseDouble(textHeightField));
                dimension.setTextGap(parseDouble(textGapField));
                dimension.setArrowSize(parseDouble(arrowSizeField));
                dimension.setExtensionLineOffset(parseDouble(extOffsetField));
                dimension.setExtensionLineOvershoot(parseDouble(extOvershootField));
                dimension.setFilledArrows(filledCheck.isSelected());
                if (dimension instanceof RadialDimension radialDimension && finalShelfCombo != null) {
                    radialDimension.setShelfSide(finalShelfCombo.getValue());
                }
                measuredValueLabel.setText(dimension.getDisplayText());
                painter.redrawAll();
            } catch (NumberFormatException e) {
                showError("Некорректные значения");
            }
        });

        editPropertiesContainer.getChildren().addAll(grid, filledCheck, applyBtn);
    }

    private void buildDimensionControlsV4(DimensionPrimitive dimension) {
        GridPane grid = createGrid();

        TextField textField = new TextField(dimension.getTextOverride());
        TextField textHeightField = createNumberField(dimension.getTextHeight());
        TextField arrowSizeField = createNumberField(dimension.getArrowSize());
        TextField extOffsetField = createNumberField(dimension.getExtensionLineOffset());
        TextField extOvershootField = createNumberField(dimension.getExtensionLineOvershoot());
        TextField textGapField = createNumberField(dimension.getTextGap());
        TextField dimensionExtensionField = createNumberField(dimension.getDimensionLineExtension());
        TextField dimensionOffsetField = dimension instanceof LinearDimension linearDimension
                ? createNumberField(linearDimension.getOffsetDistance())
                : null;
        Label measuredValueLabel = new Label(dimension.getDisplayText());

        ToggleGroup placementGroup = new ToggleGroup();
        RadioButton aboveButton = new RadioButton("Над");
        RadioButton onButton = new RadioButton("На");
        RadioButton belowButton = new RadioButton("Под");
        aboveButton.setToggleGroup(placementGroup);
        onButton.setToggleGroup(placementGroup);
        belowButton.setToggleGroup(placementGroup);
        switch (dimension.getTextPlacement()) {
            case ABOVE_LINE -> aboveButton.setSelected(true);
            case ON_LINE -> onButton.setSelected(true);
            case BELOW_LINE -> belowButton.setSelected(true);
        }
        HBox placementButtons = new HBox(8, aboveButton, onButton, belowButton);

        ComboBox<String> fontFamilyCombo = new ComboBox<>(FXCollections.observableArrayList(Font.getFamilies()));
        fontFamilyCombo.setEditable(false);
        fontFamilyCombo.setMaxWidth(Double.MAX_VALUE);
        if (!fontFamilyCombo.getItems().contains(dimension.getTextFont())) {
            fontFamilyCombo.getItems().add(dimension.getTextFont());
        }
        fontFamilyCombo.setValue(dimension.getTextFont());

        ComboBox<DimensionPrimitive.FontVariant> fontVariantCombo = new ComboBox<>();
        fontVariantCombo.getItems().addAll(DimensionPrimitive.FontVariant.values());
        fontVariantCombo.setValue(dimension.getFontVariant());
        fontVariantCombo.setMaxWidth(Double.MAX_VALUE);
        fontVariantCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(DimensionPrimitive.FontVariant item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getDisplayName());
            }
        });
        fontVariantCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(DimensionPrimitive.FontVariant item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getDisplayName());
            }
        });

        ComboBox<DimensionPrimitive.ArrowType> arrowTypeCombo = new ComboBox<>();
        arrowTypeCombo.getItems().addAll(DimensionPrimitive.ArrowType.values());
        arrowTypeCombo.setValue(dimension.getArrowType());
        arrowTypeCombo.setMaxWidth(Double.MAX_VALUE);
        arrowTypeCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(DimensionPrimitive.ArrowType item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : getArrowTypeLabel(item));
            }
        });
        arrowTypeCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(DimensionPrimitive.ArrowType item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : getArrowTypeLabel(item));
            }
        });

        ComboBox<LineStyle> extensionStyleCombo = new ComboBox<>(styleManager.getStyles());
        extensionStyleCombo.setMaxWidth(Double.MAX_VALUE);
        extensionStyleCombo.setCellFactory(lv -> new StyleListCell());
        extensionStyleCombo.setButtonCell(new StyleListCell());
        extensionStyleCombo.setValue(dimension.getExtensionLineStyle());

        ColorPicker dimensionColorPicker = new ColorPicker(
                dimension.getDimensionLineColor() != null ? dimension.getDimensionLineColor() : settings.getSegmentColor());
        ColorPicker extensionColorPicker = new ColorPicker(
                dimension.getExtensionLineColor() != null ? dimension.getExtensionLineColor() : dimensionColorPicker.getValue());

        ComboBox<RadialDimension.ShelfSide> shelfCombo = null;
        if (dimension instanceof RadialDimension radialDimension) {
            shelfCombo = new ComboBox<>();
            shelfCombo.getItems().addAll(RadialDimension.ShelfSide.values());
            shelfCombo.setValue(radialDimension.getShelfSide());
            shelfCombo.setMaxWidth(Double.MAX_VALUE);
            shelfCombo.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(RadialDimension.ShelfSide item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getDisplayName());
                }
            });
            shelfCombo.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(RadialDimension.ShelfSide item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getDisplayName());
                }
            });
        }

        CheckBox filledCheck = new CheckBox("Закрашенные стрелки");
        filledCheck.setSelected(dimension.isFilledArrows());
        filledCheck.setDisable(dimension.getArrowType() != DimensionPrimitive.ArrowType.CLOSED);
        arrowTypeCombo.valueProperty().addListener((obs, oldValue, newValue) -> {
            filledCheck.setDisable(newValue != DimensionPrimitive.ArrowType.CLOSED);
        });

        int row = 0;
        grid.add(new Label("Значение:"), 0, row);
        grid.add(measuredValueLabel, 1, row++);
        grid.add(new Label("Переопределение:"), 0, row);
        grid.add(textField, 1, row++);
        grid.add(new Label("Положение текста:"), 0, row);
        grid.add(placementButtons, 1, row++);
        grid.add(new Label("Шрифт:"), 0, row);
        grid.add(fontFamilyCombo, 1, row++);
        grid.add(new Label("Вариант шрифта:"), 0, row);
        grid.add(fontVariantCombo, 1, row++);
        grid.add(new Label("Высота текста:"), 0, row);
        grid.add(textHeightField, 1, row++);
        grid.add(new Label("Смещение текста:"), 0, row);
        grid.add(textGapField, 1, row++);
        grid.add(new Label("Тип стрелки:"), 0, row);
        grid.add(arrowTypeCombo, 1, row++);
        grid.add(new Label("Размер стрелки:"), 0, row);
        grid.add(arrowSizeField, 1, row++);
        grid.add(new Label("Стиль выносных линий:"), 0, row);
        grid.add(extensionStyleCombo, 1, row++);
        grid.add(new Label("Смещение выносных:"), 0, row);
        grid.add(extOffsetField, 1, row++);
        grid.add(new Label("Выступ выносных:"), 0, row);
        grid.add(extOvershootField, 1, row++);

        if (dimension instanceof LinearDimension) {
            grid.add(new Label("Выход размерной линии:"), 0, row);
            grid.add(dimensionExtensionField, 1, row++);
        }

        grid.add(new Label("Цвет размерной линии:"), 0, row);
        grid.add(dimensionColorPicker, 1, row++);
        grid.add(new Label("Цвет выносных линий:"), 0, row);
        grid.add(extensionColorPicker, 1, row++);

        if (shelfCombo != null) {
            grid.add(new Label("Полка:"), 0, row);
            grid.add(shelfCombo, 1, row++);
        }

        ComboBox<RadialDimension.ShelfSide> finalShelfCombo = shelfCombo;
        Button applyBtn = createApplyButton("Применить", () -> {
            try {
                DimensionPrimitive.TextPlacement selectedPlacement = aboveButton.isSelected()
                        ? DimensionPrimitive.TextPlacement.ABOVE_LINE
                        : belowButton.isSelected()
                        ? DimensionPrimitive.TextPlacement.BELOW_LINE
                        : DimensionPrimitive.TextPlacement.ON_LINE;

                dimension.setTextOverride(textField.getText());
                dimension.setTextPlacement(selectedPlacement);
                dimension.setTextFont(fontFamilyCombo.getValue());
                dimension.setFontVariant(fontVariantCombo.getValue());
                dimension.setTextHeight(parseDouble(textHeightField));
                dimension.setTextGap(parseDouble(textGapField));
                dimension.setArrowType(arrowTypeCombo.getValue());
                dimension.setArrowSize(parseDouble(arrowSizeField));
                dimension.setFilledArrows(filledCheck.isSelected());
                dimension.setExtensionLineStyle(extensionStyleCombo.getValue());
                dimension.setExtensionLineOffset(parseDouble(extOffsetField));
                dimension.setExtensionLineOvershoot(parseDouble(extOvershootField));
                dimension.setDimensionLineColor(dimensionColorPicker.getValue());
                dimension.setExtensionLineColor(extensionColorPicker.getValue());

                if (dimension instanceof LinearDimension) {
                    dimension.setDimensionLineExtension(parseDouble(dimensionExtensionField));
                }
                if (dimension instanceof RadialDimension radialDimension && finalShelfCombo != null) {
                    radialDimension.setShelfSide(finalShelfCombo.getValue());
                }

                measuredValueLabel.setText(dimension.getDisplayText());
                painter.redrawAll();
            } catch (NumberFormatException e) {
                showError("Некорректные значения");
            }
        });

        editPropertiesContainer.getChildren().addAll(grid, filledCheck, applyBtn);
    }

    private void buildDimensionControlsV5(DimensionPrimitive dimension) {
        GridPane grid = createDimensionEditorGrid();

        TextField textField = new TextField(dimension.getTextOverride());
        textField.setPromptText("Оставьте пустым для автоматического значения");
        TextField textHeightField = createNumberField(dimension.getTextHeight());
        TextField arrowSizeField = createNumberField(dimension.getArrowSize());
        TextField extOffsetField = createNumberField(dimension.getExtensionLineOffset());
        TextField extOvershootField = createNumberField(dimension.getExtensionLineOvershoot());
        TextField textGapField = createNumberField(dimension.getTextGap());
        TextField dimensionExtensionField = createNumberField(dimension.getDimensionLineExtension());
        TextField dimensionOffsetField = dimension instanceof LinearDimension linearDimension
                ? createNumberField(linearDimension.getOffsetDistance())
                : null;

        Label measuredValueLabel = new Label(getDimensionMeasuredValueText(dimension));
        measuredValueLabel.setStyle("-fx-font-weight: bold;");
        measuredValueLabel.setWrapText(true);
        measuredValueLabel.setMaxWidth(Double.MAX_VALUE);
        measuredValueLabel.setTooltip(new Tooltip("Вычисляется автоматически по геометрии"));

        ToggleGroup placementGroup = new ToggleGroup();
        RadioButton aboveButton = new RadioButton("Над");
        RadioButton onButton = new RadioButton("На");
        RadioButton belowButton = new RadioButton("Под");
        aboveButton.setToggleGroup(placementGroup);
        onButton.setToggleGroup(placementGroup);
        belowButton.setToggleGroup(placementGroup);
        switch (dimension.getTextPlacement()) {
            case ABOVE_LINE -> aboveButton.setSelected(true);
            case ON_LINE -> onButton.setSelected(true);
            case BELOW_LINE -> belowButton.setSelected(true);
        }
        aboveButton.setTooltip(new Tooltip("Показывать текст над размерной линией"));
        onButton.setTooltip(new Tooltip("Показывать текст на размерной линии"));
        belowButton.setTooltip(new Tooltip("Показывать текст под размерной линией"));
        HBox placementButtons = new HBox(10, aboveButton, onButton, belowButton);
        placementButtons.setAlignment(Pos.CENTER_LEFT);

        ComboBox<DimensionPrimitive.FontVariant> fontVariantCombo = new ComboBox<>();
        fontVariantCombo.getItems().addAll(DimensionPrimitive.FontVariant.values());
        fontVariantCombo.setValue(dimension.getFontVariant());
        fontVariantCombo.setMaxWidth(Double.MAX_VALUE);
        fontVariantCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(DimensionPrimitive.FontVariant item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getDisplayName());
            }
        });
        fontVariantCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(DimensionPrimitive.FontVariant item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getDisplayName());
            }
        });

        ComboBox<DimensionPrimitive.ArrowType> arrowTypeCombo = new ComboBox<>();
        arrowTypeCombo.getItems().addAll(DimensionPrimitive.ArrowType.values());
        arrowTypeCombo.setValue(dimension.getArrowType());
        arrowTypeCombo.setMaxWidth(Double.MAX_VALUE);
        arrowTypeCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(DimensionPrimitive.ArrowType item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getDisplayName());
            }
        });
        arrowTypeCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(DimensionPrimitive.ArrowType item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getDisplayName());
            }
        });

        ComboBox<LineStyle> dimensionStyleCombo = new ComboBox<>(styleManager.getStyles());
        dimensionStyleCombo.setMaxWidth(Double.MAX_VALUE);
        dimensionStyleCombo.setCellFactory(lv -> new StyleListCell());
        dimensionStyleCombo.setButtonCell(new StyleListCell());
        dimensionStyleCombo.setValue(dimension.getLineStyle());

        ComboBox<LineStyle> extensionStyleCombo = new ComboBox<>(styleManager.getStyles());
        extensionStyleCombo.setMaxWidth(Double.MAX_VALUE);
        extensionStyleCombo.setCellFactory(lv -> new StyleListCell());
        extensionStyleCombo.setButtonCell(new StyleListCell());
        extensionStyleCombo.setValue(dimension.getExtensionLineStyle());

        ColorPicker dimensionColorPicker = new ColorPicker(
                dimension.getDimensionLineColor() != null ? dimension.getDimensionLineColor() : settings.getSegmentColor());
        ColorPicker extensionColorPicker = new ColorPicker(
                dimension.getExtensionLineColor() != null ? dimension.getExtensionLineColor() : dimensionColorPicker.getValue());
        dimensionColorPicker.setMaxWidth(Double.MAX_VALUE);
        extensionColorPicker.setMaxWidth(Double.MAX_VALUE);

        ComboBox<RadialDimension.ShelfSide> shelfCombo = null;
        if (dimension instanceof RadialDimension radialDimension) {
            shelfCombo = new ComboBox<>();
            shelfCombo.getItems().addAll(RadialDimension.ShelfSide.values());
            shelfCombo.setValue(radialDimension.getShelfSide());
            shelfCombo.setMaxWidth(Double.MAX_VALUE);
            shelfCombo.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(RadialDimension.ShelfSide item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getDisplayName());
                }
            });
            shelfCombo.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(RadialDimension.ShelfSide item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getDisplayName());
                }
            });
        }

        CheckBox filledCheck = new CheckBox("Заполнять");
        filledCheck.setSelected(dimension.isFilledArrows());
        filledCheck.setDisable(dimension.getArrowType() != DimensionPrimitive.ArrowType.CLOSED);
        filledCheck.setTooltip(new Tooltip("Для закрытых стрелок включает заливку"));
        arrowTypeCombo.valueProperty().addListener((obs, oldValue, newValue) -> {
            filledCheck.setDisable(newValue != DimensionPrimitive.ArrowType.CLOSED);
        });

        int row = 0;
        row = addDimensionEditorRow(grid, row, "Значение:", measuredValueLabel);
        row = addDimensionEditorRow(grid, row, "Переопределение текста:", textField);
        row = addDimensionEditorRow(grid, row, "Положение текста:", placementButtons);
        row = addDimensionEditorRow(grid, row, "Вариант шрифта:", fontVariantCombo);
        row = addDimensionEditorRow(grid, row, "Высота текста:", textHeightField);
        row = addDimensionEditorRow(grid, row, "Смещение текста:", textGapField);
        row = addDimensionEditorRow(grid, row, "Тип стрелок:", arrowTypeCombo);
        row = addDimensionEditorRow(grid, row, "Размер стрелок:", arrowSizeField);
        row = addDimensionEditorRow(grid, row, "Заливка стрелок:", filledCheck);
        row = addDimensionEditorRow(grid, row, "Стиль размерной линии:", dimensionStyleCombo);
        row = addDimensionEditorRow(grid, row, "Стиль выносных линий:", extensionStyleCombo);
        row = addDimensionEditorRow(grid, row, "Смещение выносных линий:", extOffsetField);
        row = addDimensionEditorRow(grid, row, "Выступ выносных линий:", extOvershootField);

        if (dimension instanceof LinearDimension) {
            row = addDimensionEditorRow(grid, row, "Отступ размерной линии:", dimensionOffsetField);
            row = addDimensionEditorRow(grid, row, "Выход размерной линии:", dimensionExtensionField);
        }

        row = addDimensionEditorRow(grid, row, "Цвет размерной линии:", dimensionColorPicker);
        row = addDimensionEditorRow(grid, row, "Цвет выносных линий:", extensionColorPicker);

        if (shelfCombo != null) {
            row = addDimensionEditorRow(grid, row, "Полка:", shelfCombo);
        }

        ComboBox<RadialDimension.ShelfSide> finalShelfCombo = shelfCombo;
        Button applyBtn = createApplyButton("Применить", () -> {
            try {
                DimensionPrimitive.TextPlacement selectedPlacement = aboveButton.isSelected()
                        ? DimensionPrimitive.TextPlacement.ABOVE_LINE
                        : belowButton.isSelected()
                        ? DimensionPrimitive.TextPlacement.BELOW_LINE
                        : DimensionPrimitive.TextPlacement.ON_LINE;

                dimension.setTextOverride(textField.getText());
                dimension.setTextPlacement(selectedPlacement);
                dimension.setFontVariant(fontVariantCombo.getValue());
                dimension.setTextHeight(parseDouble(textHeightField));
                dimension.setTextGap(parseDouble(textGapField));
                dimension.setArrowType(arrowTypeCombo.getValue());
                dimension.setArrowSize(parseDouble(arrowSizeField));
                dimension.setFilledArrows(filledCheck.isSelected());
                dimension.setLineStyle(dimensionStyleCombo.getValue());
                dimension.setExtensionLineStyle(extensionStyleCombo.getValue());
                dimension.setExtensionLineOffset(parseDouble(extOffsetField));
                dimension.setExtensionLineOvershoot(parseDouble(extOvershootField));
                dimension.setDimensionLineColor(dimensionColorPicker.getValue());
                dimension.setExtensionLineColor(extensionColorPicker.getValue());

                if (dimension instanceof LinearDimension) {
                    ((LinearDimension) dimension).setOffsetDistance(parseDouble(dimensionOffsetField));
                    dimension.setDimensionLineExtension(parseDouble(dimensionExtensionField));
                }
                if (dimension instanceof RadialDimension radialDimension && finalShelfCombo != null) {
                    radialDimension.setShelfSide(finalShelfCombo.getValue());
                }

                measuredValueLabel.setText(getDimensionMeasuredValueText(dimension));
                painter.redrawAll();
            } catch (NumberFormatException e) {
                showError("Некорректные значения");
            }
        });

        editPropertiesContainer.getChildren().addAll(grid, applyBtn);
    }

    private void rebuildSplinePointsList(Spline spline, VBox pointsBox, Label info, CanvasPainter painter) {
        pointsBox.getChildren().clear();
        int pointCount = spline.getPointCount();

        for (int i = 0; i < pointCount; i++) {
            final int index = i;
            Point p = spline.getControlPoint(i);

            HBox row = new HBox(3);
            row.setAlignment(Pos.CENTER_LEFT);

            Label numLabel = new Label((i + 1) + ":");
            numLabel.setMinWidth(20);

            TextField xField = createNumberField(p.getX());
            TextField yField = createNumberField(p.getY());
            xField.setPrefWidth(60);
            yField.setPrefWidth(60);

            xField.setOnAction(e -> {
                try {
                    Point oldP = spline.getControlPoint(index);
                    spline.setControlPoint(index, new Point(parseDouble(xField), oldP.getY()));
                    painter.redrawAll();
                } catch (NumberFormatException ex) {
                }
            });
            yField.setOnAction(e -> {
                try {
                    Point oldP = spline.getControlPoint(index);
                    spline.setControlPoint(index, new Point(oldP.getX(), parseDouble(yField)));
                    painter.redrawAll();
                } catch (NumberFormatException ex) {
                }
            });

            Button deleteBtn = new Button("×");
            deleteBtn.setStyle(
                    "-fx-font-size: 10; -fx-padding: 2 5 2 5; -fx-background-color: #ff6b6b; -fx-text-fill: white;");
            deleteBtn.setTooltip(new Tooltip("Удалить точку"));
            deleteBtn.setDisable(pointCount <= 2);
            deleteBtn.setOnAction(e -> {
                if (spline.removeControlPoint(index)) {
                    info.setText("Контрольные точки: " + spline.getPointCount());
                    rebuildSplinePointsList(spline, pointsBox, info, painter);
                    painter.redrawAll();
                }
            });

            Button insertBtn = new Button("+");
            insertBtn.setStyle(
                    "-fx-font-size: 10; -fx-padding: 2 5 2 5; -fx-background-color: #51cf66; -fx-text-fill: white;");
            insertBtn.setTooltip(new Tooltip("Вставить точку после"));
            insertBtn.setOnAction(e -> {
                Point current = spline.getControlPoint(index);
                Point next;
                if (index + 1 < spline.getPointCount()) {
                    next = spline.getControlPoint(index + 1);
                } else if (spline.isClosed()) {
                    next = spline.getControlPoint(0);
                } else {
                    next = new Point(current.getX() + 50, current.getY() + 50);
                }
                Point newPoint = new Point(
                        (current.getX() + next.getX()) / 2,
                        (current.getY() + next.getY()) / 2);
                spline.insertControlPoint(index + 1, newPoint);
                info.setText("Контрольные точки: " + spline.getPointCount());
                rebuildSplinePointsList(spline, pointsBox, info, painter);
                painter.redrawAll();
            });

            row.getChildren().addAll(numLabel, new Label("X"), xField, new Label("Y"), yField, deleteBtn, insertBtn);
            pointsBox.getChildren().add(row);
        }
    }

    private GridPane createDimensionEditorGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setMaxWidth(Double.MAX_VALUE);

        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(165);
        labelColumn.setPrefWidth(180);

        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);
        valueColumn.setFillWidth(true);

        grid.getColumnConstraints().addAll(labelColumn, valueColumn);
        return grid;
    }

    private int addDimensionEditorRow(GridPane grid, int row, String labelText, Node control) {
        grid.add(createDimensionEditorLabel(labelText), 0, row);
        GridPane.setHgrow(control, Priority.ALWAYS);
        grid.add(control, 1, row);
        return row + 1;
    }

    private Label createDimensionEditorLabel(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMinWidth(165);
        label.setPrefWidth(180);
        label.setTooltip(new Tooltip(text));
        return label;
    }

    private String getDimensionMeasuredValueText(DimensionPrimitive dimension) {
        String formattedValue = formatNumber(dimension.getMeasuredValue());
        if (dimension instanceof AngularDimension) {
            return formattedValue + "°";
        }
        if (dimension instanceof RadialDimension radialDimension) {
            return radialDimension.getKind().getPrefix() + formattedValue;
        }
        return formattedValue;
    }

    private String getArrowTypeLabel(DimensionPrimitive.ArrowType arrowType) {
        if (arrowType == DimensionPrimitive.ArrowType.DOT) {
            return "Кружок";
        }
        return arrowType.getDisplayName();
    }

    private GridPane createGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        return grid;
    }

    private TextField createNumberField(double value) {
        TextField field = new TextField(formatNumber(value));
        field.setPrefWidth(150);
        field.setMaxWidth(Double.MAX_VALUE);
        return field;
    }

    private String formatNumber(double value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.format("%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private double parseDouble(TextField field) {
        return Double.parseDouble(field.getText().replace(",", "."));
    }

    private Button createApplyButton(String text, Runnable action) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        btn.setOnAction(e -> action.run());
        return btn;
    }

    private void showError(String message) {
        new Alert(Alert.AlertType.ERROR, message).showAndWait();
    }

    private VBox createPropertySection() {
        Label header = new Label("Стиль линии");
        header.setStyle("-fx-font-weight: bold;");

        ComboBox<LineStyle> styleCombo = new ComboBox<>(styleManager.getStyles());
        styleCombo.setMaxWidth(Double.MAX_VALUE);
        styleCombo.setCellFactory(lv -> new StyleListCell());
        styleCombo.setButtonCell(new StyleListCell());
        styleCombo.setDisable(true);

        model.selectedPrimitiveProperty().addListener((obs, oldPrim, newPrim) -> {
            if (newPrim != null) {
                styleCombo.setDisable(false);
                styleCombo.setValue(newPrim.getLineStyle());
            } else {
                styleCombo.setDisable(true);
                styleCombo.setValue(null);
            }
        });

        styleCombo.setOnAction(e -> {
            Primitive selected = model.getSelectedPrimitive();
            LineStyle selectedStyle = styleCombo.getValue();
            if (selectedStyle != null) {
                model.setCurrentStyle(selectedStyle);
                if (selected != null && selected.getLineStyle() != selectedStyle) {
                    selected.setLineStyle(selectedStyle);
                    painter.redrawAll();
                }
            }
        });

        return new VBox(10, header, styleCombo);
    }

    private VBox createPropertySectionV2() {
        Label header = new Label("Стиль и слой");
        header.setStyle("-fx-font-weight: bold;");

        ComboBox<LineStyle> styleCombo = new ComboBox<>(styleManager.getStyles());
        styleCombo.setMaxWidth(Double.MAX_VALUE);
        styleCombo.setCellFactory(lv -> new StyleListCell());
        styleCombo.setButtonCell(new StyleListCell());
        styleCombo.setDisable(true);

        ComboBox<String> layerCombo = new ComboBox<>();
        layerCombo.setMaxWidth(Double.MAX_VALUE);
        layerCombo.setDisable(true);

        model.selectedPrimitiveProperty().addListener((obs, oldPrim, newPrim) -> {
            if (newPrim != null) {
                styleCombo.setDisable(false);
                styleCombo.setValue(newPrim.getLineStyle());

                layerCombo.setDisable(false);
                layerCombo.getItems().setAll(model.getLayers().stream().map(Layer::getName).toList());
                layerCombo.setValue(newPrim.getLayerName());
            } else {
                styleCombo.setDisable(true);
                styleCombo.setValue(null);

                layerCombo.setDisable(true);
                layerCombo.getItems().clear();
                layerCombo.setValue(null);
            }
        });

        styleCombo.setOnAction(e -> {
            Primitive selected = model.getSelectedPrimitive();
            LineStyle selectedStyle = styleCombo.getValue();
            if (selectedStyle != null) {
                model.setCurrentStyle(selectedStyle);
                if (selected != null && selected.getLineStyle() != selectedStyle) {
                    selected.setLineStyle(selectedStyle);
                    painter.redrawAll();
                }
            }
        });

        layerCombo.setOnAction(e -> {
            Primitive selected = model.getSelectedPrimitive();
            String selectedLayer = layerCombo.getValue();
            if (selected != null && selectedLayer != null && !selectedLayer.equals(selected.getLayerName())) {
                selected.setLayerName(selectedLayer);
                painter.redrawAll();
            }
        });

        return new VBox(10,
                header,
                new Label("Стиль размерной/контурной линии:"),
                styleCombo,
                new Label("Слой:"),
                layerCombo);
    }

    private VBox createInputSection() {
        GridPane grid = new GridPane();
        grid.setHgap(5);
        grid.setVgap(10);

        TextField x1Field = new TextField("100");
        TextField y1Field = new TextField("100");
        grid.add(new Label("X₁:"), 0, 0);
        grid.add(x1Field, 1, 0);
        grid.add(new Label("Y₁:"), 0, 1);
        grid.add(y1Field, 1, 1);

        TextField x2Field = new TextField("300");
        TextField y2Field = new TextField("200");

        TextField lField = new TextField("150");
        TextField thetaField = new TextField("45");

        GridPane cartesianPane = new GridPane();
        cartesianPane.setHgap(5);
        cartesianPane.setVgap(10);
        cartesianPane.add(new Label("X₂:"), 0, 0);
        cartesianPane.add(x2Field, 1, 0);
        cartesianPane.add(new Label("Y₂:"), 0, 1);
        cartesianPane.add(y2Field, 1, 1);

        GridPane polarPane = new GridPane();
        polarPane.setHgap(5);
        polarPane.setVgap(10);
        ToggleButton angleUnitToggle = new ToggleButton("Градусы");
        angleUnitToggle.setOnAction(e -> angleUnitToggle.setText(angleUnitToggle.isSelected() ? "Радианы" : "Градусы"));
        HBox angleInputBox = new HBox(5, thetaField, angleUnitToggle);
        HBox.setHgrow(thetaField, Priority.ALWAYS);
        polarPane.add(new Label("L:"), 0, 0);
        polarPane.add(lField, 1, 0);
        polarPane.add(new Label("θ:"), 0, 1);
        polarPane.add(angleInputBox, 1, 1);

        ToggleButton modeToggle = new ToggleButton("Переключить на ввод по углу");
        modeToggle.setMaxWidth(Double.MAX_VALUE);
        polarPane.setVisible(false);
        polarPane.setManaged(false);

        modeToggle.setOnAction(e -> {
            boolean isPolar = modeToggle.isSelected();
            modeToggle.setText(isPolar ? "Переключить на ввод по координатам" : "Переключить на ввод по углу");
            cartesianPane.setVisible(!isPolar);
            cartesianPane.setManaged(!isPolar);
            polarPane.setVisible(isPolar);
            polarPane.setManaged(isPolar);
        });

        grid.add(cartesianPane, 0, 2, 2, 1);
        grid.add(polarPane, 0, 2, 2, 1);

        Button drawButton = new Button("Построить");
        drawButton.setOnAction(event -> {
            try {
                double x1 = Double.parseDouble(x1Field.getText());
                double y1 = Double.parseDouble(y1Field.getText());
                Point start = new Point(x1, y1);
                Point end;
                Segment.CreationMode mode;

                if (modeToggle.isSelected()) {
                    double l = Double.parseDouble(lField.getText());
                    double angVal = Double.parseDouble(thetaField.getText());
                    double rad = angleUnitToggle.isSelected() ? angVal : Math.toRadians(angVal);
                    end = new Point(x1 + l * Math.cos(rad), y1 + l * Math.sin(rad));
                    mode = Segment.CreationMode.POLAR;
                } else {
                    double x2 = Double.parseDouble(x2Field.getText());
                    double y2 = Double.parseDouble(y2Field.getText());
                    end = new Point(x2, y2);
                    mode = Segment.CreationMode.CARTESIAN;
                }
                model.addPrimitive(new Segment(start, end, mode, model.getCurrentStyle()));
                painter.redrawAll();
            } catch (NumberFormatException ex) {
                new Alert(Alert.AlertType.ERROR, "Некорректные данные").showAndWait();
            }
        });

        Label header = new Label("1. Построение отрезка");
        header.setStyle("-fx-font-weight: bold;");
        return new VBox(10, header, grid, modeToggle, drawButton);
    }

    private VBox createDisplaySection() {
        GridPane grid = new GridPane();
        grid.setHgap(5);
        grid.setVgap(10);

        TextField gridStepField = new TextField();
        gridStepField.textProperty().bindBidirectional(settings.gridStepProperty(), new NumberStringConverter());

        ColorPicker segmentColorPicker = new ColorPicker(settings.getSegmentColor());
        settings.segmentColorProperty().bind(segmentColorPicker.valueProperty());

        ColorPicker gridColorPicker = new ColorPicker(settings.getGridColor());
        settings.gridColorProperty().bind(gridColorPicker.valueProperty());

        ColorPicker backgroundColorPicker = new ColorPicker(settings.getBackgroundColor());
        settings.backgroundColorProperty().bind(backgroundColorPicker.valueProperty());

        grid.add(new Label("Шаг сетки:"), 0, 0);
        grid.add(gridStepField, 1, 0);

        grid.add(new Label("Цвет отрезка:"), 0, 1);
        grid.add(segmentColorPicker, 1, 1);
        grid.add(new Label("Цвет сетки:"), 0, 2);
        grid.add(gridColorPicker, 1, 2);
        grid.add(new Label("Цвет фона:"), 0, 3);
        grid.add(backgroundColorPicker, 1, 3);

        Label header = new Label("Настройки отображения");
        header.setStyle("-fx-font-weight: bold;");
        return new VBox(10, header, grid);
    }
}
