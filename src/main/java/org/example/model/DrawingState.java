package org.example.model;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.List;

/**
 * Состояние текущего сеанса рисования.
 *
 * Хранит выбранный инструмент ({@link Tool}), способ создания ({@link CreationMethod}),
 * накопленные точки ({@code collectedPoints}), позицию курсора и параметры
 * ручного ввода (радиус, размеры и т.д.).
 *
 * Используется {@code CanvasController} для определения, что создавать при кликах,
 * и {@code CanvasPainter} для рисования предварительного вида.
 * Все поля — JavaFX-пропертис, что позволяет UI автоматически реагировать на изменения.
 */
public class DrawingState {

    /**
     * Доступные инструменты рисования.
     * Каждый инструмент соответствует одному типу геометрического примитива.
     * SELECT — режим выбора и редактирования примитивов.
     */
    public enum Tool {
        SELECT("Выбор", "🖱"),
        SEGMENT("Отрезок", "╱"),
        CIRCLE("Окружность", "○"),
        ARC("Дуга", "◠"),
        RECTANGLE("Прямоугольник", "▭"),
        ELLIPSE("Эллипс", "⬭"),
        POLYGON("Многоугольник", "⬡"),
        SPLINE("Сплайн", "〜");

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

    /**
     * Способы создания примитива для каждого инструмента.
     * Определяет количество опорных точек и порядок их ввода мышью.
     */
    public enum CreationMethod {
        SEGMENT_TWO_POINTS("Две точки"),
        
        // Окружность
        CIRCLE_CENTER_RADIUS("Центр и радиус"),
        CIRCLE_CENTER_DIAMETER("Центр и диаметр"),
        CIRCLE_TWO_POINTS("Две точки (диаметр)"),
        CIRCLE_THREE_POINTS("Три точки"),
        
        // Дуга
        ARC_THREE_POINTS("Три точки"),
        ARC_CENTER_ANGLES("Центр и углы"),
        
        RECT_TWO_POINTS("Две точки"),
        RECT_CORNER_SIZE("Угол и размеры"),
        RECT_CENTER_SIZE("Центр и размеры"),
        
        // Эллипс (центр → большая ось → малая ось)
        ELLIPSE_CENTER_AXES("Центр и оси"),
        
        POLYGON_CENTER_RADIUS("Центр и радиус"),
        
        SPLINE_POINTS("По точкам");

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
    
    private final ObjectProperty<Point> currentMousePosition = new SimpleObjectProperty<>(null);
    
    private final BooleanProperty isDrawing = new SimpleBooleanProperty(false);
    
    
    /** Радиус (для окружности, дуги, многоугольника) */
    private final DoubleProperty inputRadius = new SimpleDoubleProperty(0);
    
    /** Диаметр (для окружности) */
    private final DoubleProperty inputDiameter = new SimpleDoubleProperty(0);
    
    private final DoubleProperty inputWidth = new SimpleDoubleProperty(0);
    
    private final DoubleProperty inputHeight = new SimpleDoubleProperty(0);
    
    /** Большая полуось эллипса */
    private final DoubleProperty inputSemiMajorAxis = new SimpleDoubleProperty(0);
    
    /** Малая полуось эллипса */
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

    /**
     * Переключает активный инструмент, сбрасывает накопленные точки
     * и устанавливает метод создания по умолчанию для нового инструмента.
     */
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

    /**
     * Изменяет метод создания и сбрасывает накопленные точки.
     * Вызывается при переключении способа (например, с «Три точки» на «Центр и радиус»).
     */
    public void setCreationMethod(CreationMethod method) {
        creationMethod.set(method);
        reset();
    }

    public ObjectProperty<CreationMethod> creationMethodProperty() {
        return creationMethod;
    }

