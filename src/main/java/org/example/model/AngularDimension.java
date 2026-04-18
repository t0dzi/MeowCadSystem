package org.example.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AngularDimension extends DimensionPrimitive {

    private DimensionAnchor vertexAnchor;
    private DimensionAnchor firstRayAnchor;
    private DimensionAnchor secondRayAnchor;
    private Point arcPoint;
    private Double manualTextAngle;

    public AngularDimension(DimensionAnchor vertexAnchor, DimensionAnchor firstRayAnchor,
                            DimensionAnchor secondRayAnchor, Point arcPoint, LineStyle lineStyle) {
        super(lineStyle);
        this.vertexAnchor = vertexAnchor;
        this.firstRayAnchor = firstRayAnchor;
        this.secondRayAnchor = secondRayAnchor;
        this.arcPoint = arcPoint;
    }

    public Point getVertexPoint() {
        return vertexAnchor.resolve();
    }

    public Point getFirstRayPoint() {
        return firstRayAnchor.resolve();
    }

    public Point getSecondRayPoint() {
        return secondRayAnchor.resolve();
    }

    public Point getArcPoint() {
        return arcPoint;
    }

    public void setArcPoint(Point arcPoint) {
        this.arcPoint = arcPoint;
    }

    public double getRadiusValue() {
        return Math.max(distance(getVertexPoint(), arcPoint), 1.0);
    }

    public double getStartAngle() {
        Point vertex = getVertexPoint();
        Point first = getFirstRayPoint();
        return Math.atan2(first.getY() - vertex.getY(), first.getX() - vertex.getX());
    }

    public double getEndAngle() {
        Point vertex = getVertexPoint();
        Point second = getSecondRayPoint();
        return Math.atan2(second.getY() - vertex.getY(), second.getX() - vertex.getX());
    }

    public double getSweepAngle() {
        double start = getStartAngle();
        double end = getEndAngle();
        double ccwSweep = normalizeAngle(end - start);
        double pointAngle = Math.atan2(arcPoint.getY() - getVertexPoint().getY(), arcPoint.getX() - getVertexPoint().getX());

        if (isAngleOnSweep(start, ccwSweep, pointAngle)) {
            return ccwSweep;
        }
        return ccwSweep - Math.PI * 2.0;
    }

    public double getMiddleAngle() {
        return getStartAngle() + getSweepAngle() / 2.0;
    }

    public Point getArcStartPoint() {
        Point vertex = getVertexPoint();
        return new Point(
                vertex.getX() + getRadiusValue() * Math.cos(getStartAngle()),
                vertex.getY() + getRadiusValue() * Math.sin(getStartAngle()));
    }

    public Point getArcEndPoint() {
        Point vertex = getVertexPoint();
        return new Point(
                vertex.getX() + getRadiusValue() * Math.cos(getEndAngle()),
                vertex.getY() + getRadiusValue() * Math.sin(getEndAngle()));
    }

    @Override
    public PrimitiveType getType() {
        return PrimitiveType.ANGULAR_DIMENSION;
    }

    @Override
    public List<ControlPoint> getControlPoints() {
        List<ControlPoint> controlPoints = new ArrayList<>();
        controlPoints.add(new ControlPoint(getVertexPoint(), ControlPoint.Type.CENTER, 0, "Вершина"));
        controlPoints.add(new ControlPoint(getFirstRayPoint(), ControlPoint.Type.ENDPOINT, 1, "Первая лучевая точка"));
        controlPoints.add(new ControlPoint(getSecondRayPoint(), ControlPoint.Type.ENDPOINT, 2, "Вторая лучевая точка"));
        controlPoints.add(new ControlPoint(arcPoint, ControlPoint.Type.AXIS, 3, "Дуга размера"));
        controlPoints.add(new ControlPoint(getTextPosition(), ControlPoint.Type.CONTROL, 4, "Текст"));
        return controlPoints;
    }

    @Override
    public void moveControlPoint(int pointIndex, Point newPosition) {
        switch (pointIndex) {
            case 0 -> vertexAnchor.moveTo(newPosition);
            case 1 -> firstRayAnchor.moveTo(newPosition);
            case 2 -> secondRayAnchor.moveTo(newPosition);
            case 3 -> arcPoint = newPosition;
            case 4 -> setTextPosition(newPosition);
            default -> { }
        }
    }

    @Override
    public void translate(double dx, double dy) {
        vertexAnchor.translate(dx, dy);
        firstRayAnchor.translate(dx, dy);
        secondRayAnchor.translate(dx, dy);
        arcPoint = new Point(arcPoint.getX() + dx, arcPoint.getY() + dy);
    }

    @Override
    public boolean containsPoint(Point point, double tolerance) {
        if (distanceToSegment(point, getVertexPoint(), getArcStartPoint()) <= tolerance) {
            return true;
        }
        if (distanceToSegment(point, getVertexPoint(), getArcEndPoint()) <= tolerance) {
            return true;
        }

        Point vertex = getVertexPoint();
        double radius = getRadiusValue();
        double distanceToArc = Math.abs(distance(vertex, point) - radius);
        if (distanceToArc <= tolerance) {
            double pointAngle = Math.atan2(point.getY() - vertex.getY(), point.getX() - vertex.getX());
            if (isAngleOnSweep(getStartAngle(), getSweepAngle(), pointAngle)) {
                return true;
            }
        }

        return distance(point, getTextPosition()) <= Math.max(tolerance * 1.5, getTextHeight());
    }

    @Override
    public double[] getBoundingBox() {
        List<Point> points = List.of(
                getVertexPoint(),
                getFirstRayPoint(),
                getSecondRayPoint(),
                getArcStartPoint(),
                getArcEndPoint(),
                getTextPosition(),
                arcPoint);

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
        properties.put("Тип", "Угловой");
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
        return getVertexPoint();
    }

    @Override
    public double getMeasuredValue() {
        return Math.toDegrees(Math.abs(getSweepAngle()));
    }

    @Override
    public Point getTextPosition() {
        return buildTextPosition(getCurrentTextAngle());
    }

    @Override
    public void setTextPosition(Point textPosition) {
        if (textPosition == null) {
            manualTextAngle = null;
            return;
        }

        Point vertex = getVertexPoint();
        double angle = Math.atan2(
                textPosition.getY() - vertex.getY(),
                textPosition.getX() - vertex.getX());
        manualTextAngle = clampAngleToSweep(angle);
    }

    @Override
    public boolean isTextPositionManuallyMoved() {
        return manualTextAngle != null;
    }

    @Override
    public void resetTextPosition() {
        manualTextAngle = null;
    }

    @Override
    protected Point getDefaultTextPosition() {
        return buildTextPosition(getMiddleAngle());
    }

    @Override
    protected String getMeasurementSuffix() {
        return "°";
    }

    private boolean isAngleOnSweep(double startAngle, double sweepAngle, double testAngle) {
        if (sweepAngle >= 0) {
            return normalizeAngle(testAngle - startAngle) <= sweepAngle + 1e-9;
        }
        return normalizeAngle(startAngle - testAngle) <= -sweepAngle + 1e-9;
    }

    private Point buildTextPosition(double angle) {
        Point vertex = getVertexPoint();
        double radius = getTextRadius();
        return new Point(
                vertex.getX() + radius * Math.cos(angle),
                vertex.getY() + radius * Math.sin(angle));
    }

    private double getCurrentTextAngle() {
        return manualTextAngle != null ? clampAngleToSweep(manualTextAngle) : getMiddleAngle();
    }

    private double getTextRadius() {
        double offset = switch (getTextPlacement()) {
            case ABOVE_LINE -> getTextGap();
            case ON_LINE -> 0.0;
            case BELOW_LINE -> -getTextGap();
        };
        return getRadiusValue() + Math.max(getTextHeight(), getArrowSize() * 1.5) + offset;
    }

    private double clampAngleToSweep(double angle) {
        double start = getStartAngle();
        double sweep = getSweepAngle();
        double normalizedAngle = normalizeAngle(angle);
        double normalizedStart = normalizeAngle(start);
        double normalizedEnd = normalizeAngle(start + sweep);

        if (sweep >= 0) {
            if (isAngleBetween(normalizedAngle, normalizedStart, normalizedEnd)) {
                return angle;
            }
        } else if (isAngleBetween(normalizedAngle, normalizedEnd, normalizedStart)) {
            return angle;
        }

        double startDelta = angularDistance(normalizedAngle, normalizedStart);
        double endDelta = angularDistance(normalizedAngle, normalizedEnd);
        return startDelta <= endDelta ? start : start + sweep;
    }

    private boolean isAngleBetween(double angle, double start, double end) {
        if (start <= end) {
            return angle >= start && angle <= end;
        }
        return angle >= start || angle <= end;
    }

    private double angularDistance(double first, double second) {
        double diff = Math.abs(first - second) % (Math.PI * 2.0);
        return Math.min(diff, Math.PI * 2.0 - diff);
    }

    private double normalizeAngle(double angle) {
        double normalized = angle % (Math.PI * 2.0);
        if (normalized < 0) {
            normalized += Math.PI * 2.0;
        }
        return normalized;
    }

    private double parseDouble(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(value.toString());
    }
}
