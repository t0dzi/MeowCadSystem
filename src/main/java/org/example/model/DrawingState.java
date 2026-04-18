package org.example.model;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.List;

/**
 * Состояние текущего сеанса рисования.
 */
public class DrawingState {

    public enum Tool {
        SELECT("Выбор", "Выб"),
        SEGMENT("Отрезок", "Лин"),
        CIRCLE("Окружность", "Окр"),
        ARC("Дуга", "Дуг"),
        RECTANGLE("Прямоугольник", "Прм"),
        ELLIPSE("Эллипс", "Элп"),
        POLYGON("Многоугольник", "Мнг"),
        SPLINE("Сплайн", "Спл"),
        DIMENSION("Размеры", "Раз");

        private final String displayName;
        private final String icon;

        Tool(String displayName, String icon) {
            this.displayName = displayName;
            this.icon = icon;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getIcon() {
            return icon;
        }
    }

    public enum CreationMethod {
        SEGMENT_TWO_POINTS("Две точки"),
        CIRCLE_CENTER_RADIUS("Центр и радиус"),
        CIRCLE_CENTER_DIAMETER("Центр и диаметр"),
        CIRCLE_TWO_POINTS("Две точки (диаметр)"),
        CIRCLE_THREE_POINTS("Три точки"),
        ARC_THREE_POINTS("Три точки"),
        ARC_CENTER_ANGLES("Центр и углы"),
        RECT_TWO_POINTS("Две точки"),
        RECT_CORNER_SIZE("Угол и размеры"),
        RECT_CENTER_SIZE("Центр и размеры"),
        ELLIPSE_CENTER_AXES("Центр и оси"),
        POLYGON_CENTER_RADIUS("Центр и радиус"),
        SPLINE_POINTS("По точкам"),
        DIMENSION_LINEAR_HORIZONTAL("Линейный горизонтальный"),
        DIMENSION_LINEAR_VERTICAL("Линейный вертикальный"),
        DIMENSION_LINEAR_ALIGNED("Линейный выровненный"),
        DIMENSION_RADIUS("Радиус"),
        DIMENSION_DIAMETER("Диаметр"),
        DIMENSION_ANGLE("Угол");

        private final String displayName;

