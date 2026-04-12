package org.example.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Отрезок — базовый примитив САПР.
 * Определяется двумя точками: начальной и конечной.
 * Поддерживает два режима создания: по декартовым ({@code CARTESIAN})
 * и полярным координатам ({@code POLAR}: длина + угол).
 */
public class Segment extends Primitive {

    public enum CreationMode {
        /** Создание по декартовым координатам (X, Y) */
        CARTESIAN,
        /** Создание по полярным координатам (длина, угол) */
        POLAR
    }

    private Point startPoint;
    private Point endPoint;
    private final CreationMode mode;

    public Segment(Point startPoint, Point endPoint, CreationMode mode, LineStyle lineStyle) {
        super(lineStyle);
        this.startPoint = startPoint;
        this.endPoint = endPoint;
        this.mode = mode;
    }


    public Point getStartPoint() { 
        return startPoint; 
    }
    
    public Point getEndPoint() { 
        return endPoint; 
    }
    
    public CreationMode getMode() { 
        return mode; 
    }

    public double getLength() {
        double dx = endPoint.getX() - startPoint.getX();
        double dy = endPoint.getY() - startPoint.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    public double getAngle() {
        double dx = endPoint.getX() - startPoint.getX();
        double dy = endPoint.getY() - startPoint.getY();
        return Math.atan2(dy, dx);
    }
    
    public double getAngleDegrees() {
        return Math.toDegrees(getAngle());
    }


    @Override
    public PrimitiveType getType() {
        return PrimitiveType.SEGMENT;
    }

    @Override
    public List<ControlPoint> getControlPoints() {
        List<ControlPoint> points = new ArrayList<>();
        points.add(new ControlPoint(startPoint, ControlPoint.Type.ENDPOINT, 0, "Начало"));
        points.add(new ControlPoint(endPoint, ControlPoint.Type.ENDPOINT, 1, "Конец"));
        return points;
    }

    @Override
    public void moveControlPoint(int pointIndex, Point newPosition) {
        switch (pointIndex) {
            case 0 -> startPoint = newPosition;
            case 1 -> endPoint = newPosition;
        }
    }

    @Override
    public void translate(double dx, double dy) {
        startPoint = new Point(startPoint.getX() + dx, startPoint.getY() + dy);
        endPoint = new Point(endPoint.getX() + dx, endPoint.getY() + dy);
    }

    /** Проверяет, находится ли точка в зоне достижимости от отрезка (tolerance в мировых единицах). */
    @Override
    public boolean containsPoint(Point point, double tolerance) {
        return distanceToSegment(point, startPoint, endPoint) < tolerance;
    }

    @Override
    public double[] getBoundingBox() {
        double minX = Math.min(startPoint.getX(), endPoint.getX());
        double minY = Math.min(startPoint.getY(), endPoint.getY());
        double maxX = Math.max(startPoint.getX(), endPoint.getX());
        double maxY = Math.max(startPoint.getY(), endPoint.getY());
        return new double[]{minX, minY, maxX, maxY};
    }

    @Override
    public Map<String, Object> getProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put("Начало X", startPoint.getX());
        props.put("Начало Y", startPoint.getY());
        props.put("Конец X", endPoint.getX());
        props.put("Конец Y", endPoint.getY());
        props.put("Длина", getLength());
        props.put("Угол (°)", getAngleDegrees());
        return props;
    }

    @Override
    public boolean setProperty(String propertyName, Object value) {
        try {
            double val = value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(value.toString());
            switch (propertyName) {
                case "Начало X" -> startPoint = new Point(val, startPoint.getY());
                case "Начало Y" -> startPoint = new Point(startPoint.getX(), val);
                case "Конец X" -> endPoint = new Point(val, endPoint.getY());
                case "Конец Y" -> endPoint = new Point(endPoint.getX(), val);
                case "Длина" -> {
                    double angle = getAngle();
                    endPoint = new Point(
                        startPoint.getX() + val * Math.cos(angle),
                        startPoint.getY() + val * Math.sin(angle)
                    );
                }
                case "Угол (°)" -> {
                    double length = getLength();
                    double rad = Math.toRadians(val);
                    endPoint = new Point(
                        startPoint.getX() + length * Math.cos(rad),
                        startPoint.getY() + length * Math.sin(rad)
                    );
                }
                default -> { return false; }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public Point getCenter() {
        return new Point(
            (startPoint.getX() + endPoint.getX()) / 2,
            (startPoint.getY() + endPoint.getY()) / 2
        );
    }
}


