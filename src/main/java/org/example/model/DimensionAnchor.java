package org.example.model;

import java.util.List;

public class DimensionAnchor {

    public enum Type {
        FIXED,
        CONTROL_POINT,
        SEGMENT_MIDPOINT,
        ARC_MIDPOINT,
        RECTANGLE_EDGE_MIDPOINT,
        POLYGON_EDGE_MIDPOINT,
        CIRCLE_QUADRANT,
        ELLIPSE_PARAMETRIC,
        SPLINE_SEGMENT_MIDPOINT
    }

    private Primitive primitive;
    private Type type;
    private int index;
    private Point fixedPoint;

    private DimensionAnchor(Primitive primitive, Type type, int index, Point fixedPoint) {
        this.primitive = primitive;
        this.type = type;
        this.index = index;
        this.fixedPoint = fixedPoint;
    }

    public static DimensionAnchor fixed(Point point) {
        return new DimensionAnchor(null, Type.FIXED, -1, point);
    }

    public static DimensionAnchor controlPoint(Primitive primitive, int controlPointIndex, Point fallbackPoint) {
        return new DimensionAnchor(primitive, Type.CONTROL_POINT, controlPointIndex, fallbackPoint);
    }

    public static DimensionAnchor fromSnapPoint(SnapPoint snapPoint) {
        if (snapPoint == null || snapPoint.getPosition() == null) {
            return null;
        }

        Primitive primitive = snapPoint.getPrimaryPrimitive();
        if (primitive == null) {
            return fixed(snapPoint.getPosition());
        }

        Point snapPosition = snapPoint.getPosition();
        return switch (primitive.getType()) {
            case SEGMENT -> fromSegmentSnap((Segment) primitive, snapPoint.getType(), snapPosition);
            case CIRCLE -> fromCircleSnap((Circle) primitive, snapPoint.getType(), snapPosition);
            case ARC -> fromArcSnap((Arc) primitive, snapPoint.getType(), snapPosition);
            case RECTANGLE -> fromRectangleSnap((Rectangle) primitive, snapPoint.getType(), snapPosition);
            case ELLIPSE -> fromEllipseSnap((Ellipse) primitive, snapPoint.getType(), snapPosition);
            case POLYGON -> fromPolygonSnap((Polygon) primitive, snapPoint.getType(), snapPosition);
            case SPLINE -> fromSplineSnap((Spline) primitive, snapPoint.getType(), snapPosition);
            default -> fixed(snapPosition);
        };
    }

    public Point resolve() {
        if (type == Type.FIXED || primitive == null) {
            return fixedPoint;
        }

        return switch (type) {
            case CONTROL_POINT -> resolveControlPoint();
            case SEGMENT_MIDPOINT -> resolveSegmentMidpoint();
            case ARC_MIDPOINT -> resolveArcMidpoint();
            case RECTANGLE_EDGE_MIDPOINT -> resolveRectangleEdgeMidpoint();
            case POLYGON_EDGE_MIDPOINT -> resolvePolygonEdgeMidpoint();
            case CIRCLE_QUADRANT -> resolveCircleQuadrant();
            case ELLIPSE_PARAMETRIC -> resolveEllipseParametric();
            case SPLINE_SEGMENT_MIDPOINT -> resolveSplineSegmentMidpoint();
            case FIXED -> fixedPoint;
        };
    }

    public void moveTo(Point point) {
        primitive = null;
        type = Type.FIXED;
        index = -1;
        fixedPoint = point;
    }

    public void translate(double dx, double dy) {
        if (isAssociative()) {
            Point resolved = resolve();
            if (resolved != null) {
                moveTo(new Point(resolved.getX() + dx, resolved.getY() + dy));
            }
            return;
        }

        if (fixedPoint != null) {
            fixedPoint = new Point(fixedPoint.getX() + dx, fixedPoint.getY() + dy);
        }
    }

    public boolean isAssociative() {
        return primitive != null && type != Type.FIXED;
    }

    public Primitive getPrimitive() {
        return primitive;
    }

    public Type getType() {
        return type;
    }

    public int getIndex() {
        return index;
    }

    public Point getFallbackPoint() {
        return fixedPoint;
    }

    private Point resolveControlPoint() {
        List<ControlPoint> controlPoints = primitive.getControlPoints();
        for (ControlPoint cp : controlPoints) {
            if (cp.getIndex() == index) {
                return cp.getPosition();
            }
        }
        return fixedPoint;
    }

    private Point resolveSegmentMidpoint() {
        if (!(primitive instanceof Segment segment)) {
            return fixedPoint;
        }
        return midpoint(segment.getStartPoint(), segment.getEndPoint());
    }

    private Point resolveArcMidpoint() {
        if (!(primitive instanceof Arc arc)) {
            return fixedPoint;
        }
        double angle = arc.getStartAngle() + arc.getSweepAngle() / 2.0;
        return new Point(
                arc.getCenter().getX() + arc.getRadius() * Math.cos(angle),
                arc.getCenter().getY() + arc.getRadius() * Math.sin(angle));
    }

