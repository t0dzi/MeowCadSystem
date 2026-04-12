package org.example.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Правильный многоугольник - геометрический примитив САПР.
 * Определяется центром, радиусом и количеством вершин.
 * 
 * Способы создания:
 * - Центр и радиус описанной окружности (вписанный многоугольник)
 * - Центр и радиус вписанной окружности (описанный многоугольник)
 */
public class Polygon extends Primitive {

    public enum InscriptionType {
        /** Многоугольник вписан в окружность (вершины на окружности) */
        INSCRIBED("Вписанный"),
        /** Многоугольник описан около окружности (стороны касаются окружности) */
        CIRCUMSCRIBED("Описанный");

        private final String displayName;

        InscriptionType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private Point center;
    /** Радиус окружности (описанной или вписанной, в зависимости от типа) */
    private double radius;
    private int sides;
    private InscriptionType inscriptionType;
    private double rotation;


    /**
     * Создаёт правильный многоугольник.
     * @param center центр многоугольника
     * @param radius радиус окружности
     * @param sides количество вершин (минимум 3)
     * @param inscriptionType тип построения
     * @param lineStyle стиль линии
     */
    public Polygon(Point center, double radius, int sides, 
                   InscriptionType inscriptionType, LineStyle lineStyle) {
        super(lineStyle);
        this.center = center;
        this.radius = Math.abs(radius);
        this.sides = Math.max(3, sides);
        this.inscriptionType = inscriptionType;
        this.rotation = 0;
    }

    public Polygon(Point center, double radius, int sides, 
                   InscriptionType inscriptionType, double rotation, LineStyle lineStyle) {
        super(lineStyle);
        this.center = center;
        this.radius = Math.abs(radius);
        this.sides = Math.max(3, sides);
        this.inscriptionType = inscriptionType;
        this.rotation = rotation;
    }

