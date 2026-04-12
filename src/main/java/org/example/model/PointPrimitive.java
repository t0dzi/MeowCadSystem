package org.example.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Точка — геометрический примитив САПР.
 * Соответствует объекту DXF POINT.
 */
public class PointPrimitive extends Primitive {

    /** Координаты точки */
    private Point position;


    /**
     * Создаёт точку по координатам.
     */
    public PointPrimitive(Point position, LineStyle lineStyle) {
        super(lineStyle);
        this.position = position;
    }

    /**
     * Создаёт точку по X и Y координатам.
     */
    public PointPrimitive(double x, double y, LineStyle lineStyle) {
        this(new Point(x, y), lineStyle);
    }


    public Point getPosition() {
        return position;
    }

    public void setPosition(Point position) {
        this.position = position;
    }


    @Override
    public PrimitiveType getType() {
        return PrimitiveType.POINT;
    }

    @Override
    public Point getCenter() {
        return position;
    }

    @Override
    public List<ControlPoint> getControlPoints() {
        List<ControlPoint> pts = new ArrayList<>();
        pts.add(new ControlPoint(position, ControlPoint.Type.ENDPOINT, 0, "Положение"));
        return pts;
    }

    @Override
    public void moveControlPoint(int pointIndex, Point newPosition) {
        if (pointIndex == 0) {
            position = newPosition;
        }
    }

    @Override
    public void translate(double dx, double dy) {
        position = new Point(position.getX() + dx, position.getY() + dy);
    }

    @Override
    public boolean containsPoint(Point point, double tolerance) {
        return distance(point, position) <= tolerance;
    }

    @Override
    public double[] getBoundingBox() {
        // Точка — вырожденный прямоугольник (минимальный размер для корректности границ)
        double margin = 1.0;
        return new double[]{
            position.getX() - margin,
            position.getY() - margin,
            position.getX() + margin,
            position.getY() + margin
        };
    }

    @Override
    public Map<String, Object> getProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put("X", position.getX());
        props.put("Y", position.getY());
        return props;
    }

    @Override
    public boolean setProperty(String propertyName, Object value) {
        try {
            double val = value instanceof Number
                    ? ((Number) value).doubleValue()
                    : Double.parseDouble(value.toString());
            switch (propertyName) {
                case "X" -> position = new Point(val, position.getY());
                case "Y" -> position = new Point(position.getX(), val);
                default -> { return false; }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}