    private Point resolveRectangleEdgeMidpoint() {
        if (!(primitive instanceof Rectangle rectangle)) {
            return fixedPoint;
        }
        Point[] corners = rectangle.getCorners();
        Point start = corners[Math.floorMod(index, corners.length)];
        Point end = corners[Math.floorMod(index + 1, corners.length)];
        return midpoint(start, end);
    }

    private Point resolvePolygonEdgeMidpoint() {
        if (!(primitive instanceof Polygon polygon)) {
            return fixedPoint;
        }
        Point[] vertices = polygon.getVertices();
        Point start = vertices[Math.floorMod(index, vertices.length)];
        Point end = vertices[Math.floorMod(index + 1, vertices.length)];
        return midpoint(start, end);
    }

    private Point resolveCircleQuadrant() {
        if (!(primitive instanceof Circle circle)) {
            return fixedPoint;
        }
        double angle = Math.toRadians(index * 90.0);
        return new Point(
                circle.getCenter().getX() + circle.getRadius() * Math.cos(angle),
                circle.getCenter().getY() + circle.getRadius() * Math.sin(angle));
    }

    private Point resolveEllipseParametric() {
        if (!(primitive instanceof Ellipse ellipse)) {
            return fixedPoint;
        }
        double angle = switch (index) {
            case 0 -> 0.0;
            case 1 -> Math.PI / 2.0;
            case 2 -> Math.PI;
            case 3 -> 3.0 * Math.PI / 2.0;
            case 4 -> Math.PI / 4.0;
            case 5 -> 3.0 * Math.PI / 4.0;
            case 6 -> 5.0 * Math.PI / 4.0;
            case 7 -> 7.0 * Math.PI / 4.0;
            default -> 0.0;
        };
        return ellipse.getPointAtAngle(angle);
    }

    private Point resolveSplineSegmentMidpoint() {
        if (!(primitive instanceof Spline spline)) {
            return fixedPoint;
        }
        Point p1 = spline.getControlPoint(index);
        Point p2 = spline.getControlPoint(index + 1);
        if (p1 == null || p2 == null) {
            return fixedPoint;
        }
        return midpoint(p1, p2);
    }

    private static DimensionAnchor fromSegmentSnap(Segment segment, SnapType snapType, Point snapPosition) {
        if (snapType == SnapType.MIDPOINT) {
            return new DimensionAnchor(segment, Type.SEGMENT_MIDPOINT, 0, snapPosition);
        }
        if (snapType == SnapType.ENDPOINT) {
            int cpIndex = nearestControlPointIndex(segment, snapPosition, 0, 1);
            return controlPoint(segment, cpIndex, snapPosition);
        }
        return fixed(snapPosition);
    }

    private static DimensionAnchor fromCircleSnap(Circle circle, SnapType snapType, Point snapPosition) {
        if (snapType == SnapType.CENTER) {
            return controlPoint(circle, 0, snapPosition);
        }
        if (snapType == SnapType.ENDPOINT) {
            int quadrant = nearestCircleQuadrant(circle, snapPosition);
            return new DimensionAnchor(circle, Type.CIRCLE_QUADRANT, quadrant, snapPosition);
        }
        return fixed(snapPosition);
    }

    private static DimensionAnchor fromArcSnap(Arc arc, SnapType snapType, Point snapPosition) {
        if (snapType == SnapType.CENTER) {
            return controlPoint(arc, 0, snapPosition);
        }
        if (snapType == SnapType.MIDPOINT) {
            return new DimensionAnchor(arc, Type.ARC_MIDPOINT, 0, snapPosition);
        }
        if (snapType == SnapType.ENDPOINT) {
            int cpIndex = nearestControlPointIndex(arc, snapPosition, 1, 2);
            return controlPoint(arc, cpIndex, snapPosition);
        }
        return fixed(snapPosition);
    }

    private static DimensionAnchor fromRectangleSnap(Rectangle rectangle, SnapType snapType, Point snapPosition) {
        if (snapType == SnapType.CENTER) {
            return controlPoint(rectangle, 0, snapPosition);
        }
        if (snapType == SnapType.ENDPOINT) {
            int cpIndex = nearestCornerIndex(rectangle.getCorners(), snapPosition) + 1;
            return controlPoint(rectangle, cpIndex, snapPosition);
        }
        if (snapType == SnapType.MIDPOINT) {
            int edgeIndex = nearestEdgeMidpointIndex(rectangle.getCorners(), snapPosition);
            return new DimensionAnchor(rectangle, Type.RECTANGLE_EDGE_MIDPOINT, edgeIndex, snapPosition);
        }
        return fixed(snapPosition);
    }

