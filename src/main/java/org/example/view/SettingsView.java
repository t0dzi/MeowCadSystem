package org.example.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
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
        VBox propertySection = createPropertySection();
        VBox editSection = createEditSection();

        VBox mainPanel = new VBox(15,
                editSection, new Separator(),
                propertySection, new Separator(),
                inputSection, new Separator(),
                displaySection);
        mainPanel.setPadding(new Insets(10));
        mainPanel.setStyle("-fx-background-color: #f0f0f0;");
        mainPanel.setPrefWidth(320);

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
            case SEGMENT -> buildSegmentControls((Segment) primitive);
            case CIRCLE -> buildCircleControls((Circle) primitive);
            case ARC -> buildArcControls((Arc) primitive);
            case RECTANGLE -> buildRectangleControls((Rectangle) primitive);
            case ELLIPSE -> buildEllipseControls((Ellipse) primitive);
            case POLYGON -> buildPolygonControls((Polygon) primitive);
            case SPLINE -> buildSplineControls((Spline) primitive);
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

    private GridPane createGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        return grid;
    }

    private TextField createNumberField(double value) {
        TextField field = new TextField(formatNumber(value));
        field.setPrefWidth(100);
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

