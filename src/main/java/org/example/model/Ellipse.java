package org.example.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Эллипс - геометрический примитив САПР.
 * Определяется центром и двумя полуосями (большой и малой).
 * 
 * Способы создания:
 * - Центр и две конечные точки осей
 * - Центр и длины полуосей
 */
public class Ellipse extends Primitive {

    public enum CreationMode {
        CENTER_AXES_POINTS,
        CENTER_AXES_LENGTH
    }

    private Point center;
    private double semiMajorAxis;
    private double semiMinorAxis;
    /** Угол поворота эллипса (угол большой оси относительно оси X) в радианах */
    private double rotation;
    private final CreationMode creationMode;


    /**
     * Создаёт эллипс по центру и полуосям.
     */
    public Ellipse(Point center, double semiMajorAxis, double semiMinorAxis, LineStyle lineStyle) {
        super(lineStyle);
        this.center = center;
        this.semiMajorAxis = Math.abs(semiMajorAxis);
        this.semiMinorAxis = Math.abs(semiMinorAxis);
        this.rotation = 0;
        this.creationMode = CreationMode.CENTER_AXES_LENGTH;
    }

    /**
     * Создаёт эллипс по центру, полуосям и углу поворота.
     */
    public Ellipse(Point center, double semiMajorAxis, double semiMinorAxis, 
                   double rotation, LineStyle lineStyle) {
        super(lineStyle);
        this.center = center;
        this.semiMajorAxis = Math.abs(semiMajorAxis);
        this.semiMinorAxis = Math.abs(semiMinorAxis);
        this.rotation = rotation;
        this.creationMode = CreationMode.CENTER_AXES_LENGTH;
    }

    /**
     * Создаёт эллипс по центру и двум точкам на осях.
     * @param center центр эллипса
     * @param majorAxisPoint точка на конце большой оси
     * @param minorAxisPoint точка на конце малой оси
     */
    public static Ellipse fromCenterAndAxisPoints(Point center, Point majorAxisPoint, 
                                                   Point minorAxisPoint, LineStyle lineStyle) {
        double semiMajor = distance(center, majorAxisPoint);
        double semiMinor = distance(center, minorAxisPoint);
        double rotation = Math.atan2(
            majorAxisPoint.getY() - center.getY(),
            majorAxisPoint.getX() - center.getX()
        );
        return new Ellipse(center, semiMajor, semiMinor, rotation, lineStyle);
    }


    @Override
    public Point getCenter() {
        return center;
    }

    public void setCenter(Point center) {
        this.center = center;
    }

    public double getSemiMajorAxis() {
        return semiMajorAxis;
    }

    public void setSemiMajorAxis(double semiMajorAxis) {
        this.semiMajorAxis = Math.abs(semiMajorAxis);
    }

    public double getSemiMinorAxis() {
        return semiMinorAxis;
    }

    public void setSemiMinorAxis(double semiMinorAxis) {
        this.semiMinorAxis = Math.abs(semiMinorAxis);
    }

    public double getMajorAxis() {
        return semiMajorAxis * 2;
    }

    public double getMinorAxis() {
        return semiMinorAxis * 2;
    }

    public double getRotation() {
        return rotation;
    }

    public double getRotationDegrees() {
        return Math.toDegrees(rotation);
    }

    public void setRotation(double rotation) {
        this.rotation = rotation;
    }

    public void setRotationDegrees(double degrees) {
        this.rotation = Math.toRadians(degrees);
    }

    public CreationMode getCreationMode() {
        return creationMode;
    }

    /** Возвращает эксцентриситет эллипса */
    public double getEccentricity() {
        double a = Math.max(semiMajorAxis, semiMinorAxis);
        double b = Math.min(semiMajorAxis, semiMinorAxis);
        return Math.sqrt(1 - (b * b) / (a * a));
    }

    /** Возвращает приблизительный периметр эллипса (формула Рамануджана) */
    public double getPerimeter() {
        double a = semiMajorAxis;
        double b = semiMinorAxis;
        double h = Math.pow(a - b, 2) / Math.pow(a + b, 2);
        return Math.PI * (a + b) * (1 + 3 * h / (10 + Math.sqrt(4 - 3 * h)));
    }

    /** Возвращает площадь эллипса */
    public double getArea() {
        return Math.PI * semiMajorAxis * semiMinorAxis;
    }


