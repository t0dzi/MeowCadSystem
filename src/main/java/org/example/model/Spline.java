package org.example.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Сплайн - гладкая кривая, проходящая через набор контрольных точек.
 * Реализован как кубический сплайн Катмулла-Рома.
 * 
 * Способы создания:
 * - По набору контрольных точек
 * 
 * Редактирование:
 * - Добавление, удаление и перемещение контрольных точек
 */
public class Spline extends Primitive {

    private List<Point> controlPoints;
    
    /** Замкнут ли сплайн */
    private boolean closed;
    
    private double tension;


    public Spline(List<Point> controlPoints, LineStyle lineStyle) {
        super(lineStyle);
        this.controlPoints = new ArrayList<>(controlPoints);
        this.closed = false;
        this.tension = 0.5; // Стандартное натяжение для Catmull-Rom
    }

    public Spline(List<Point> controlPoints, boolean closed, double tension, LineStyle lineStyle) {
        super(lineStyle);
        this.controlPoints = new ArrayList<>(controlPoints);
        this.closed = closed;
        this.tension = Math.max(0, Math.min(1, tension));
    }


    public List<Point> getControlPointsList() {
        return new ArrayList<>(controlPoints);
    }

    public void setControlPoints(List<Point> controlPoints) {
        this.controlPoints = new ArrayList<>(controlPoints);
    }

    public int getPointCount() {
        return controlPoints.size();
    }

    public Point getControlPoint(int index) {
        if (index >= 0 && index < controlPoints.size()) {
            return controlPoints.get(index);
        }
        return null;
    }

    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    public double getTension() {
        return tension;
    }

    public void setTension(double tension) {
        this.tension = Math.max(0, Math.min(1, tension));
    }


    public void addControlPoint(Point point) {
        controlPoints.add(point);
    }

    public void insertControlPoint(int index, Point point) {
        if (index >= 0 && index <= controlPoints.size()) {
            controlPoints.add(index, point);
        }
    }

    /**
     * Удаляет контрольную точку по индексу.
     * @return true, если точка удалена (минимум 2 точки должны остаться)
     */
    public boolean removeControlPoint(int index) {
        if (controlPoints.size() > 2 && index >= 0 && index < controlPoints.size()) {
            controlPoints.remove(index);
            return true;
        }
        return false;
    }

    public void setControlPoint(int index, Point newPosition) {
        if (index >= 0 && index < controlPoints.size()) {
            controlPoints.set(index, newPosition);
        }
    }


    /**
     * Возвращает интерполированную точку на сплайне.
     * @param t параметр от 0 до (количество сегментов)
     */
    public Point getPointAt(double t) {
        int n = controlPoints.size();
        if (n < 2) return n > 0 ? controlPoints.get(0) : new Point(0, 0);

        int segments = closed ? n : n - 1;
        int segment = (int) Math.floor(t);
        segment = Math.max(0, Math.min(segment, segments - 1));
        double localT = t - segment;

        Point p0, p1, p2, p3;
        if (closed) {
            p0 = controlPoints.get((segment - 1 + n) % n);
            p1 = controlPoints.get(segment % n);
            p2 = controlPoints.get((segment + 1) % n);
            p3 = controlPoints.get((segment + 2) % n);
        } else {
            p0 = controlPoints.get(Math.max(0, segment - 1));
            p1 = controlPoints.get(segment);
            p2 = controlPoints.get(Math.min(n - 1, segment + 1));
            p3 = controlPoints.get(Math.min(n - 1, segment + 2));
        }

        return catmullRom(p0, p1, p2, p3, localT, tension);
    }

    private Point catmullRom(Point p0, Point p1, Point p2, Point p3, double t, double tension) {
        double t2 = t * t;
        double t3 = t2 * t;

        double s = (1 - tension) / 2;

        double b0 = -s * t3 + 2 * s * t2 - s * t;
        double b1 = (2 - s) * t3 + (s - 3) * t2 + 1;
        double b2 = (s - 2) * t3 + (3 - 2 * s) * t2 + s * t;
        double b3 = s * t3 - s * t2;

        double x = b0 * p0.getX() + b1 * p1.getX() + b2 * p2.getX() + b3 * p3.getX();
        double y = b0 * p0.getY() + b1 * p1.getY() + b2 * p2.getY() + b3 * p3.getY();

        return new Point(x, y);
    }

