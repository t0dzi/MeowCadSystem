package org.example.model;

import java.util.List;
import java.util.Map;

/**
 * Базовый абстрактный класс для всех геометрических примитивов САПР.
 * Определяет общий интерфейс для отрисовки, редактирования и взаимодействия.
 * Все фигуры наследуются от этого класса.
 * Любая фигура должна: отдавать свой тип, переносить себя и отдавать свои точки
 */
public abstract class Primitive {

    protected LineStyle lineStyle;

    protected String layerName = Layer.DEFAULT_LAYER_NAME;

    /**
     * Цвет ACI (AutoCAD Color Index), 1–255.
     * Значение −1 означает «не задан» — цвет наследуется от слоя.
     */
    protected int colorAci = -1;

    protected final long id;

    private static long idCounter = 0;

    protected Primitive(LineStyle lineStyle) {
        this.id = ++idCounter;
        this.lineStyle = lineStyle;
    }


    public long getId() {
        return id;
    }

    public LineStyle getLineStyle() {
        return lineStyle;
    }

    public void setLineStyle(LineStyle lineStyle) {
        this.lineStyle = lineStyle;
    }

    public String getLayerName() {
        return layerName;
    }

    public void setLayerName(String layerName) {
        this.layerName = layerName != null ? layerName : Layer.DEFAULT_LAYER_NAME;
    }

    /**
     * Возвращает индекс цвета ACI (1–255) или −1, если цвет не задан явно.
     */
    public int getColorAci() {
        return colorAci;
    }

    /**
     * Устанавливает индекс цвета ACI.
     *
     * @param colorAci значение 1–255, или −1 для наследования от слоя
     */
    public void setColorAci(int colorAci) {
        this.colorAci = colorAci;
    }

    public abstract PrimitiveType getType();

    public abstract List<ControlPoint> getControlPoints();

    /**
     * Перемещает контрольную точку в новую позицию.
     * 
     * @param pointIndex  индекс контрольной точки
     * @param newPosition новая позиция в мировых координатах
     */
    public abstract void moveControlPoint(int pointIndex, Point newPosition);

    /**
     * Перемещает весь примитив на заданное смещение.
     * 
     * @param dx смещение по X
     * @param dy смещение по Y
     */
    public abstract void translate(double dx, double dy);

    /**
     * Проверяет, находится ли точка вблизи примитива (для выделения).
     * 
     * @param point     точка в мировых координатах
     * @param tolerance допуск в мировых единицах
     * @return true, если точка находится достаточно близко к примитиву
     */
    public abstract boolean containsPoint(Point point, double tolerance);

    /**
     * Возвращает ограничивающий прямоугольник примитива.
     * 
     * @return массив из 4 значений: [minX, minY, maxX, maxY]
     */
    public abstract double[] getBoundingBox();


    /**
     * Возвращает редактируемые свойства примитива для панели свойств.
     * 
     * @return карта "название свойства" -> "текущее значение"
     */
    public abstract Map<String, Object> getProperties();

    /**
     * Устанавливает значение свойства по имени.
     * 
     * @param propertyName имя свойства
     * @param value        новое значение
     * @return true, если свойство успешно изменено
     */
    public abstract boolean setProperty(String propertyName, Object value);

    public abstract Point getCenter();


    protected static double distanceToSegment(Point p, Point segStart, Point segEnd) {
        double px = p.getX(), py = p.getY();
        double x1 = segStart.getX(), y1 = segStart.getY();
        double x2 = segEnd.getX(), y2 = segEnd.getY();

        double dx = x2 - x1;
        double dy = y2 - y1;

        if (dx == 0 && dy == 0) {
            return Math.sqrt((px - x1) * (px - x1) + (py - y1) * (py - y1));
        }

        double t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy);
        t = Math.max(0, Math.min(1, t));

        double closestX = x1 + t * dx;
        double closestY = y1 + t * dy;

        return Math.sqrt((px - closestX) * (px - closestX) + (py - closestY) * (py - closestY));
    }

    protected static double distance(Point p1, Point p2) {
        double dx = p2.getX() - p1.getX();
        double dy = p2.getY() - p1.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Вычисляет расстояние от точки до окружности.
     */
    protected static double distanceToCircle(Point p, Point center, double radius) {
        return Math.abs(distance(p, center) - radius);
    }
}


