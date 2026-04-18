package org.example.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LinearDimension extends DimensionPrimitive {

    public enum Orientation {
        HORIZONTAL("Горизонтальный"),
        VERTICAL("Вертикальный"),
        ALIGNED("Выровненный");

        private final String displayName;

        Orientation(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private DimensionAnchor firstAnchor;
    private DimensionAnchor secondAnchor;
    private Point dimensionLinePoint;
    private Orientation orientation;
    private Double manualTextShift;

    public LinearDimension(DimensionAnchor firstAnchor, DimensionAnchor secondAnchor,
                           Point dimensionLinePoint, Orientation orientation, LineStyle lineStyle) {
        super(lineStyle);
        this.firstAnchor = firstAnchor;
        this.secondAnchor = secondAnchor;
        this.dimensionLinePoint = dimensionLinePoint;
        this.orientation = orientation;
    }

    public DimensionAnchor getFirstAnchor() {
        return firstAnchor;
    }

    public DimensionAnchor getSecondAnchor() {
        return secondAnchor;
    }

    public Point getDimensionLinePoint() {
        return dimensionLinePoint;
    }

    public void setDimensionLinePoint(Point dimensionLinePoint) {
        this.dimensionLinePoint = dimensionLinePoint;
    }

    public Orientation getOrientation() {
        return orientation;
    }

    public void setOrientation(Orientation orientation) {
        this.orientation = orientation;
    }

    public Point getFirstPoint() {
        return firstAnchor.resolve();
    }

    public Point getSecondPoint() {
        return secondAnchor.resolve();
    }

    public Point getDimensionStart() {
        return switch (orientation) {
            case HORIZONTAL -> new Point(getFirstPoint().getX(), getDimensionLineBasePoint().getY());
            case VERTICAL -> new Point(getDimensionLineBasePoint().getX(), getFirstPoint().getY());
            case ALIGNED -> add(getFirstPoint(), scale(getNormal(), getSignedOffset()));
        };
    }

    public Point getDimensionEnd() {
        return switch (orientation) {
            case HORIZONTAL -> new Point(getSecondPoint().getX(), getDimensionLineBasePoint().getY());
            case VERTICAL -> new Point(getDimensionLineBasePoint().getX(), getSecondPoint().getY());
            case ALIGNED -> add(getSecondPoint(), scale(getNormal(), getSignedOffset()));
        };
    }

    public Point getRenderedDimensionStart() {
        return add(getDimensionStart(), scale(normalize(getDirection()), -getDimensionLineExtension()));
    }

    public Point getRenderedDimensionEnd() {
        return add(getDimensionEnd(), scale(normalize(getDirection()), getDimensionLineExtension()));
    }

    public Point getDirection() {
        Point p1 = getFirstPoint();
        Point p2 = getSecondPoint();
        if (orientation == Orientation.HORIZONTAL) {
            return new Point(1, 0);
        }
        if (orientation == Orientation.VERTICAL) {
            return new Point(0, 1);
        }
        return normalize(subtract(p2, p1));
    }

    public Point getNormal() {
        return perpendicularLeft(getDirection());
    }

    public double getSignedOffset() {
        Point p1 = getFirstPoint();
        return dot(subtract(dimensionLinePoint, p1), getNormal());
    }

    public double getOffsetDistance() {
        return Math.abs(getSignedOffset());
    }

    public void setOffsetDistance(double offsetDistance) {
        Point basePoint = getFirstPoint();
        double sign = Math.signum(getSignedOffset());
        if (Math.abs(sign) < 1e-9) {
            sign = 1.0;
        }
        this.dimensionLinePoint = add(basePoint, scale(getNormal(), sign * Math.max(0.0, offsetDistance)));
    }

    @Override
    public PrimitiveType getType() {
        return PrimitiveType.LINEAR_DIMENSION;
    }

    @Override
    public List<ControlPoint> getControlPoints() {
        List<ControlPoint> controlPoints = new ArrayList<>();
        controlPoints.add(new ControlPoint(getFirstPoint(), ControlPoint.Type.ENDPOINT, 0, "Первая выноска"));
        controlPoints.add(new ControlPoint(getSecondPoint(), ControlPoint.Type.ENDPOINT, 1, "Вторая выноска"));
        controlPoints.add(new ControlPoint(midpoint(getDimensionStart(), getDimensionEnd()),
                ControlPoint.Type.AXIS, 2, "Размерная линия"));
        controlPoints.add(new ControlPoint(getTextPosition(), ControlPoint.Type.CONTROL, 3, "Текст"));
        return controlPoints;
    }

    @Override
    public void moveControlPoint(int pointIndex, Point newPosition) {
        switch (pointIndex) {
            case 0 -> firstAnchor.moveTo(newPosition);
            case 1 -> secondAnchor.moveTo(newPosition);
            case 2 -> dimensionLinePoint = newPosition;
            case 3 -> setTextPosition(newPosition);
            default -> { }
        }
    }

    @Override
    public void translate(double dx, double dy) {
        firstAnchor.translate(dx, dy);
        secondAnchor.translate(dx, dy);
        dimensionLinePoint = new Point(dimensionLinePoint.getX() + dx, dimensionLinePoint.getY() + dy);
    }

    @Override
    public boolean containsPoint(Point point, double tolerance) {
        Point start = getRenderedDimensionStart();
        Point end = getRenderedDimensionEnd();
        if (distanceToSegment(point, start, end) <= tolerance) {
            return true;
        }

        Point normal = getNormal();
        double sign = Math.signum(getSignedOffset());
        if (Math.abs(sign) < 1e-9) {
            sign = 1.0;
        }

        Point firstExtStart = add(getFirstPoint(), scale(normal, sign * getExtensionLineOffset()));
        Point secondExtStart = add(getSecondPoint(), scale(normal, sign * getExtensionLineOffset()));
        Point firstExtEnd = add(start, scale(normal, sign * getExtensionLineOvershoot()));
        Point secondExtEnd = add(end, scale(normal, sign * getExtensionLineOvershoot()));

        if (distanceToSegment(point, firstExtStart, firstExtEnd) <= tolerance) {
            return true;
        }
        if (distanceToSegment(point, secondExtStart, secondExtEnd) <= tolerance) {
            return true;
        }

        return distance(point, getTextPosition()) <= Math.max(tolerance * 1.5, getTextHeight());
    }

    @Override
    public double[] getBoundingBox() {
        List<Point> points = List.of(
                getFirstPoint(),
                getSecondPoint(),
                getRenderedDimensionStart(),
                getRenderedDimensionEnd(),
                getTextPosition());

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
        properties.put("Тип", orientation.getDisplayName());
        properties.put("Значение", getMeasuredValue());
        properties.put("Текст", getDisplayText());
        properties.put("Переопределение текста", getTextOverride());
        properties.put("Высота текста", getTextHeight());
        properties.put("Размер стрелки", getArrowSize());
        properties.put("Смещение выносных", getExtensionLineOffset());
        properties.put("Выступ выносных", getExtensionLineOvershoot());
        return properties;
    }

    @Override
    public boolean setProperty(String propertyName, Object value) {
        try {
            switch (propertyName) {
                case "Переопределение текста", "Текст" -> setTextOverride(value != null ? value.toString() : "");
                case "Высота текста" -> setTextHeight(parseDouble(value));
                case "Размер стрелки" -> setArrowSize(parseDouble(value));
                case "Смещение выносных" -> setExtensionLineOffset(parseDouble(value));
                case "Выступ выносных" -> setExtensionLineOvershoot(parseDouble(value));
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
        return midpoint(getDimensionStart(), getDimensionEnd());
    }

    @Override
    public double getMeasuredValue() {
        Point p1 = getFirstPoint();
        Point p2 = getSecondPoint();
        return switch (orientation) {
            case HORIZONTAL -> Math.abs(p2.getX() - p1.getX());
            case VERTICAL -> Math.abs(p2.getY() - p1.getY());
            case ALIGNED -> distance(p1, p2);
        };
    }

    @Override
    public Point getTextPosition() {
        return buildTextPosition(getTextShiftAlongLine());
    }

    @Override
    public void setTextPosition(Point textPosition) {
        if (textPosition == null) {
            manualTextShift = null;
            return;
        }

        Point base = getDefaultTextPosition();
        Point direction = normalize(getDirection());
        double shift = dot(subtract(textPosition, base), direction);
        manualTextShift = clampTextShift(shift);
    }

    @Override
    public boolean isTextPositionManuallyMoved() {
        return manualTextShift != null;
    }

    @Override
    public void resetTextPosition() {
        manualTextShift = null;
    }

    @Override
    protected Point getDefaultTextPosition() {
        Point base = midpoint(getDimensionStart(), getDimensionEnd());
        if (getTextPlacement() == TextPlacement.ON_LINE) {
            return base;
        }

        Point textNormal = getStableTextNormal();
        double placementSign = getTextPlacement() == TextPlacement.BELOW_LINE ? -1.0 : 1.0;
        return add(base, scale(textNormal, placementSign * getTextGap()));
    }

    @Override
    protected String getMeasurementSuffix() {
        return null;
    }

    private double parseDouble(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(value.toString());
    }

    public double getTextShiftAlongLine() {
        return manualTextShift != null ? clampTextShift(manualTextShift) : 0.0;
    }

    public double getTextPositionFactor() {
        double length = distance(getDimensionStart(), getDimensionEnd());
        if (length < 1e-9) {
            return 0.5;
        }
        return clamp(0.5 + getTextShiftAlongLine() / length, 0.0, 1.0);
    }

    public void setTextPositionFactor(double factor) {
        double length = distance(getDimensionStart(), getDimensionEnd());
        if (length < 1e-9) {
            manualTextShift = 0.0;
            return;
        }
        double clampedFactor = clamp(factor, 0.0, 1.0);
        manualTextShift = clampTextShift((clampedFactor - 0.5) * length);
    }

    private Point buildTextPosition(double shift) {
        Point base = getDefaultTextPosition();
        Point direction = normalize(getDirection());
        return add(base, scale(direction, clampTextShift(shift)));
    }

    private Point getDimensionLineBasePoint() {
        return add(getFirstPoint(), scale(getNormal(), getSignedOffset()));
    }

    private double clampTextShift(double shift) {
        double halfLength = distance(getDimensionStart(), getDimensionEnd()) / 2.0;
        return clamp(shift, -halfLength, halfLength);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Выбирает стабильную нормаль для положения текста, чтобы "над"/"под"
     * не зависели от того, с какой стороны расположена размерная линия.
     */
    private Point getStableTextNormal() {
        Point normal = getNormal();
        boolean shouldFlip = normal.getY() < -1e-9
                || (Math.abs(normal.getY()) <= 1e-9 && normal.getX() < 0.0);

        if (shouldFlip) {
            return scale(normal, -1.0);
        }

        return normal;
    }
}