    public static Polygon fromCenterAndVertex(Point center, Point vertex, int sides, LineStyle lineStyle) {
        double radius = distance(center, vertex);
        double rotation = Math.atan2(
            vertex.getY() - center.getY(),
            vertex.getX() - center.getX()
        );
        return new Polygon(center, radius, sides, InscriptionType.INSCRIBED, rotation, lineStyle);
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

    public int getSides() {
        return sides;
    }

    public void setSides(int sides) {
        this.sides = Math.max(3, sides);
    }

    public InscriptionType getInscriptionType() {
        return inscriptionType;
    }

    public void setInscriptionType(InscriptionType inscriptionType) {
        this.inscriptionType = inscriptionType;
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


    /** Возвращает радиус описанной окружности (расстояние от центра до вершины) */
    public double getCircumradius() {
        if (inscriptionType == InscriptionType.INSCRIBED) {
            return radius;
        } else {
            return radius / Math.cos(Math.PI / sides);
        }
    }

    /** Возвращает радиус вписанной окружности (расстояние от центра до стороны) */
    public double getInradius() {
        if (inscriptionType == InscriptionType.CIRCUMSCRIBED) {
            return radius;
        } else {
            return radius * Math.cos(Math.PI / sides);
        }
    }

    public double getSideLength() {
        double R = getCircumradius();
        return 2 * R * Math.sin(Math.PI / sides);
    }

    public double getPerimeter() {
        return sides * getSideLength();
    }

    public double getArea() {
        double R = getCircumradius();
        return 0.5 * sides * R * R * Math.sin(2 * Math.PI / sides);
    }

    public double getInteriorAngle() {
        return (sides - 2) * 180.0 / sides;
    }


    public Point[] getVertices() {
        Point[] vertices = new Point[sides];
        double R = getCircumradius();
        double angleStep = 2 * Math.PI / sides;
        
        for (int i = 0; i < sides; i++) {
            double angle = rotation + i * angleStep;
            vertices[i] = new Point(
                center.getX() + R * Math.cos(angle),
                center.getY() + R * Math.sin(angle)
            );
        }
        return vertices;
    }


    @Override
    public PrimitiveType getType() {
        return PrimitiveType.POLYGON;
    }

    @Override
    public List<ControlPoint> getControlPoints() {
        List<ControlPoint> points = new ArrayList<>();
        Point[] vertices = getVertices();
        
        points.add(new ControlPoint(center, ControlPoint.Type.CENTER, 0, "Центр"));
        
        for (int i = 0; i < vertices.length; i++) {
            points.add(new ControlPoint(vertices[i], ControlPoint.Type.ENDPOINT, i + 1, "Вершина " + (i + 1)));
        }
        
        // Точка на первой стороне для изменения радиуса (апофема)
        if (inscriptionType == InscriptionType.CIRCUMSCRIBED) {
            double midAngle = rotation + Math.PI / sides;
            Point midSidePoint = new Point(
                center.getX() + radius * Math.cos(midAngle),
                center.getY() + radius * Math.sin(midAngle)
            );
            points.add(new ControlPoint(midSidePoint, ControlPoint.Type.RADIUS, sides + 1, "Радиус"));
        }
        
        return points;
    }

    @Override
    public void moveControlPoint(int pointIndex, Point newPosition) {
        if (pointIndex == 0) {
            center = newPosition;
        } else if (pointIndex <= sides) {
            // Перемещение вершины - изменяем радиус и поворот
            radius = distance(center, newPosition);
            if (inscriptionType == InscriptionType.CIRCUMSCRIBED) {
                radius = radius * Math.cos(Math.PI / sides);
            }
            if (pointIndex == 1) {
                rotation = Math.atan2(
                    newPosition.getY() - center.getY(),
                    newPosition.getX() - center.getX()
                );
            }
        } else if (pointIndex == sides + 1) {
            // Изменение радиуса через точку на стороне
            radius = distance(center, newPosition);
        }
    }

    @Override
    public void translate(double dx, double dy) {
        center = new Point(center.getX() + dx, center.getY() + dy);
    }

    @Override
    public boolean containsPoint(Point point, double tolerance) {
        Point[] vertices = getVertices();
        
        for (int i = 0; i < sides; i++) {
            Point v1 = vertices[i];
            Point v2 = vertices[(i + 1) % sides];
            if (distanceToSegment(point, v1, v2) < tolerance) {
                return true;
            }
        }
        return false;
    }

    @Override
    public double[] getBoundingBox() {
        Point[] vertices = getVertices();
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;
        
        for (Point v : vertices) {
            minX = Math.min(minX, v.getX());
            minY = Math.min(minY, v.getY());
            maxX = Math.max(maxX, v.getX());
            maxY = Math.max(maxY, v.getY());
        }
        
        return new double[]{minX, minY, maxX, maxY};
    }

    @Override
    public Map<String, Object> getProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put("Центр X", center.getX());
        props.put("Центр Y", center.getY());
        props.put("Количество сторон", sides);
        props.put("Тип построения", inscriptionType.getDisplayName());
        props.put("Радиус", radius);
        props.put("Угол поворота (°)", getRotationDegrees());
        props.put("Длина стороны", getSideLength());
        props.put("Периметр", getPerimeter());
        props.put("Площадь", getArea());
        props.put("Внутренний угол (°)", getInteriorAngle());
        return props;
    }

    @Override
    public boolean setProperty(String propertyName, Object value) {
        try {
            switch (propertyName) {
                case "Центр X" -> {
                    double val = parseDouble(value);
                    center = new Point(val, center.getY());
                }
                case "Центр Y" -> {
                    double val = parseDouble(value);
                    center = new Point(center.getX(), val);
                }
                case "Количество сторон" -> {
                    int val = value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(value.toString());
                    setSides(val);
                }
                case "Радиус" -> setRadius(parseDouble(value));
                case "Угол поворота (°)" -> setRotationDegrees(parseDouble(value));
                case "Тип построения" -> {
                    if (value instanceof InscriptionType) {
                        inscriptionType = (InscriptionType) value;
                    } else {
                        String strVal = value.toString();
                        if (strVal.equals("Вписанный") || strVal.equals("INSCRIBED")) {
                            inscriptionType = InscriptionType.INSCRIBED;
                        } else if (strVal.equals("Описанный") || strVal.equals("CIRCUMSCRIBED")) {
                            inscriptionType = InscriptionType.CIRCUMSCRIBED;
                        }
                    }
                }
                default -> { return false; }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private double parseDouble(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(value.toString());
    }
}