    /** Возвращает точку на эллипсе по параметру t (угол в радианах) */
    public Point getPointAtAngle(double t) {
        double x = semiMajorAxis * Math.cos(t);
        double y = semiMinorAxis * Math.sin(t);
        
        double cos = Math.cos(rotation);
        double sin = Math.sin(rotation);
        double rotX = x * cos - y * sin;
        double rotY = x * sin + y * cos;
        
        return new Point(center.getX() + rotX, center.getY() + rotY);
    }

    public Point getMajorAxisEndPoint() {
        return getPointAtAngle(0);
    }

    public Point getMinorAxisEndPoint() {
        return getPointAtAngle(Math.PI / 2);
    }


    @Override
    public PrimitiveType getType() {
        return PrimitiveType.ELLIPSE;
    }

    @Override
    public List<ControlPoint> getControlPoints() {
        List<ControlPoint> points = new ArrayList<>();
        
        points.add(new ControlPoint(center, ControlPoint.Type.CENTER, 0, "Центр"));
        
        points.add(new ControlPoint(getPointAtAngle(0), ControlPoint.Type.AXIS, 1, "Большая ось +"));
        points.add(new ControlPoint(getPointAtAngle(Math.PI), ControlPoint.Type.AXIS, 2, "Большая ось -"));
        
        points.add(new ControlPoint(getPointAtAngle(Math.PI / 2), ControlPoint.Type.AXIS, 3, "Малая ось +"));
        points.add(new ControlPoint(getPointAtAngle(3 * Math.PI / 2), ControlPoint.Type.AXIS, 4, "Малая ось -"));
        
        return points;
    }

    @Override
    public void moveControlPoint(int pointIndex, Point newPosition) {
        switch (pointIndex) {
            case 0 -> {
                center = newPosition;
            }
            case 1, 2 -> {
                semiMajorAxis = distance(center, newPosition);
                if (pointIndex == 1) {
                    rotation = Math.atan2(
                        newPosition.getY() - center.getY(),
                        newPosition.getX() - center.getX()
                    );
                }
            }
            case 3, 4 -> {
                semiMinorAxis = distance(center, newPosition);
            }
        }
    }

    @Override
    public void translate(double dx, double dy) {
        center = new Point(center.getX() + dx, center.getY() + dy);
    }

    @Override
    public boolean containsPoint(Point point, double tolerance) {
        // Преобразуем точку в локальные координаты эллипса
        double dx = point.getX() - center.getX();
        double dy = point.getY() - center.getY();
        
        double cos = Math.cos(-rotation);
        double sin = Math.sin(-rotation);
        double localX = dx * cos - dy * sin;
        double localY = dx * sin + dy * cos;
        
        // Уравнение эллипса: (x/a)² + (y/b)² = 1
        // Расстояние до эллипса приближённо
        double normalizedDist = Math.sqrt(
            (localX * localX) / (semiMajorAxis * semiMajorAxis) +
            (localY * localY) / (semiMinorAxis * semiMinorAxis)
        );
        
        // Для точки на эллипсе normalizedDist = 1
        double approxDistToEllipse = Math.abs(normalizedDist - 1) * 
            Math.min(semiMajorAxis, semiMinorAxis);
        
        return approxDistToEllipse < tolerance;
    }

    @Override
    public double[] getBoundingBox() {
        // Для повёрнутого эллипса нужно вычислить корректный bounding box
        double cos = Math.cos(rotation);
        double sin = Math.sin(rotation);
        
        // Полуширина и полувысота bounding box
        double halfWidth = Math.sqrt(
            semiMajorAxis * semiMajorAxis * cos * cos + 
            semiMinorAxis * semiMinorAxis * sin * sin
        );
        double halfHeight = Math.sqrt(
            semiMajorAxis * semiMajorAxis * sin * sin + 
            semiMinorAxis * semiMinorAxis * cos * cos
        );
        
        return new double[]{
            center.getX() - halfWidth,
            center.getY() - halfHeight,
            center.getX() + halfWidth,
            center.getY() + halfHeight
        };
    }

    @Override
    public Map<String, Object> getProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put("Центр X", center.getX());
        props.put("Центр Y", center.getY());
        props.put("Большая полуось", semiMajorAxis);
        props.put("Малая полуось", semiMinorAxis);
        props.put("Угол поворота (°)", getRotationDegrees());
        props.put("Эксцентриситет", getEccentricity());
        props.put("Периметр", getPerimeter());
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
                case "Большая полуось" -> setSemiMajorAxis(val);
                case "Малая полуось" -> setSemiMinorAxis(val);
                case "Угол поворота (°)" -> setRotationDegrees(val);
                default -> { return false; }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}