    private static DimensionAnchor fromEllipseSnap(Ellipse ellipse, SnapType snapType, Point snapPosition) {
        if (snapType == SnapType.CENTER) {
            return controlPoint(ellipse, 0, snapPosition);
        }
        if (snapType == SnapType.ENDPOINT) {
            int parametricIndex = nearestEllipseIndex(ellipse, snapPosition, false);
            return new DimensionAnchor(ellipse, Type.ELLIPSE_PARAMETRIC, parametricIndex, snapPosition);
        }
        if (snapType == SnapType.MIDPOINT) {
            int parametricIndex = nearestEllipseIndex(ellipse, snapPosition, true);
            return new DimensionAnchor(ellipse, Type.ELLIPSE_PARAMETRIC, parametricIndex, snapPosition);
        }
        return fixed(snapPosition);
    }

    private static DimensionAnchor fromPolygonSnap(Polygon polygon, SnapType snapType, Point snapPosition) {
        if (snapType == SnapType.CENTER) {
            return controlPoint(polygon, 0, snapPosition);
        }
        if (snapType == SnapType.ENDPOINT) {
            int vertexIndex = nearestCornerIndex(polygon.getVertices(), snapPosition) + 1;
            return controlPoint(polygon, vertexIndex, snapPosition);
        }
        if (snapType == SnapType.MIDPOINT) {
            int edgeIndex = nearestEdgeMidpointIndex(polygon.getVertices(), snapPosition);
            return new DimensionAnchor(polygon, Type.POLYGON_EDGE_MIDPOINT, edgeIndex, snapPosition);
        }
        return fixed(snapPosition);
    }

    private static DimensionAnchor fromSplineSnap(Spline spline, SnapType snapType, Point snapPosition) {
        if (snapType == SnapType.ENDPOINT) {
            int controlPointIndex = nearestSplinePointIndex(spline, snapPosition);
            return controlPoint(spline, controlPointIndex, snapPosition);
        }
        if (snapType == SnapType.MIDPOINT) {
            int segmentIndex = nearestSplineSegmentMidpointIndex(spline, snapPosition);
            return new DimensionAnchor(spline, Type.SPLINE_SEGMENT_MIDPOINT, segmentIndex, snapPosition);
        }
        return fixed(snapPosition);
    }

    private static int nearestControlPointIndex(Primitive primitive, Point target, int... allowedIndices) {
        List<ControlPoint> controlPoints = primitive.getControlPoints();
        double bestDistance = Double.MAX_VALUE;
        int bestIndex = allowedIndices[0];

        for (ControlPoint cp : controlPoints) {
            for (int allowedIndex : allowedIndices) {
                if (cp.getIndex() == allowedIndex) {
                    double distance = distance(cp.getPosition(), target);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestIndex = allowedIndex;
                    }
                }
            }
        }

        return bestIndex;
    }

    private static int nearestCornerIndex(Point[] points, Point target) {
        double bestDistance = Double.MAX_VALUE;
        int bestIndex = 0;
        for (int i = 0; i < points.length; i++) {
            double distance = distance(points[i], target);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private static int nearestEdgeMidpointIndex(Point[] points, Point target) {
        double bestDistance = Double.MAX_VALUE;
        int bestIndex = 0;
        for (int i = 0; i < points.length; i++) {
            Point midpoint = midpoint(points[i], points[(i + 1) % points.length]);
            double distance = distance(midpoint, target);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private static int nearestCircleQuadrant(Circle circle, Point target) {
        double bestDistance = Double.MAX_VALUE;
        int bestIndex = 0;
        for (int i = 0; i < 4; i++) {
            Point point = new Point(
                    circle.getCenter().getX() + circle.getRadius() * Math.cos(Math.toRadians(i * 90.0)),
                    circle.getCenter().getY() + circle.getRadius() * Math.sin(Math.toRadians(i * 90.0)));
            double distance = distance(point, target);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private static int nearestEllipseIndex(Ellipse ellipse, Point target, boolean diagonal) {
        int start = diagonal ? 4 : 0;
        int end = diagonal ? 8 : 4;
        double bestDistance = Double.MAX_VALUE;
        int bestIndex = start;

        for (int i = start; i < end; i++) {
            Point point = new DimensionAnchor(ellipse, Type.ELLIPSE_PARAMETRIC, i, target).resolveEllipseParametric();
            double distance = distance(point, target);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }

        return bestIndex;
    }

    private static int nearestSplinePointIndex(Spline spline, Point target) {
        double bestDistance = Double.MAX_VALUE;
        int bestIndex = 0;
        for (int i = 0; i < spline.getPointCount(); i++) {
            Point point = spline.getControlPoint(i);
            double distance = distance(point, target);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private static int nearestSplineSegmentMidpointIndex(Spline spline, Point target) {
        double bestDistance = Double.MAX_VALUE;
        int bestIndex = 0;
        for (int i = 0; i < spline.getPointCount() - 1; i++) {
            Point midpoint = midpoint(spline.getControlPoint(i), spline.getControlPoint(i + 1));
            double distance = distance(midpoint, target);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private static Point midpoint(Point p1, Point p2) {
        return new Point(
                (p1.getX() + p2.getX()) / 2.0,
                (p1.getY() + p2.getY()) / 2.0);
    }

    private static double distance(Point p1, Point p2) {
        double dx = p2.getX() - p1.getX();
        double dy = p2.getY() - p1.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }
}
