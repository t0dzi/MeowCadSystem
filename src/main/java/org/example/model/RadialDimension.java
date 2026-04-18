package org.example.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RadialDimension extends DimensionPrimitive {

    public enum Kind {
        RADIUS("Радиус", "R"),
        DIAMETER("Диаметр", "\u2300");

        private final String displayName;
        private final String prefix;

        Kind(String displayName, String prefix) {
            this.displayName = displayName;
            this.prefix = prefix;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getPrefix() {
            return prefix;
        }
    }

    public enum ShelfSide {
        ALONG_LINE("По линии"),
        LEFT("Слева"),
        RIGHT("Справа");

        private final String displayName;

        ShelfSide(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public ShelfSide toggle() {
            return switch (this) {
                case ALONG_LINE -> RIGHT;
                case RIGHT -> LEFT;
                case LEFT -> ALONG_LINE;
            };
        }
    }

    private Primitive referencedPrimitive;
    private Point leaderPoint;
    private Kind kind;
    private ShelfSide shelfSide = ShelfSide.ALONG_LINE;

    public RadialDimension(Primitive referencedPrimitive, Point leaderPoint, Kind kind, LineStyle lineStyle) {
        super(lineStyle);
        this.referencedPrimitive = referencedPrimitive;
        this.leaderPoint = leaderPoint;
        this.kind = kind;
        setTextPlacement(TextPlacement.ABOVE_LINE);
    }

    public Primitive getReferencedPrimitive() {
        return referencedPrimitive;
    }

    public Point getLeaderPoint() {
        return leaderPoint;
    }

    public void setLeaderPoint(Point leaderPoint) {
        this.leaderPoint = leaderPoint;
        if (!isTextPositionManuallyMoved()) {
            resetTextPosition();
        }
    }

    public Kind getKind() {
        return kind;
    }

    public void setKind(Kind kind) {
        this.kind = kind;
    }

    public ShelfSide getShelfSide() {
        return shelfSide;
    }

    public void setShelfSide(ShelfSide shelfSide) {
        this.shelfSide = shelfSide != null ? shelfSide : ShelfSide.ALONG_LINE;
    }

    public void toggleShelfSide() {
        shelfSide = shelfSide.toggle();
    }

    public Point getCenterPoint() {
        return referencedPrimitive.getCenter();
    }

    public double getRadiusValue() {
        if (referencedPrimitive instanceof Circle circle) {
            return circle.getRadius();
        }
        if (referencedPrimitive instanceof Arc arc) {
            return arc.getRadius();
        }
        return 0.0;
    }

    public Point getAttachmentPoint() {
        Point center = getCenterPoint();
        Point direction = normalize(subtract(leaderPoint, center));
        return add(center, scale(direction, getRadiusValue()));
    }

    public Point getOppositeDiameterPoint() {
        Point center = getCenterPoint();
        Point direction = normalize(subtract(leaderPoint, center));
        return add(center, scale(direction, -getRadiusValue()));
    }

    @Override
    public PrimitiveType getType() {
        return PrimitiveType.RADIAL_DIMENSION;
    }

    @Override
    public List<ControlPoint> getControlPoints() {
        List<ControlPoint> controlPoints = new ArrayList<>();
        controlPoints.add(new ControlPoint(getAttachmentPoint(), ControlPoint.Type.ENDPOINT, 0, "Точка на окружности"));
        if (kind == Kind.DIAMETER) {
            controlPoints.add(new ControlPoint(getOppositeDiameterPoint(), ControlPoint.Type.ENDPOINT, 1, "Противоположная точка"));
            controlPoints.add(new ControlPoint(leaderPoint, ControlPoint.Type.AXIS, 2, "Выноска"));
            controlPoints.add(new ControlPoint(getTextPosition(), ControlPoint.Type.CONTROL, 3, "Текст"));
        } else {
            controlPoints.add(new ControlPoint(leaderPoint, ControlPoint.Type.AXIS, 1, "Выноска"));
            controlPoints.add(new ControlPoint(getTextPosition(), ControlPoint.Type.CONTROL, 2, "Текст"));
        }
        return controlPoints;
    }

    @Override
    public void moveControlPoint(int pointIndex, Point newPosition) {
        if (kind == Kind.DIAMETER) {
            switch (pointIndex) {
                case 0, 1, 2 -> leaderPoint = newPosition;
                case 3 -> setTextPosition(new Point(newPosition.getX(), getTextPosition().getY()));
                default -> {
                }
            }
            return;
        }

        switch (pointIndex) {
            case 0, 1 -> leaderPoint = newPosition;
            case 2 -> setTextPosition(new Point(newPosition.getX(), getTextPosition().getY()));
            default -> {
            }
        }
    }

    @Override
    public void translate(double dx, double dy) {
        leaderPoint = new Point(leaderPoint.getX() + dx, leaderPoint.getY() + dy);
        translateText(dx, dy);
    }

    @Override
    public boolean containsPoint(Point point, double tolerance) {
        if (kind == Kind.DIAMETER) {
            if (distanceToSegment(point, getOppositeDiameterPoint(), leaderPoint) <= tolerance) {
                return true;
            }
        } else if (distanceToSegment(point, getCenterPoint(), leaderPoint) <= tolerance) {
            return true;
        }

        return distance(point, getTextPosition()) <= Math.max(tolerance * 1.5, getTextHeight());
    }

    @Override
    public double[] getBoundingBox() {
        List<Point> points = new ArrayList<>();
        points.add(getCenterPoint());
        points.add(getAttachmentPoint());
        points.add(leaderPoint);
        points.add(getTextPosition());
        if (kind == Kind.DIAMETER) {
            points.add(getOppositeDiameterPoint());
        }

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        for (Point point : points) {
            minX = Math.min(minX, point.getX());
            minY = Math.min(minY, point.getY());
            maxX = Math.max(maxX, point.getX());
            maxY = Math.max(maxY, point.getY());
        }

        double padding = Math.max(getArrowSize(), getTextHeight());
        return new double[] { minX - padding, minY - padding, maxX + padding, maxY + padding };
    }

    @Override
    public Map<String, Object> getProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("Тип", kind.getDisplayName());
        properties.put("Полка", shelfSide.getDisplayName());
        properties.put("Значение", getMeasuredValue());
        properties.put("Текст", getDisplayText());
        properties.put("Переопределение текста", getTextOverride());
        properties.put("Высота текста", getTextHeight());
        properties.put("Размер стрелки", getArrowSize());
        return properties;
    }

    @Override
    public boolean setProperty(String propertyName, Object value) {
        try {
            switch (propertyName) {
                case "Переопределение текста", "Текст" -> setTextOverride(value != null ? value.toString() : "");
                case "Высота текста" -> setTextHeight(parseDouble(value));
                case "Размер стрелки" -> setArrowSize(parseDouble(value));
                default -> {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Point getCenter() {
        return getCenterPoint();
    }

    @Override
    public double getMeasuredValue() {
        return kind == Kind.DIAMETER ? getRadiusValue() * 2.0 : getRadiusValue();
    }

    @Override
    protected Point getDefaultTextPosition() {
        return leaderPoint;
    }

    @Override
    protected String getMeasurementSuffix() {
        return null;
    }

    @Override
    public String getDisplayText() {
        if (hasTextOverride()) {
            return getTextOverride();
        }
        return kind.getPrefix() + formatMeasurement(getMeasuredValue(), null);
    }

    private double parseDouble(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(value.toString());
    }
}