        CreationMethod(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private final ObjectProperty<Tool> currentTool = new SimpleObjectProperty<>(Tool.SELECT);
    private final ObjectProperty<CreationMethod> creationMethod = new SimpleObjectProperty<>(null);

    private final ObservableList<Point> collectedPoints = FXCollections.observableArrayList();
    private final ObservableList<DimensionAnchor> collectedDimensionAnchors = FXCollections.observableArrayList();

    private final ObjectProperty<Point> currentMousePosition = new SimpleObjectProperty<>(null);
    private final ObjectProperty<Primitive> referencePrimitive = new SimpleObjectProperty<>(null);
    private final ObjectProperty<RadialDimension.ShelfSide> radialShelfSide =
            new SimpleObjectProperty<>(RadialDimension.ShelfSide.ALONG_LINE);

    private final BooleanProperty isDrawing = new SimpleBooleanProperty(false);

    private final DoubleProperty inputRadius = new SimpleDoubleProperty(0);
    private final DoubleProperty inputDiameter = new SimpleDoubleProperty(0);
    private final DoubleProperty inputWidth = new SimpleDoubleProperty(0);
    private final DoubleProperty inputHeight = new SimpleDoubleProperty(0);
    private final DoubleProperty inputSemiMajorAxis = new SimpleDoubleProperty(0);
    private final DoubleProperty inputSemiMinorAxis = new SimpleDoubleProperty(0);
    private final DoubleProperty inputLength = new SimpleDoubleProperty(0);
    private final DoubleProperty inputAngle = new SimpleDoubleProperty(0);
    private final BooleanProperty useManualInput = new SimpleBooleanProperty(false);

    private final IntegerProperty polygonSides = new SimpleIntegerProperty(6);
    private final ObjectProperty<Polygon.InscriptionType> polygonType =
            new SimpleObjectProperty<>(Polygon.InscriptionType.INSCRIBED);

    private final DoubleProperty rectangleCornerRadius = new SimpleDoubleProperty(0);
    private final ObjectProperty<Rectangle.CornerType> rectangleCornerType =
            new SimpleObjectProperty<>(Rectangle.CornerType.SHARP);

    public Tool getCurrentTool() {
        return currentTool.get();
    }

    public void setCurrentTool(Tool tool) {
        currentTool.set(tool);
        setDefaultCreationMethod(tool);
        reset();
    }

    public ObjectProperty<Tool> currentToolProperty() {
        return currentTool;
    }

    public CreationMethod getCreationMethod() {
        return creationMethod.get();
    }

    public void setCreationMethod(CreationMethod method) {
        creationMethod.set(method);
        reset();
    }

    public void setCreationMethodPreservingState(CreationMethod method) {
        creationMethod.set(method);
    }

    public ObjectProperty<CreationMethod> creationMethodProperty() {
        return creationMethod;
    }

    public ObservableList<Point> getCollectedPoints() {
        return collectedPoints;
    }

    public ObservableList<DimensionAnchor> getCollectedDimensionAnchors() {
        return collectedDimensionAnchors;
    }

    public Point getCurrentMousePosition() {
        return currentMousePosition.get();
    }

    public void setCurrentMousePosition(Point pos) {
        currentMousePosition.set(pos);
    }

    public ObjectProperty<Point> currentMousePositionProperty() {
        return currentMousePosition;
    }

    public Primitive getReferencePrimitive() {
        return referencePrimitive.get();
    }

    public void setReferencePrimitive(Primitive primitive) {
        referencePrimitive.set(primitive);
    }

    public ObjectProperty<Primitive> referencePrimitiveProperty() {
        return referencePrimitive;
    }

    public RadialDimension.ShelfSide getRadialShelfSide() {
        return radialShelfSide.get();
    }

    public void setRadialShelfSide(RadialDimension.ShelfSide shelfSide) {
        radialShelfSide.set(shelfSide != null ? shelfSide : RadialDimension.ShelfSide.ALONG_LINE);
    }

    public ObjectProperty<RadialDimension.ShelfSide> radialShelfSideProperty() {
        return radialShelfSide;
    }

    public boolean isDrawing() {
        return isDrawing.get();
    }

    public void setDrawing(boolean drawing) {
        isDrawing.set(drawing);
    }

    public BooleanProperty isDrawingProperty() {
        return isDrawing;
    }

    public double getInputRadius() {
        return inputRadius.get();
    }

    public void setInputRadius(double value) {
        inputRadius.set(value);
        inputDiameter.set(value * 2);
    }

    public DoubleProperty inputRadiusProperty() {
        return inputRadius;
    }

    public double getInputDiameter() {
        return inputDiameter.get();
    }

    public void setInputDiameter(double value) {
        inputDiameter.set(value);
        inputRadius.set(value / 2);
    }

    public DoubleProperty inputDiameterProperty() {
        return inputDiameter;
    }

    public double getInputWidth() {
        return inputWidth.get();
    }

    public void setInputWidth(double value) {
        inputWidth.set(value);
    }

    public DoubleProperty inputWidthProperty() {
        return inputWidth;
    }

    public double getInputHeight() {
        return inputHeight.get();
    }

    public void setInputHeight(double value) {
        inputHeight.set(value);
    }

    public DoubleProperty inputHeightProperty() {
        return inputHeight;
    }

    public double getInputSemiMajorAxis() {
        return inputSemiMajorAxis.get();
    }

    public void setInputSemiMajorAxis(double value) {
        inputSemiMajorAxis.set(value);
    }

    public DoubleProperty inputSemiMajorAxisProperty() {
        return inputSemiMajorAxis;
    }

    public double getInputSemiMinorAxis() {
        return inputSemiMinorAxis.get();
    }

    public void setInputSemiMinorAxis(double value) {
        inputSemiMinorAxis.set(value);
    }

    public DoubleProperty inputSemiMinorAxisProperty() {
        return inputSemiMinorAxis;
    }

    public double getInputLength() {
        return inputLength.get();
    }

    public void setInputLength(double value) {
        inputLength.set(value);
    }

    public DoubleProperty inputLengthProperty() {
        return inputLength;
    }

    public double getInputAngle() {
        return inputAngle.get();
    }

    public void setInputAngle(double value) {
        inputAngle.set(value);
    }

    public DoubleProperty inputAngleProperty() {
        return inputAngle;
    }

    public boolean isUseManualInput() {
        return useManualInput.get();
    }

    public void setUseManualInput(boolean value) {
        useManualInput.set(value);
    }

    public BooleanProperty useManualInputProperty() {
        return useManualInput;
    }

    public int getPolygonSides() {
        return polygonSides.get();
    }

    public void setPolygonSides(int sides) {
        polygonSides.set(Math.max(3, sides));
    }

    public IntegerProperty polygonSidesProperty() {
        return polygonSides;
    }

    public Polygon.InscriptionType getPolygonType() {
        return polygonType.get();
    }

    public void setPolygonType(Polygon.InscriptionType type) {
        polygonType.set(type);
    }

    public ObjectProperty<Polygon.InscriptionType> polygonTypeProperty() {
        return polygonType;
    }

    public double getRectangleCornerRadius() {
        return rectangleCornerRadius.get();
    }

    public void setRectangleCornerRadius(double radius) {
        rectangleCornerRadius.set(Math.max(0, radius));
    }

    public DoubleProperty rectangleCornerRadiusProperty() {
        return rectangleCornerRadius;
    }

    public Rectangle.CornerType getRectangleCornerType() {
        return rectangleCornerType.get();
    }

    public void setRectangleCornerType(Rectangle.CornerType type) {
        rectangleCornerType.set(type);
    }

    public ObjectProperty<Rectangle.CornerType> rectangleCornerTypeProperty() {
        return rectangleCornerType;
    }

    public void addPoint(Point point) {
        collectedPoints.add(point);
        setDrawing(true);
    }

    public void addDimensionPoint(Point point, DimensionAnchor anchor) {
        addPoint(point);
        collectedDimensionAnchors.add(anchor != null ? anchor : DimensionAnchor.fixed(point));
    }

    public void reset() {
        collectedPoints.clear();
        collectedDimensionAnchors.clear();
        currentMousePosition.set(null);
        referencePrimitive.set(null);
        isDrawing.set(false);
        useManualInput.set(false);
    }

    public int getRequiredPointCount() {
        CreationMethod method = getCreationMethod();
        if (method == null) {
            return 0;
        }

        return switch (method) {
            case SEGMENT_TWO_POINTS, CIRCLE_CENTER_RADIUS, CIRCLE_CENTER_DIAMETER,
                 CIRCLE_TWO_POINTS, RECT_TWO_POINTS, POLYGON_CENTER_RADIUS -> 2;
            case CIRCLE_THREE_POINTS, ARC_THREE_POINTS, ARC_CENTER_ANGLES, ELLIPSE_CENTER_AXES -> 3;
            case RECT_CORNER_SIZE, RECT_CENTER_SIZE -> 2;
            case SPLINE_POINTS -> -1;
            case DIMENSION_LINEAR_HORIZONTAL, DIMENSION_LINEAR_VERTICAL, DIMENSION_LINEAR_ALIGNED -> 3;
            case DIMENSION_RADIUS, DIMENSION_DIAMETER -> 2;
            case DIMENSION_ANGLE -> 4;
        };
    }

    public boolean hasEnoughPoints() {
        int required = getRequiredPointCount();
        if (required < 0) {
            return false;
        }
        return collectedPoints.size() >= required;
    }

    public boolean cycleDimensionCreationMethod() {
        CreationMethod nextMethod = switch (getCreationMethod()) {
            case DIMENSION_LINEAR_HORIZONTAL -> CreationMethod.DIMENSION_LINEAR_VERTICAL;
            case DIMENSION_LINEAR_VERTICAL -> CreationMethod.DIMENSION_LINEAR_ALIGNED;
            case DIMENSION_LINEAR_ALIGNED -> CreationMethod.DIMENSION_LINEAR_HORIZONTAL;
            case DIMENSION_RADIUS -> CreationMethod.DIMENSION_DIAMETER;
            case DIMENSION_DIAMETER -> CreationMethod.DIMENSION_RADIUS;
            default -> null;
        };

        if (nextMethod == null) {
            return false;
        }

        setCreationMethodPreservingState(nextMethod);
        return true;
    }

    public boolean toggleRadialShelfSide() {
        if (getCreationMethod() != CreationMethod.DIMENSION_RADIUS
                && getCreationMethod() != CreationMethod.DIMENSION_DIAMETER) {
            return false;
        }
        setRadialShelfSide(getRadialShelfSide().toggle());
        return true;
    }

    public List<CreationMethod> getAvailableCreationMethods() {
        Tool tool = getCurrentTool();
        List<CreationMethod> methods = new ArrayList<>();

        switch (tool) {
            case SEGMENT -> methods.add(CreationMethod.SEGMENT_TWO_POINTS);
            case CIRCLE -> {
                methods.add(CreationMethod.CIRCLE_CENTER_RADIUS);
                methods.add(CreationMethod.CIRCLE_CENTER_DIAMETER);
                methods.add(CreationMethod.CIRCLE_TWO_POINTS);
                methods.add(CreationMethod.CIRCLE_THREE_POINTS);
            }
            case ARC -> {
                methods.add(CreationMethod.ARC_THREE_POINTS);
                methods.add(CreationMethod.ARC_CENTER_ANGLES);
            }
            case RECTANGLE -> {
                methods.add(CreationMethod.RECT_TWO_POINTS);
                methods.add(CreationMethod.RECT_CORNER_SIZE);
                methods.add(CreationMethod.RECT_CENTER_SIZE);
            }
            case ELLIPSE -> methods.add(CreationMethod.ELLIPSE_CENTER_AXES);
            case POLYGON -> methods.add(CreationMethod.POLYGON_CENTER_RADIUS);
            case SPLINE -> methods.add(CreationMethod.SPLINE_POINTS);
            case DIMENSION -> {
                methods.add(CreationMethod.DIMENSION_LINEAR_HORIZONTAL);
                methods.add(CreationMethod.DIMENSION_LINEAR_VERTICAL);
                methods.add(CreationMethod.DIMENSION_LINEAR_ALIGNED);
                methods.add(CreationMethod.DIMENSION_RADIUS);
                methods.add(CreationMethod.DIMENSION_DIAMETER);
                methods.add(CreationMethod.DIMENSION_ANGLE);
            }
            default -> {}
        }

        return methods;
    }

    private void setDefaultCreationMethod(Tool tool) {
        CreationMethod defaultMethod = switch (tool) {
            case SEGMENT -> CreationMethod.SEGMENT_TWO_POINTS;
            case CIRCLE -> CreationMethod.CIRCLE_CENTER_RADIUS;
            case ARC -> CreationMethod.ARC_THREE_POINTS;
            case RECTANGLE -> CreationMethod.RECT_TWO_POINTS;
            case ELLIPSE -> CreationMethod.ELLIPSE_CENTER_AXES;
            case POLYGON -> CreationMethod.POLYGON_CENTER_RADIUS;
            case SPLINE -> CreationMethod.SPLINE_POINTS;
            case DIMENSION -> CreationMethod.DIMENSION_LINEAR_HORIZONTAL;
            default -> null;
        };
        creationMethod.set(defaultMethod);
    }

    public String getHint() {
        if (getCurrentTool() == Tool.SELECT) {
            return "Выберите примитив для редактирования";
        }

        CreationMethod method = getCreationMethod();
        if (method == null) {
            return "";
        }

        int collected = collectedPoints.size();

        return switch (method) {
            case SEGMENT_TWO_POINTS ->
                    collected == 0 ? "Укажите начальную точку" : "Укажите конечную точку";
            case CIRCLE_CENTER_RADIUS, CIRCLE_CENTER_DIAMETER ->
                    collected == 0 ? "Укажите центр окружности" : "Укажите точку на окружности";
            case CIRCLE_TWO_POINTS ->
                    collected == 0 ? "Укажите первую точку диаметра" : "Укажите вторую точку диаметра";
            case CIRCLE_THREE_POINTS ->
                    switch (collected) {
                        case 0 -> "Укажите первую точку";
                        case 1 -> "Укажите вторую точку";
                        default -> "Укажите третью точку";
                    };
            case ARC_THREE_POINTS ->
                    switch (collected) {
                        case 0 -> "Укажите начальную точку дуги";
                        case 1 -> "Укажите точку на дуге";
                        default -> "Укажите конечную точку дуги";
                    };
            case ARC_CENTER_ANGLES ->
                    switch (collected) {
                        case 0 -> "Укажите центр дуги";
                        case 1 -> "Укажите начальную точку дуги";
                        default -> "Укажите конечную точку дуги";
                    };
            case RECT_TWO_POINTS ->
                    collected == 0 ? "Укажите первый угол" : "Укажите противоположный угол";
            case RECT_CORNER_SIZE, RECT_CENTER_SIZE ->
                    collected == 0 ? "Укажите " + (method == CreationMethod.RECT_CENTER_SIZE ? "центр" : "угол") :
                            "Укажите размеры";
            case ELLIPSE_CENTER_AXES ->
                    switch (collected) {
                        case 0 -> "Укажите центр эллипса";
                        case 1 -> "Укажите конец большой оси";
                        default -> "Укажите конец малой оси";
                    };
            case POLYGON_CENTER_RADIUS ->
                    collected == 0 ? "Укажите центр многоугольника" : "Укажите вершину";
            case SPLINE_POINTS ->
                    collected < 2 ? "Добавляйте точки, минимум 2. Enter для завершения" :
                            "Добавляйте точки. Enter для завершения";
            case DIMENSION_LINEAR_HORIZONTAL, DIMENSION_LINEAR_VERTICAL, DIMENSION_LINEAR_ALIGNED ->
                    switch (collected) {
                        case 0 -> "Укажите первую точку измерения. M - смена типа";
                        case 1 -> "Укажите вторую точку измерения. M - смена типа";
                        default -> "Укажите положение размерной линии. M - смена типа";
                    };
            case DIMENSION_RADIUS, DIMENSION_DIAMETER ->
                    collected == 0 ? "Укажите окружность или дугу. M - радиус/диаметр, Z - полка" :
                            "Укажите положение выноски. M - радиус/диаметр, Z - полка";
            case DIMENSION_ANGLE ->
                    switch (collected) {
                        case 0 -> "Укажите вершину угла";
                        case 1 -> "Укажите первую точку на луче";
                        case 2 -> "Укажите вторую точку на луче";
                        default -> "Укажите положение дуги размера";
                    };
        };
    }
}