    public ObservableList<Point> getCollectedPoints() {
        return collectedPoints;
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

    public boolean isDrawing() {
        return isDrawing.get();
    }

    public void setDrawing(boolean drawing) {
        isDrawing.set(drawing);
    }

    public BooleanProperty isDrawingProperty() {
        return isDrawing;
    }

    
    public double getInputRadius() { return inputRadius.get(); }
    /**
     * Устанавливает радиус и синхронно обновляет диаметр (diameter = radius × 2).
     */
    public void setInputRadius(double value) { 
        inputRadius.set(value); 
        inputDiameter.set(value * 2);
    }
    public DoubleProperty inputRadiusProperty() { return inputRadius; }
    
    public double getInputDiameter() { return inputDiameter.get(); }
    /**
     * Устанавливает диаметр и синхронно обновляет радиус (radius = diameter / 2).
     */
    public void setInputDiameter(double value) { 
        inputDiameter.set(value); 
        inputRadius.set(value / 2);
    }
    public DoubleProperty inputDiameterProperty() { return inputDiameter; }
    
    public double getInputWidth() { return inputWidth.get(); }
    public void setInputWidth(double value) { inputWidth.set(value); }
    public DoubleProperty inputWidthProperty() { return inputWidth; }
    
    public double getInputHeight() { return inputHeight.get(); }
    public void setInputHeight(double value) { inputHeight.set(value); }
    public DoubleProperty inputHeightProperty() { return inputHeight; }
    
    public double getInputSemiMajorAxis() { return inputSemiMajorAxis.get(); }
    public void setInputSemiMajorAxis(double value) { inputSemiMajorAxis.set(value); }
    public DoubleProperty inputSemiMajorAxisProperty() { return inputSemiMajorAxis; }
    
    public double getInputSemiMinorAxis() { return inputSemiMinorAxis.get(); }
    public void setInputSemiMinorAxis(double value) { inputSemiMinorAxis.set(value); }
    public DoubleProperty inputSemiMinorAxisProperty() { return inputSemiMinorAxis; }
    
    public double getInputLength() { return inputLength.get(); }
    public void setInputLength(double value) { inputLength.set(value); }
    public DoubleProperty inputLengthProperty() { return inputLength; }
    
    public double getInputAngle() { return inputAngle.get(); }
    public void setInputAngle(double value) { inputAngle.set(value); }
    public DoubleProperty inputAngleProperty() { return inputAngle; }
    
    public boolean isUseManualInput() { return useManualInput.get(); }
    public void setUseManualInput(boolean value) { useManualInput.set(value); }
    public BooleanProperty useManualInputProperty() { return useManualInput; }

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

    /**
     * Сбрасывает состояние рисования: очищает накопленные точки, позицию мыши
     * и флаг ручного ввода.
     */
    public void reset() {
        collectedPoints.clear();
        currentMousePosition.set(null);
        isDrawing.set(false);
        useManualInput.set(false);
    }

    public int getRequiredPointCount() {
        CreationMethod method = getCreationMethod();
        if (method == null) return 0;
        
        return switch (method) {
            case SEGMENT_TWO_POINTS, CIRCLE_CENTER_RADIUS, CIRCLE_CENTER_DIAMETER, 
                 CIRCLE_TWO_POINTS, RECT_TWO_POINTS, POLYGON_CENTER_RADIUS -> 2;
            case CIRCLE_THREE_POINTS, ARC_THREE_POINTS, ARC_CENTER_ANGLES, 
                 ELLIPSE_CENTER_AXES -> 3; // Эллипс теперь 3 точки!
            case RECT_CORNER_SIZE, RECT_CENTER_SIZE -> 2;
            case SPLINE_POINTS -> -1;
        };
    }

    /**
     * Проверяет, набрано ли достаточно точек для завершения создания примитива.
     * Для сплайна всегда возвращает {@code false} — он завершается Enter.
     */
    public boolean hasEnoughPoints() {
        int required = getRequiredPointCount();
        if (required < 0) return false;
        return collectedPoints.size() >= required;
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
            default -> null;
        };
        creationMethod.set(defaultMethod);
    }

    /**
     * Формирует пошаговую подсказку для статусной строки:
     * объясняет пользователю, какую точку надо указать следующей.
     */
    public String getHint() {
        if (getCurrentTool() == Tool.SELECT) {
            return "Выберите примитив для редактирования";
        }
        
        CreationMethod method = getCreationMethod();
        if (method == null) return "";
        
        int collected = collectedPoints.size();
        
        return switch (method) {
            case SEGMENT_TWO_POINTS -> collected == 0 ? "Укажите начальную точку" : "Укажите конечную точку";
            case CIRCLE_CENTER_RADIUS, CIRCLE_CENTER_DIAMETER -> 
                collected == 0 ? "Укажите центр окружности" : "Укажите точку на окружности (или введите радиус)";
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
                    case 1 -> "Укажите конец большой оси (или введите длину)";
                    default -> "Укажите конец малой оси (или введите ширину)";
                };
            case POLYGON_CENTER_RADIUS -> 
                collected == 0 ? "Укажите центр многоугольника" : "Укажите вершину (или введите радиус)";
            case SPLINE_POINTS -> 
                collected < 2 ? "Добавляйте точки (мин. 2). Enter для завершения" :
                "Добавляйте точки. Enter для завершения";
        };
    }
}


