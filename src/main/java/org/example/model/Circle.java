package org.example.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Окружность - геометрический примитив САПР.
 * Определяется центром и радиусом.
 * 
 * Способы создания:
 * - Центр и радиус
 * - Центр и диаметр
 * - Две точки (диаметр)
 * - Три точки на окружности
 */
public class Circle extends Primitive {

    public enum CreationMode {
        /** Центр и радиус */
        CENTER_RADIUS,
        CENTER_DIAMETER,
        TWO_POINTS,
        /** Три точки на окружности */
        THREE_POINTS
    }

    private Point center;
    private double radius;
    private final CreationMode creationMode;


    /**
     * Создаёт окружность по центру и радиусу.
     */
    public Circle(Point center, double radius, LineStyle lineStyle) {
        super(lineStyle);
        this.center = center;
        this.radius = Math.abs(radius);
        this.creationMode = CreationMode.CENTER_RADIUS;
    }

    /**
     * Создаёт окружность по двум точкам (концы диаметра).
     */
    public static Circle fromTwoPoints(Point p1, Point p2, LineStyle lineStyle) {
        double cx = (p1.getX() + p2.getX()) / 2;
        double cy = (p1.getY() + p2.getY()) / 2;
        double radius = distance(p1, p2) / 2;
        
        Circle circle = new Circle(new Point(cx, cy), radius, lineStyle);
        return circle;
    }

    /**
     * Создаёт окружность по трём точкам на окружности.
     * Использует алгоритм нахождения описанной окружности треугольника.
     */
    public static Circle fromThreePoints(Point p1, Point p2, Point p3, LineStyle lineStyle) {
        double x1 = p1.getX(), y1 = p1.getY();
        double x2 = p2.getX(), y2 = p2.getY();
        double x3 = p3.getX(), y3 = p3.getY();

        double a = x1 * (y2 - y3) - y1 * (x2 - x3) + x2 * y3 - x3 * y2;
        
        if (Math.abs(a) < 1e-10) {
            return null; // Точки лежат на одной прямой
        }

        double b = (x1 * x1 + y1 * y1) * (y3 - y2) 
                 + (x2 * x2 + y2 * y2) * (y1 - y3) 
                 + (x3 * x3 + y3 * y3) * (y2 - y1);

        double c = (x1 * x1 + y1 * y1) * (x2 - x3) 
                 + (x2 * x2 + y2 * y2) * (x3 - x1) 
                 + (x3 * x3 + y3 * y3) * (x1 - x2);

        double cx = -b / (2 * a);
        double cy = -c / (2 * a);
        double radius = Math.sqrt((cx - x1) * (cx - x1) + (cy - y1) * (cy - y1));

        return new Circle(new Point(cx, cy), radius, lineStyle);
    }

    /**
     * Создаёт окружность по центру и точке на окружности.
     */
    public static Circle fromCenterAndPoint(Point center, Point pointOnCircle, LineStyle lineStyle) {
        double radius = distance(center, pointOnCircle);
        return new Circle(center, radius, lineStyle);
    }


    public Point getCenter() {
        return center;
    }

    public void setCenter(Point center) {
        this.center = center;
    }

    public double getRadius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = Math.abs(radius);
    }

    public double getDiameter() {
        return radius * 2;
    }

    public void setDiameter(double diameter) {
        this.radius = Math.abs(diameter) / 2;
    }

    public double getCircumference() {
        return 2 * Math.PI * radius;
    }

    public double getArea() {
        return Math.PI * radius * radius;
    }

    public CreationMode getCreationMode() {
        return creationMode;
    }


    @Override
    public PrimitiveType getType() {
        return PrimitiveType.CIRCLE;
    }

    @Override
    public List<ControlPoint> getControlPoints() {
        List<ControlPoint> points = new ArrayList<>();
        
        // Центр окружности
        points.add(new ControlPoint(center, ControlPoint.Type.CENTER, 0, "Центр"));
        
        // Точки на окружности для изменения радиуса (по 4 направлениям)
        points.add(new ControlPoint(
            new Point(center.getX() + radius, center.getY()), 
            ControlPoint.Type.RADIUS, 1, "Радиус"
        ));
        points.add(new ControlPoint(
            new Point(center.getX(), center.getY() + radius), 
            ControlPoint.Type.RADIUS, 2, "Радиус"
        ));
        points.add(new ControlPoint(
            new Point(center.getX() - radius, center.getY()), 
            ControlPoint.Type.RADIUS, 3, "Радиус"
        ));
        points.add(new ControlPoint(
            new Point(center.getX(), center.getY() - radius), 
            ControlPoint.Type.RADIUS, 4, "Радиус"
        ));
        
        return points;
    }

    @Override
    public void moveControlPoint(int pointIndex, Point newPosition) {
        if (pointIndex == 0) {
            center = newPosition;
        } else {
            // Изменение радиуса
            radius = distance(center, newPosition);
        }
    }

    @Override
    public void translate(double dx, double dy) {
        center = new Point(center.getX() + dx, center.getY() + dy);
    }

    @Override
    public boolean containsPoint(Point point, double tolerance) {
        return distanceToCircle(point, center, radius) < tolerance;
    }

    @Override
    public double[] getBoundingBox() {
        return new double[]{
            center.getX() - radius,
            center.getY() - radius,
            center.getX() + radius,
            center.getY() + radius
        };
    }

    @Override
    public Map<String, Object> getProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put("Центр X", center.getX());
        props.put("Центр Y", center.getY());
        props.put("Радиус", radius);
        props.put("Диаметр", getDiameter());
        props.put("Длина окружности", getCircumference());
        props.put("Площадь", getArea());
        return props;
    }

    @Override
    public boolean setProperty(String propertyName, Object value) {
        try {
            double val = value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(value.toString());
            switch (propertyName) {
                case "Центр X" -> center = new Point(val, center.getY());
                case "Центр Y" -> center = new Point(center.getX(), val);
                case "Радиус" -> setRadius(val);
                case "Диаметр" -> setDiameter(val);
                default -> { return false; }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}








