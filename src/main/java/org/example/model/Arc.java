package org.example.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Дуга окружности - геометрический примитив САПР.
 * Определяется центром, радиусом, начальным и конечным углами.
 * 
 * Способы создания:
 * - Три точки (начало, промежуточная, конец)
 * - Центр, радиус, начальный и конечный углы
 */
public class Arc extends Primitive {

    public enum CreationMode {
        /** Три точки: начальная, промежуточная (на дуге), конечная */
        THREE_POINTS,
        /** Центр, радиус и углы */
        CENTER_ANGLES
    }

    private Point center;
    private double radius;
    private double startAngle;
    private double endAngle;
    private final CreationMode creationMode;


    /**
     * Создаёт дугу по центру, радиусу и углам.
     * 
     * @param center     центр дуги
     * @param radius     радиус
     * @param startAngle начальный угол в радианах
     * @param endAngle   конечный угол в радианах
     */
    public Arc(Point center, double radius, double startAngle, double endAngle, LineStyle lineStyle) {
        super(lineStyle);
        this.center = center;
        this.radius = Math.abs(radius);
        this.startAngle = normalizeAngle(startAngle);
        this.endAngle = normalizeAngle(endAngle);
        this.creationMode = CreationMode.CENTER_ANGLES;
    }

    /**
     * Создаёт дугу по трём точкам.
     * 
     * @param startPoint начальная точка дуги
     * @param midPoint   промежуточная точка на дуге
     * @param endPoint   конечная точка дуги
     */
    public static Arc fromThreePoints(Point startPoint, Point midPoint, Point endPoint, LineStyle lineStyle) {
        // Находим центр и радиус описанной окружности
        double x1 = startPoint.getX(), y1 = startPoint.getY();
        double x2 = midPoint.getX(), y2 = midPoint.getY();
        double x3 = endPoint.getX(), y3 = endPoint.getY();

        double a = x1 * (y2 - y3) - y1 * (x2 - x3) + x2 * y3 - x3 * y2;

        if (Math.abs(a) < 1e-10) {
            return null;
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

        Point center = new Point(cx, cy);

        double startAngle = Math.atan2(y1 - cy, x1 - cx);
        double midAngle = Math.atan2(y2 - cy, x2 - cx);
        double endAngle = Math.atan2(y3 - cy, x3 - cx);

        // Определяем направление дуги (по или против часовой стрелки)
        // Дуга должна проходить через midPoint
        Arc arc = new Arc(center, radius, startAngle, endAngle, lineStyle);

        // Проверяем, проходит ли дуга через среднюю точку
        if (!arc.containsAngle(midAngle)) {
            arc = new Arc(center, radius, endAngle, startAngle, lineStyle);
        }

        return arc;
    }


    @Override
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

    public double getStartAngle() {
        return startAngle;
    }

    public double getStartAngleDegrees() {
        return Math.toDegrees(startAngle);
    }

    public void setStartAngle(double startAngle) {
        this.startAngle = normalizeAngle(startAngle);
    }

    public void setStartAngleDegrees(double degrees) {
        this.startAngle = normalizeAngle(Math.toRadians(degrees));
    }

    public double getEndAngle() {
        return endAngle;
    }

    public double getEndAngleDegrees() {
        return Math.toDegrees(endAngle);
    }

    public void setEndAngle(double endAngle) {
        this.endAngle = normalizeAngle(endAngle);
    }

    public void setEndAngleDegrees(double degrees) {
        this.endAngle = normalizeAngle(Math.toRadians(degrees));
    }

    public CreationMode getCreationMode() {
        return creationMode;
    }

    /** Возвращает начальную точку дуги */
    public Point getStartPoint() {
        return new Point(
                center.getX() + radius * Math.cos(startAngle),
                center.getY() + radius * Math.sin(startAngle));
    }

    /** Возвращает конечную точку дуги */
    public Point getEndPoint() {
        return new Point(
                center.getX() + radius * Math.cos(endAngle),
                center.getY() + radius * Math.sin(endAngle));
    }

    /** Возвращает угловую длину дуги в радианах */
    public double getSweepAngle() {
        double sweep = endAngle - startAngle;
        if (sweep < 0) {
            sweep += 2 * Math.PI;
        }
        return sweep;
    }

    /** Возвращает длину дуги */
    public double getArcLength() {
        return radius * getSweepAngle();
    }


    private static double normalizeAngle(double angle) {
        angle = angle % (2 * Math.PI);
        if (angle < 0)
            angle += 2 * Math.PI;
        return angle;
    }

    /** Проверяет, находится ли угол в пределах дуги */
    private boolean containsAngle(double angle) {
        angle = normalizeAngle(angle);
        double start = normalizeAngle(startAngle);
        double end = normalizeAngle(endAngle);

        if (start <= end) {
            return angle >= start && angle <= end;
        } else {
            // Дуга пересекает 0
            return angle >= start || angle <= end;
        }
    }


    @Override
    public PrimitiveType getType() {
        return PrimitiveType.ARC;
    }

    @Override
    public List<ControlPoint> getControlPoints() {
        List<ControlPoint> points = new ArrayList<>();

        // Центр дуги
        points.add(new ControlPoint(center, ControlPoint.Type.CENTER, 0, "Центр"));

        points.add(new ControlPoint(getStartPoint(), ControlPoint.Type.ANGLE, 1, "Начало"));

        points.add(new ControlPoint(getEndPoint(), ControlPoint.Type.ANGLE, 2, "Конец"));

        // Средняя точка для изменения радиуса
        double midAngle = startAngle + getSweepAngle() / 2;
        Point midPoint = new Point(
                center.getX() + radius * Math.cos(midAngle),
                center.getY() + radius * Math.sin(midAngle));
        points.add(new ControlPoint(midPoint, ControlPoint.Type.RADIUS, 3, "Радиус"));

        return points;
    }

    @Override
    public void moveControlPoint(int pointIndex, Point newPosition) {
        switch (pointIndex) {
            case 0 -> {
                center = newPosition;
            }
            case 1 -> {
                setStartAngle(Math.atan2(
                        newPosition.getY() - center.getY(),
                        newPosition.getX() - center.getX()));
            }
            case 2 -> {
                setEndAngle(Math.atan2(
                        newPosition.getY() - center.getY(),
                        newPosition.getX() - center.getX()));
            }
            case 3 -> {
                // Изменение радиуса
                radius = distance(center, newPosition);
            }
        }
    }

    @Override
    public void translate(double dx, double dy) {
        center = new Point(center.getX() + dx, center.getY() + dy);
    }

    @Override
    public boolean containsPoint(Point point, double tolerance) {
        // Сначала проверяем расстояние до окружности
        double distToCircle = distanceToCircle(point, center, radius);
        if (distToCircle > tolerance) {
            return false;
        }

        // Затем проверяем, находится ли точка в пределах углов дуги
        double angle = Math.atan2(
                point.getY() - center.getY(),
                point.getX() - center.getX());

        return containsAngle(angle);
    }

    @Override
    public double[] getBoundingBox() {
        // Начинаем с начальной и конечной точек дуги
        Point start = getStartPoint();
        Point end = getEndPoint();

        double minX = Math.min(start.getX(), end.getX());
        double minY = Math.min(start.getY(), end.getY());
        double maxX = Math.max(start.getX(), end.getX());
        double maxY = Math.max(start.getY(), end.getY());

        // Проверяем, попадают ли квадрантные экстремумы (0°, 90°, 180°, 270°) в дугу
        if (containsAngle(0)) {
            maxX = Math.max(maxX, center.getX() + radius);
        }
        if (containsAngle(Math.PI / 2)) {
            maxY = Math.max(maxY, center.getY() + radius);
        }
        if (containsAngle(Math.PI)) {
            minX = Math.min(minX, center.getX() - radius);
        }
        if (containsAngle(3 * Math.PI / 2)) {
            minY = Math.min(minY, center.getY() - radius);
        }

        return new double[] { minX, minY, maxX, maxY };
    }

    @Override
    public Map<String, Object> getProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put("Центр X", center.getX());
        props.put("Центр Y", center.getY());
        props.put("Радиус", radius);
        props.put("Начальный угол (°)", getStartAngleDegrees());
        props.put("Конечный угол (°)", getEndAngleDegrees());
        props.put("Длина дуги", getArcLength());
        return props;
    }

    @Override
    public boolean setProperty(String propertyName, Object value) {
        try {
            double val = value instanceof Number ? ((Number) value).doubleValue()
                    : Double.parseDouble(value.toString());
            switch (propertyName) {
                case "Центр X" -> center = new Point(val, center.getY());
                case "Центр Y" -> center = new Point(center.getX(), val);
                case "Радиус" -> setRadius(val);
                case "Начальный угол (°)" -> setStartAngleDegrees(val);
                case "Конечный угол (°)" -> setEndAngleDegrees(val);
                default -> {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}