    /**
     * Возвращает список точек для отрисовки сплайна.
     * @param pointsPerSegment количество точек на сегмент
     */
    public List<Point> getSplinePoints(int pointsPerSegment) {
        List<Point> result = new ArrayList<>();
        int n = controlPoints.size();
        if (n < 2) {
            result.addAll(controlPoints);
            return result;
        }

        int segments = closed ? n : n - 1;
        
        for (int seg = 0; seg < segments; seg++) {
            for (int i = 0; i < pointsPerSegment; i++) {
                double t = seg + (double) i / pointsPerSegment;
                result.add(getPointAt(t));
            }
        }
        
        if (closed) {
            result.add(getPointAt(0)); // Замыкаем
        } else {
            result.add(controlPoints.get(n - 1));
        }
        
        return result;
    }

    public double getLength() {
        List<Point> points = getSplinePoints(20);
        double length = 0;
        for (int i = 1; i < points.size(); i++) {
            length += distance(points.get(i - 1), points.get(i));
        }
        return length;
    }


    @Override
    public PrimitiveType getType() {
        return PrimitiveType.SPLINE;
    }

    @Override
    public List<ControlPoint> getControlPoints() {
        List<ControlPoint> points = new ArrayList<>();
        for (int i = 0; i < controlPoints.size(); i++) {
            points.add(new ControlPoint(
                controlPoints.get(i), 
                ControlPoint.Type.CONTROL, 
                i, 
                "Точка " + (i + 1)
            ));
        }
        return points;
    }

    @Override
    public void moveControlPoint(int pointIndex, Point newPosition) {
        setControlPoint(pointIndex, newPosition);
    }

    @Override
    public void translate(double dx, double dy) {
        List<Point> newPoints = new ArrayList<>();
        for (Point p : controlPoints) {
            newPoints.add(new Point(p.getX() + dx, p.getY() + dy));
        }
        controlPoints = newPoints;
    }

    @Override
    public boolean containsPoint(Point point, double tolerance) {
        // Проверяем расстояние до сегментов сплайна
        List<Point> splinePoints = getSplinePoints(10);
        
        for (int i = 1; i < splinePoints.size(); i++) {
            Point p1 = splinePoints.get(i - 1);
            Point p2 = splinePoints.get(i);
            if (distanceToSegment(point, p1, p2) < tolerance) {
                return true;
            }
        }
        return false;
    }

    @Override
    public double[] getBoundingBox() {
        if (controlPoints.isEmpty()) {
            return new double[]{0, 0, 0, 0};
        }

        // Используем точки сплайна для более точного bounding box
        List<Point> splinePoints = getSplinePoints(10);
        
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;

        for (Point p : splinePoints) {
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
        }

        return new double[]{minX, minY, maxX, maxY};
    }

    @Override
    public Point getCenter() {
        if (controlPoints.isEmpty()) {
            return new Point(0, 0);
        }
        
        double sumX = 0, sumY = 0;
        for (Point p : controlPoints) {
            sumX += p.getX();
            sumY += p.getY();
        }
        return new Point(sumX / controlPoints.size(), sumY / controlPoints.size());
    }

    @Override
    public Map<String, Object> getProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put("Количество точек", controlPoints.size());
        props.put("Замкнут", closed);
        props.put("Натяжение", tension);
        props.put("Длина", getLength());
        
        // Добавляем координаты контрольных точек
        for (int i = 0; i < controlPoints.size(); i++) {
            Point p = controlPoints.get(i);
            props.put("Точка " + (i + 1) + " X", p.getX());
            props.put("Точка " + (i + 1) + " Y", p.getY());
        }
        
        return props;
    }

    @Override
    public boolean setProperty(String propertyName, Object value) {
        try {
            if (propertyName.equals("Замкнут")) {
                closed = value instanceof Boolean ? (Boolean) value : Boolean.parseBoolean(value.toString());
                return true;
            } else if (propertyName.equals("Натяжение")) {
                setTension(parseDouble(value));
                return true;
            } else if (propertyName.startsWith("Точка ") && propertyName.endsWith(" X")) {
                int index = extractPointIndex(propertyName);
                if (index >= 0 && index < controlPoints.size()) {
                    Point old = controlPoints.get(index);
                    controlPoints.set(index, new Point(parseDouble(value), old.getY()));
                    return true;
                }
            } else if (propertyName.startsWith("Точка ") && propertyName.endsWith(" Y")) {
                int index = extractPointIndex(propertyName);
                if (index >= 0 && index < controlPoints.size()) {
                    Point old = controlPoints.get(index);
                    controlPoints.set(index, new Point(old.getX(), parseDouble(value)));
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private int extractPointIndex(String propertyName) {
        try {
            String[] parts = propertyName.split(" ");
            return Integer.parseInt(parts[1]) - 1;
        } catch (Exception e) {
            return -1;
        }
    }

    private double parseDouble(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(value.toString());
    }
}



