package org.example.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class SnapManager {

    private final CadModel model;

    private final Set<SnapType> enabledSnaps = EnumSet.of(
            SnapType.ENDPOINT,
            SnapType.MIDPOINT,
            SnapType.CENTER);

    private final BooleanProperty snapEnabled = new SimpleBooleanProperty(true);

    /** Радиус захвата привязки в пикселях экрана */
    private double snapRadius = 25.0;

    private SnapPoint currentSnap = null;

    /** Первая точка построения (для перпендикуляра/касательной) */
    private Point basePoint = null;

    public SnapManager(CadModel model) {
        this.model = model;
    }

    /**
     * Устанавливает базовую точку для расчёта перпендикуляра и касательной.
     * Это первая точка построения линии.
     */
    public void setBasePoint(Point point) {
        this.basePoint = point;
    }

    public Point getBasePoint() {
        return basePoint;
    }

    public boolean isSnapEnabled() {
        return snapEnabled.get();
    }

    public void setSnapEnabled(boolean enabled) {
        snapEnabled.set(enabled);
    }

    public BooleanProperty snapEnabledProperty() {
        return snapEnabled;
    }

    public void enableSnapType(SnapType type, boolean enabled) {
        if (enabled) {
            enabledSnaps.add(type);
        } else {
            enabledSnaps.remove(type);
        }
    }

    public boolean isSnapTypeEnabled(SnapType type) {
        return enabledSnaps.contains(type);
    }

    public Set<SnapType> getEnabledSnaps() {
        return EnumSet.copyOf(enabledSnaps);
    }

    public double getSnapRadius() {
        return snapRadius;
    }

    public void setSnapRadius(double radius) {
        this.snapRadius = radius;
    }

    public SnapPoint getCurrentSnap() {
        return currentSnap;
    }


    /**
     * Находит ближайшую точку привязки к указанной позиции.
     * 
     * @param worldPos позиция курсора в мировых координатах
     * @param scale    масштаб камеры для расчёта радиуса захвата
     * @return ближайшая точка привязки или null
     */
    public SnapPoint findSnapPoint(Point worldPos, double scale) {
        if (!snapEnabled.get() || worldPos == null) {
            currentSnap = null;
            return null;
        }

        if (scale <= 0)
            scale = 1.0;

        // Радиус захвата в мировых единицах (snapRadius пикселей на экране)
        double captureRadius = snapRadius / scale;

        List<SnapPoint> candidates = new ArrayList<>();

        try {
            for (Primitive primitive : model.getPrimitives()) {
                if (primitive != null) {
                    collectSnapPoints(primitive, candidates);
                }
            }

            if (enabledSnaps.contains(SnapType.INTERSECTION)) {
                collectIntersections(candidates);
            }

            // Добавляем динамические привязки (перпендикуляр, касательная)
            if (enabledSnaps.contains(SnapType.PERPENDICULAR)) {
                collectPerpendicularSnaps(worldPos, candidates, captureRadius);
            }
            if (enabledSnaps.contains(SnapType.TANGENT)) {
                collectTangentSnaps(worldPos, candidates, captureRadius);
            }
        } catch (Exception e) {
            e.printStackTrace();
            currentSnap = null;
            return null;
        }

        // Находим ближайшую точку в пределах радиуса захвата
        // Сначала пробуем найти касательную или перпендикуляр (динамические привязки)
        SnapPoint nearest = null;
        double nearestDist = Double.MAX_VALUE;

        // Первый проход: ищем динамические привязки (касательная, перпендикуляр)
        if (basePoint != null) {
            for (SnapPoint sp : candidates) {
                if (!enabledSnaps.contains(sp.getType())) {
                    continue;
                }

                if (sp.getType() == SnapType.TANGENT || sp.getType() == SnapType.PERPENDICULAR) {
                    double dist = sp.distanceTo(worldPos);
                    // Большой радиус захвата для динамических привязок
                    if (dist < captureRadius * 8 && dist < nearestDist) {
                        nearestDist = dist;
                        nearest = sp;
                    }
                }
            }
        }

        if (nearest != null) {
            currentSnap = nearest;
            return nearest;
        }

        nearestDist = captureRadius;
        for (SnapPoint sp : candidates) {
            if (!enabledSnaps.contains(sp.getType())) {
                continue;
            }

            double dist = sp.distanceTo(worldPos);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = sp;
            }
        }

        currentSnap = nearest;
        return nearest;
    }

    public Point getSnappedPosition(Point worldPos, double scale) {
        SnapPoint snap = findSnapPoint(worldPos, scale);
        return snap != null ? snap.getPosition() : worldPos;
    }


    private void collectSnapPoints(Primitive primitive, List<SnapPoint> points) {
        switch (primitive.getType()) {
            case SEGMENT -> collectSegmentSnaps((Segment) primitive, points);
            case CIRCLE -> collectCircleSnaps((Circle) primitive, points);
            case ARC -> collectArcSnaps((Arc) primitive, points);
            case RECTANGLE -> collectRectangleSnaps((Rectangle) primitive, points);
            case ELLIPSE -> collectEllipseSnaps((Ellipse) primitive, points);
            case POLYGON -> collectPolygonSnaps((Polygon) primitive, points);
            case SPLINE -> collectSplineSnaps((Spline) primitive, points);
            case LINEAR_DIMENSION, RADIAL_DIMENSION, ANGULAR_DIMENSION -> collectDimensionSnaps((DimensionPrimitive) primitive, points);
        }
    }

    private void collectDimensionSnaps(DimensionPrimitive dimension, List<SnapPoint> points) {
        if (dimension instanceof LinearDimension linearDimension) {
            points.add(new SnapPoint(linearDimension.getDimensionStart(), SnapType.ENDPOINT, linearDimension));
            points.add(new SnapPoint(linearDimension.getDimensionEnd(), SnapType.ENDPOINT, linearDimension));
            points.add(new SnapPoint(linearDimension.getCenter(), SnapType.MIDPOINT, linearDimension));
            return;
        }

        if (dimension instanceof RadialDimension radialDimension) {
            Point attachment = radialDimension.getAttachmentPoint();
            Point leader = radialDimension.getLeaderPoint();
            points.add(new SnapPoint(attachment, SnapType.ENDPOINT, radialDimension));
            points.add(new SnapPoint(leader, SnapType.ENDPOINT, radialDimension));
            points.add(new SnapPoint(radialDimension.getCenter(), SnapType.CENTER, radialDimension));
            points.add(new SnapPoint(new Point(
                    (attachment.getX() + leader.getX()) / 2.0,
                    (attachment.getY() + leader.getY()) / 2.0),
                    SnapType.MIDPOINT, radialDimension));
            return;
        }

        if (dimension instanceof AngularDimension angularDimension) {
            Point arcStart = angularDimension.getArcStartPoint();
            Point arcEnd = angularDimension.getArcEndPoint();
            points.add(new SnapPoint(arcStart, SnapType.ENDPOINT, angularDimension));
            points.add(new SnapPoint(arcEnd, SnapType.ENDPOINT, angularDimension));
            points.add(new SnapPoint(angularDimension.getCenter(), SnapType.CENTER, angularDimension));
            points.add(new SnapPoint(new Point(
                    angularDimension.getVertexPoint().getX() + angularDimension.getRadiusValue() * Math.cos(angularDimension.getMiddleAngle()),
                    angularDimension.getVertexPoint().getY() + angularDimension.getRadiusValue() * Math.sin(angularDimension.getMiddleAngle())),
                    SnapType.MIDPOINT, angularDimension));
        }
    }

    private void collectSegmentSnaps(Segment segment, List<SnapPoint> points) {
        points.add(new SnapPoint(segment.getStartPoint(), SnapType.ENDPOINT, segment));
        points.add(new SnapPoint(segment.getEndPoint(), SnapType.ENDPOINT, segment));

        Point mid = new Point(
                (segment.getStartPoint().getX() + segment.getEndPoint().getX()) / 2,
                (segment.getStartPoint().getY() + segment.getEndPoint().getY()) / 2);
        points.add(new SnapPoint(mid, SnapType.MIDPOINT, segment));
    }

    private void collectCircleSnaps(Circle circle, List<SnapPoint> points) {
        points.add(new SnapPoint(circle.getCenter(), SnapType.CENTER, circle));

        double r = circle.getRadius();
        Point c = circle.getCenter();
        points.add(new SnapPoint(new Point(c.getX() + r, c.getY()), SnapType.ENDPOINT, circle));
        points.add(new SnapPoint(new Point(c.getX() - r, c.getY()), SnapType.ENDPOINT, circle));
        points.add(new SnapPoint(new Point(c.getX(), c.getY() + r), SnapType.ENDPOINT, circle));
        points.add(new SnapPoint(new Point(c.getX(), c.getY() - r), SnapType.ENDPOINT, circle));
    }

    private void collectArcSnaps(Arc arc, List<SnapPoint> points) {
        points.add(new SnapPoint(arc.getCenter(), SnapType.CENTER, arc));

        // Начало и конец дуги
        points.add(new SnapPoint(arc.getStartPoint(), SnapType.ENDPOINT, arc));
        points.add(new SnapPoint(arc.getEndPoint(), SnapType.ENDPOINT, arc));

        // Середина дуги
        double midAngle = arc.getStartAngle() + arc.getSweepAngle() / 2;
        Point mid = new Point(
                arc.getCenter().getX() + arc.getRadius() * Math.cos(midAngle),
                arc.getCenter().getY() + arc.getRadius() * Math.sin(midAngle));
        points.add(new SnapPoint(mid, SnapType.MIDPOINT, arc));
    }

    private void collectRectangleSnaps(Rectangle rect, List<SnapPoint> points) {
        points.add(new SnapPoint(rect.getCenter(), SnapType.CENTER, rect));

        Point[] corners = rect.getCorners();
        for (Point corner : corners) {
            points.add(new SnapPoint(corner, SnapType.ENDPOINT, rect));
        }

        for (int i = 0; i < corners.length; i++) {
            Point p1 = corners[i];
            Point p2 = corners[(i + 1) % corners.length];
            Point mid = new Point(
                    (p1.getX() + p2.getX()) / 2,
                    (p1.getY() + p2.getY()) / 2);
            points.add(new SnapPoint(mid, SnapType.MIDPOINT, rect));
        }
    }

    private void collectEllipseSnaps(Ellipse ellipse, List<SnapPoint> points) {
        points.add(new SnapPoint(ellipse.getCenter(), SnapType.CENTER, ellipse));

        points.add(new SnapPoint(ellipse.getPointAtAngle(0), SnapType.ENDPOINT, ellipse));
        points.add(new SnapPoint(ellipse.getPointAtAngle(Math.PI), SnapType.ENDPOINT, ellipse));
        points.add(new SnapPoint(ellipse.getPointAtAngle(Math.PI / 2), SnapType.ENDPOINT, ellipse));
        points.add(new SnapPoint(ellipse.getPointAtAngle(3 * Math.PI / 2), SnapType.ENDPOINT, ellipse));

        points.add(new SnapPoint(ellipse.getPointAtAngle(Math.PI / 4), SnapType.MIDPOINT, ellipse));
        points.add(new SnapPoint(ellipse.getPointAtAngle(3 * Math.PI / 4), SnapType.MIDPOINT, ellipse));
        points.add(new SnapPoint(ellipse.getPointAtAngle(5 * Math.PI / 4), SnapType.MIDPOINT, ellipse));
        points.add(new SnapPoint(ellipse.getPointAtAngle(7 * Math.PI / 4), SnapType.MIDPOINT, ellipse));
    }

    private void collectPolygonSnaps(Polygon polygon, List<SnapPoint> points) {
        points.add(new SnapPoint(polygon.getCenter(), SnapType.CENTER, polygon));

        Point[] vertices = polygon.getVertices();
        for (Point vertex : vertices) {
            points.add(new SnapPoint(vertex, SnapType.ENDPOINT, polygon));
        }

        for (int i = 0; i < vertices.length; i++) {
            Point p1 = vertices[i];
            Point p2 = vertices[(i + 1) % vertices.length];
            Point mid = new Point(
                    (p1.getX() + p2.getX()) / 2,
                    (p1.getY() + p2.getY()) / 2);
            points.add(new SnapPoint(mid, SnapType.MIDPOINT, polygon));
        }
    }

    private void collectSplineSnaps(Spline spline, List<SnapPoint> points) {
        List<ControlPoint> controlPoints = spline.getControlPoints();
        for (ControlPoint cp : controlPoints) {
            points.add(new SnapPoint(cp.getPosition(), SnapType.ENDPOINT, spline));
        }

        for (int i = 0; i < controlPoints.size() - 1; i++) {
            Point p1 = controlPoints.get(i).getPosition();
            Point p2 = controlPoints.get(i + 1).getPosition();
            Point mid = new Point(
                    (p1.getX() + p2.getX()) / 2,
                    (p1.getY() + p2.getY()) / 2);
            points.add(new SnapPoint(mid, SnapType.MIDPOINT, spline));
        }
    }

    private void collectIntersections(List<SnapPoint> points) {
        List<Primitive> primitives = model.getPrimitives();

        for (int i = 0; i < primitives.size(); i++) {
            for (int j = i + 1; j < primitives.size(); j++) {
                Primitive p1 = primitives.get(i);
                Primitive p2 = primitives.get(j);

                List<Point> intersections = findIntersections(p1, p2);
                for (Point intersection : intersections) {
                    points.add(new SnapPoint(intersection, SnapType.INTERSECTION, p1, p2));
                }
            }
        }
    }

    private List<Point> findIntersections(Primitive p1, Primitive p2) {
        List<Point> result = new ArrayList<>();

        List<Segment> segments1 = getSegmentsFromPrimitive(p1);
        List<Segment> segments2 = getSegmentsFromPrimitive(p2);

        for (Segment s1 : segments1) {
            for (Segment s2 : segments2) {
                Point intersection = intersectSegments(s1, s2);
                if (intersection != null) {
                    result.add(intersection);
                }
            }
        }

        // Пересечение отрезка и окружности/эллипса
        if (p1 instanceof Segment) {
            if (p2 instanceof Circle) {
                result.addAll(intersectSegmentCircle((Segment) p1, (Circle) p2));
            } else if (p2 instanceof Ellipse) {
                result.addAll(intersectSegmentEllipse((Segment) p1, (Ellipse) p2));
            } else if (p2 instanceof Arc) {
                result.addAll(intersectSegmentArc((Segment) p1, (Arc) p2));
            }
        } else if (p2 instanceof Segment) {
            if (p1 instanceof Circle) {
                result.addAll(intersectSegmentCircle((Segment) p2, (Circle) p1));
            } else if (p1 instanceof Ellipse) {
                result.addAll(intersectSegmentEllipse((Segment) p2, (Ellipse) p1));
            } else if (p1 instanceof Arc) {
                result.addAll(intersectSegmentArc((Segment) p2, (Arc) p1));
            }
        }

        // Пересечение линий прямоугольника/многоугольника с окружностью/эллипсом
        for (Segment seg : segments1) {
            if (p2 instanceof Circle) {
                result.addAll(intersectSegmentCircle(seg, (Circle) p2));
            } else if (p2 instanceof Ellipse) {
                result.addAll(intersectSegmentEllipse(seg, (Ellipse) p2));
            } else if (p2 instanceof Arc) {
                result.addAll(intersectSegmentArc(seg, (Arc) p2));
            }
        }
        for (Segment seg : segments2) {
            if (p1 instanceof Circle) {
                result.addAll(intersectSegmentCircle(seg, (Circle) p1));
            } else if (p1 instanceof Ellipse) {
                result.addAll(intersectSegmentEllipse(seg, (Ellipse) p1));
            } else if (p1 instanceof Arc) {
                result.addAll(intersectSegmentArc(seg, (Arc) p1));
            }
        }

        // Пересечение двух окружностей
        if (p1 instanceof Circle c1 && p2 instanceof Circle c2) {
            result.addAll(intersectCircles(c1, c2));
        }
        // Пересечение окружности и эллипса (численно)
        else if (p1 instanceof Circle && p2 instanceof Ellipse) {
            result.addAll(intersectCircleEllipse((Circle) p1, (Ellipse) p2));
        } else if (p1 instanceof Ellipse && p2 instanceof Circle) {
            result.addAll(intersectCircleEllipse((Circle) p2, (Ellipse) p1));
        }
        // Пересечение двух эллипсов (численно)
        else if (p1 instanceof Ellipse && p2 instanceof Ellipse) {
            result.addAll(intersectTwoEllipses((Ellipse) p1, (Ellipse) p2));
        }

        // Пересечение дуг
        if (p1 instanceof Arc && p2 instanceof Arc) {
            result.addAll(intersectArcs((Arc) p1, (Arc) p2));
        } else if (p1 instanceof Arc && p2 instanceof Circle) {
            result.addAll(intersectArcCircle((Arc) p1, (Circle) p2));
        } else if (p1 instanceof Circle && p2 instanceof Arc) {
            result.addAll(intersectArcCircle((Arc) p2, (Circle) p1));
        } else if (p1 instanceof Arc && p2 instanceof Ellipse) {
            result.addAll(intersectArcEllipse((Arc) p1, (Ellipse) p2));
        } else if (p1 instanceof Ellipse && p2 instanceof Arc) {
            result.addAll(intersectArcEllipse((Arc) p2, (Ellipse) p1));
        }

        return result;
    }

    /**
     * Пересечение окружности и эллипса.
     */
    private List<Point> intersectCircleEllipse(Circle circle, Ellipse ellipse) {
        List<Point> result = new ArrayList<>();

        // Сэмплируем окружность и ищем точки близкие к эллипсу
        int samples = 360;
        Point center = circle.getCenter();
        double r = circle.getRadius();

        for (int i = 0; i < samples; i++) {
            double t = 2 * Math.PI * i / samples;
            Point p = new Point(
                    center.getX() + r * Math.cos(t),
                    center.getY() + r * Math.sin(t));

            double dist = distanceToEllipse(p, ellipse);
            if (dist < 2.0) {
                Point refined = refineCircleEllipseIntersection(circle, ellipse, t);
                if (refined != null && !isDuplicate(result, refined)) {
                    result.add(refined);
                }
            }
        }

        return result;
    }

    private double distanceToEllipse(Point point, Ellipse ellipse) {
        Point center = ellipse.getCenter();
        double a = ellipse.getSemiMajorAxis();
        double b = ellipse.getSemiMinorAxis();
        double rotation = ellipse.getRotation();

        double cos = Math.cos(-rotation);
        double sin = Math.sin(-rotation);
        double lx = (point.getX() - center.getX()) * cos - (point.getY() - center.getY()) * sin;
        double ly = (point.getX() - center.getX()) * sin + (point.getY() - center.getY()) * cos;

        double normalized = (lx * lx) / (a * a) + (ly * ly) / (b * b);
        return Math.abs(Math.sqrt(normalized) - 1) * Math.min(a, b);
    }

    private Point refineCircleEllipseIntersection(Circle circle, Ellipse ellipse, double t) {
        Point center = circle.getCenter();
        double r = circle.getRadius();

        double bestT = t;
        double bestDist = Double.MAX_VALUE;

        for (double dt = -0.1; dt <= 0.1; dt += 0.01) {
            Point p = new Point(
                    center.getX() + r * Math.cos(t + dt),
                    center.getY() + r * Math.sin(t + dt));
            double dist = distanceToEllipse(p, ellipse);
            if (dist < bestDist) {
                bestDist = dist;
                bestT = t + dt;
            }
        }

        for (double dt = -0.01; dt <= 0.01; dt += 0.001) {
            Point p = new Point(
                    center.getX() + r * Math.cos(bestT + dt),
                    center.getY() + r * Math.sin(bestT + dt));
            double dist = distanceToEllipse(p, ellipse);
            if (dist < bestDist) {
                bestDist = dist;
                bestT = bestT + dt;
            }
        }

        if (bestDist < 0.5) {
            return new Point(
                    center.getX() + r * Math.cos(bestT),
                    center.getY() + r * Math.sin(bestT));
        }
        return null;
    }

    /**
     * Пересечение двух эллипсов.
     */
    private List<Point> intersectTwoEllipses(Ellipse e1, Ellipse e2) {
        List<Point> result = new ArrayList<>();

        int samples = 360;
        for (int i = 0; i < samples; i++) {
            double t = 2 * Math.PI * i / samples;
            Point p = e1.getPointAtAngle(t);

            double dist = distanceToEllipse(p, e2);
            if (dist < 2.0) {
                Point refined = refineTwoEllipsesIntersection(e1, e2, t);
                if (refined != null && !isDuplicate(result, refined)) {
                    result.add(refined);
                }
            }
        }

        return result;
    }

    private Point refineTwoEllipsesIntersection(Ellipse e1, Ellipse e2, double t) {
        double bestT = t;
        double bestDist = Double.MAX_VALUE;

        for (double dt = -0.1; dt <= 0.1; dt += 0.005) {
            Point p = e1.getPointAtAngle(t + dt);
            double dist = distanceToEllipse(p, e2);
            if (dist < bestDist) {
                bestDist = dist;
                bestT = t + dt;
            }
        }

        if (bestDist < 0.5) {
            return e1.getPointAtAngle(bestT);
        }
        return null;
    }

    /**
     * Пересечение двух дуг.
     */
    private List<Point> intersectArcs(Arc a1, Arc a2) {
        List<Point> result = new ArrayList<>();
        List<Point> circleIntersections = intersectCircles(
                createTempCircle(a1.getCenter(), a1.getRadius()),
                createTempCircle(a2.getCenter(), a2.getRadius()));
        for (Point p : circleIntersections) {
            if (isPointOnArc(p, a1) && isPointOnArc(p, a2)) {
                result.add(p);
            }
        }
        return result;
    }

    /**
     * Пересечение дуги и окружности.
     */
    private List<Point> intersectArcCircle(Arc arc, Circle circle) {
        List<Point> result = new ArrayList<>();
        List<Point> circleIntersections = intersectCircles(
                createTempCircle(arc.getCenter(), arc.getRadius()),
                circle);
        for (Point p : circleIntersections) {
            if (isPointOnArc(p, arc)) {
                result.add(p);
            }
        }
        return result;
    }

    /**
     * Пересечение дуги и эллипса.
     */
    private List<Point> intersectArcEllipse(Arc arc, Ellipse ellipse) {
        List<Point> result = new ArrayList<>();
        List<Point> ellipseIntersections = intersectCircleEllipse(
                createTempCircle(arc.getCenter(), arc.getRadius()),
                ellipse);
        for (Point p : ellipseIntersections) {
            if (isPointOnArc(p, arc)) {
                result.add(p);
            }
        }
        return result;
    }

    private List<Segment> getSegmentsFromPrimitive(Primitive p) {
        List<Segment> segments = new ArrayList<>();

        if (p instanceof Segment seg) {
            segments.add(seg);
        } else if (p instanceof Rectangle rect) {
            Point[] corners = rect.getCorners();
            for (int i = 0; i < corners.length; i++) {
                segments.add(createTempSegment(corners[i], corners[(i + 1) % corners.length]));
            }
        } else if (p instanceof Polygon poly) {
            Point[] vertices = poly.getVertices();
            for (int i = 0; i < vertices.length; i++) {
                segments.add(createTempSegment(vertices[i], vertices[(i + 1) % vertices.length]));
            }
        }

        return segments;
    }

    private Segment createTempSegment(Point p1, Point p2) {
        return new Segment(p1, p2, Segment.CreationMode.CARTESIAN, null);
    }

    /**
     * Создаёт временную окружность для вычислений.
     */
    private Circle createTempCircle(Point center, double radius) {
        return new Circle(center, radius, null);
    }

    private Point intersectSegments(Segment s1, Segment s2) {
        double x1 = s1.getStartPoint().getX(), y1 = s1.getStartPoint().getY();
        double x2 = s1.getEndPoint().getX(), y2 = s1.getEndPoint().getY();
        double x3 = s2.getStartPoint().getX(), y3 = s2.getStartPoint().getY();
        double x4 = s2.getEndPoint().getX(), y4 = s2.getEndPoint().getY();

        double denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);
        if (Math.abs(denom) < 1e-10) {
            return null; // Параллельны или совпадают
        }

        double t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / denom;
        double u = -((x1 - x2) * (y1 - y3) - (y1 - y2) * (x1 - x3)) / denom;

        if (t >= 0 && t <= 1 && u >= 0 && u <= 1) {
            return new Point(x1 + t * (x2 - x1), y1 + t * (y2 - y1));
        }

        return null;
    }

    private List<Point> intersectSegmentCircle(Segment segment, Circle circle) {
        List<Point> result = new ArrayList<>();

        double x1 = segment.getStartPoint().getX() - circle.getCenter().getX();
        double y1 = segment.getStartPoint().getY() - circle.getCenter().getY();
        double x2 = segment.getEndPoint().getX() - circle.getCenter().getX();
        double y2 = segment.getEndPoint().getY() - circle.getCenter().getY();

        double dx = x2 - x1;
        double dy = y2 - y1;
        double dr = Math.sqrt(dx * dx + dy * dy);
        double D = x1 * y2 - x2 * y1;
        double r = circle.getRadius();

        double discriminant = r * r * dr * dr - D * D;

        if (discriminant < 0) {
            return result; // Нет пересечений
        }

        double sqrtDisc = Math.sqrt(discriminant);
        double cx = circle.getCenter().getX();
        double cy = circle.getCenter().getY();

        double px1 = cx + (D * dy + Math.signum(dy) * dx * sqrtDisc) / (dr * dr);
        double py1 = cy + (-D * dx + Math.abs(dy) * sqrtDisc) / (dr * dr);

        if (isPointOnSegment(segment, new Point(px1, py1))) {
            result.add(new Point(px1, py1));
        }

        if (discriminant > 0) {
            double px2 = cx + (D * dy - Math.signum(dy) * dx * sqrtDisc) / (dr * dr);
            double py2 = cy + (-D * dx - Math.abs(dy) * sqrtDisc) / (dr * dr);

            if (isPointOnSegment(segment, new Point(px2, py2))) {
                result.add(new Point(px2, py2));
            }
        }

        return result;
    }

    private boolean isPointOnSegment(Segment segment, Point point) {
        double minX = Math.min(segment.getStartPoint().getX(), segment.getEndPoint().getX()) - 0.001;
        double maxX = Math.max(segment.getStartPoint().getX(), segment.getEndPoint().getX()) + 0.001;
        double minY = Math.min(segment.getStartPoint().getY(), segment.getEndPoint().getY()) - 0.001;
        double maxY = Math.max(segment.getStartPoint().getY(), segment.getEndPoint().getY()) + 0.001;

        return point.getX() >= minX && point.getX() <= maxX &&
                point.getY() >= minY && point.getY() <= maxY;
    }

    private List<Point> intersectCircles(Circle c1, Circle c2) {
        List<Point> result = new ArrayList<>();

        double x1 = c1.getCenter().getX(), y1 = c1.getCenter().getY(), r1 = c1.getRadius();
        double x2 = c2.getCenter().getX(), y2 = c2.getCenter().getY(), r2 = c2.getRadius();

        double d = Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));

        if (d > r1 + r2 || d < Math.abs(r1 - r2) || d == 0) {
            return result;
        }

        double a = (r1 * r1 - r2 * r2 + d * d) / (2 * d);
        double h = Math.sqrt(r1 * r1 - a * a);

        double px = x1 + a * (x2 - x1) / d;
        double py = y1 + a * (y2 - y1) / d;

        result.add(new Point(
                px + h * (y2 - y1) / d,
                py - h * (x2 - x1) / d));

        if (h > 0.001) { // Две точки пересечения
            result.add(new Point(
                    px - h * (y2 - y1) / d,
                    py + h * (x2 - x1) / d));
        }

        return result;
    }

    /**
     * Пересечение отрезка и эллипса.
     */
    private List<Point> intersectSegmentEllipse(Segment segment, Ellipse ellipse) {
        List<Point> result = new ArrayList<>();

        Point center = ellipse.getCenter();
        double a = ellipse.getSemiMajorAxis();
        double b = ellipse.getSemiMinorAxis();
        double rotation = ellipse.getRotation();

        // Переводим отрезок в систему координат эллипса
        double cos = Math.cos(-rotation);
        double sin = Math.sin(-rotation);

        Point p1 = segment.getStartPoint();
        Point p2 = segment.getEndPoint();

        double x1 = (p1.getX() - center.getX()) * cos - (p1.getY() - center.getY()) * sin;
        double y1 = (p1.getX() - center.getX()) * sin + (p1.getY() - center.getY()) * cos;
        double x2 = (p2.getX() - center.getX()) * cos - (p2.getY() - center.getY()) * sin;
        double y2 = (p2.getX() - center.getX()) * sin + (p2.getY() - center.getY()) * cos;

        double dx = x2 - x1;
        double dy = y2 - y1;

        // Подставляем в уравнение эллипса: (x/a)^2 + (y/b)^2 = 1
        double A = (dx * dx) / (a * a) + (dy * dy) / (b * b);
        double B = 2 * ((x1 * dx) / (a * a) + (y1 * dy) / (b * b));
        double C = (x1 * x1) / (a * a) + (y1 * y1) / (b * b) - 1;

        double discriminant = B * B - 4 * A * C;

        if (discriminant < 0) {
            return result;
        }

        double sqrtD = Math.sqrt(discriminant);
        double t1 = (-B + sqrtD) / (2 * A);
        double t2 = (-B - sqrtD) / (2 * A);

        cos = Math.cos(rotation);
        sin = Math.sin(rotation);

        if (t1 >= 0 && t1 <= 1) {
            double lx = x1 + t1 * dx;
            double ly = y1 + t1 * dy;
            double wx = lx * cos - ly * sin + center.getX();
            double wy = lx * sin + ly * cos + center.getY();
            result.add(new Point(wx, wy));
        }

        if (discriminant > 0 && t2 >= 0 && t2 <= 1 && Math.abs(t1 - t2) > 0.001) {
            double lx = x1 + t2 * dx;
            double ly = y1 + t2 * dy;
            double wx = lx * cos - ly * sin + center.getX();
            double wy = lx * sin + ly * cos + center.getY();
            result.add(new Point(wx, wy));
        }

        return result;
    }

    /**
     * Пересечение отрезка и дуги.
     */
    private List<Point> intersectSegmentArc(Segment segment, Arc arc) {
        List<Point> result = new ArrayList<>();

        // Сначала находим пересечения с полной окружностью
        Circle fullCircle = createTempCircle(arc.getCenter(), arc.getRadius());
        List<Point> circleIntersections = intersectSegmentCircle(segment, fullCircle);

        // Фильтруем точки, которые лежат на дуге
        for (Point p : circleIntersections) {
            if (isPointOnArc(p, arc)) {
                result.add(p);
            }
        }

        return result;
    }

    /**
     * Проверяет, лежит ли точка на дуге.
     */
    private boolean isPointOnArc(Point p, Arc arc) {
        double dx = p.getX() - arc.getCenter().getX();
        double dy = p.getY() - arc.getCenter().getY();
        double angle = Math.atan2(dy, dx);

        double startAngle = normalizeAngle(arc.getStartAngle());
        double endAngle = normalizeAngle(arc.getStartAngle() + arc.getSweepAngle());
        double pointAngle = normalizeAngle(angle);

        if (arc.getSweepAngle() >= 0) {
            if (startAngle <= endAngle) {
                return pointAngle >= startAngle && pointAngle <= endAngle;
            } else {
                return pointAngle >= startAngle || pointAngle <= endAngle;
            }
        } else {
            if (endAngle <= startAngle) {
                return pointAngle <= startAngle && pointAngle >= endAngle;
            } else {
                return pointAngle <= startAngle || pointAngle >= endAngle;
            }
        }
    }

    private double normalizeAngle(double angle) {
        while (angle < 0)
            angle += 2 * Math.PI;
        while (angle >= 2 * Math.PI)
            angle -= 2 * Math.PI;
        return angle;
    }

    private boolean isDuplicate(List<Point> points, Point newPoint) {
        for (Point p : points) {
            double dx = p.getX() - newPoint.getX();
            double dy = p.getY() - newPoint.getY();
            if (Math.sqrt(dx * dx + dy * dy) < 2.0) {
                return true;
            }
        }
        return false;
    }


    /**
     * Собирает точки перпендикуляра.
     * Если есть basePoint - ищем точку на примитиве, где линия от basePoint
     * перпендикулярна примитиву.
     * Если нет basePoint - ищем ближайшую точку на примитиве к курсору.
     */
    private void collectPerpendicularSnaps(Point cursorPos, List<SnapPoint> points, double captureRadius) {
        Point fromPoint = basePoint != null ? basePoint : cursorPos;

        for (Primitive primitive : model.getPrimitives()) {
            List<Point> perpPoints = findPerpendicularPoints(fromPoint, cursorPos, primitive);
            for (Point perpPoint : perpPoints) {
                points.add(new SnapPoint(perpPoint, SnapType.PERPENDICULAR, primitive));
            }
        }
    }

    /**
     * Находит точки перпендикуляра от исходной точки к примитиву.
     */
    private List<Point> findPerpendicularPoints(Point fromPoint, Point cursorPos, Primitive primitive) {
        List<Point> result = new ArrayList<>();

        if (primitive instanceof Segment segment) {
            Point p = perpendicularToSegment(fromPoint, segment);
            if (p != null)
                result.add(p);
        } else if (primitive instanceof Circle circle) {
            result.addAll(perpendicularToCircleFromPoint(fromPoint, circle));
        } else if (primitive instanceof Ellipse ellipse) {
            Point p = perpendicularToEllipse(fromPoint, ellipse);
            if (p != null)
                result.add(p);
        } else if (primitive instanceof Arc arc) {
            Point p = perpendicularToArc(cursorPos, arc);
            if (p != null)
                result.add(p);
        } else if (primitive instanceof Rectangle rect) {
            Point p = perpendicularToRectangle(fromPoint, rect);
            if (p != null)
                result.add(p);
        } else if (primitive instanceof Polygon poly) {
            Point p = perpendicularToPolygon(fromPoint, poly);
            if (p != null)
                result.add(p);
        }

        return result;
    }

    /**
     * Находит точки на окружности, где линия от внешней точки перпендикулярна
     * касательной.
     * Это две точки - на линии от fromPoint через центр окружности.
     */
    private List<Point> perpendicularToCircleFromPoint(Point fromPoint, Circle circle) {
        List<Point> result = new ArrayList<>();

        Point center = circle.getCenter();
        double r = circle.getRadius();

        double dx = center.getX() - fromPoint.getX();
        double dy = center.getY() - fromPoint.getY();
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist < 0.0001)
            return result;

        // Нормализованный вектор от fromPoint к center
        double nx = dx / dist;
        double ny = dy / dist;

        // Две точки перпендикуляра: ближняя и дальняя
        result.add(new Point(
                center.getX() - r * nx,
                center.getY() - r * ny));
        result.add(new Point(
                center.getX() + r * nx,
                center.getY() + r * ny));

        return result;
    }

    private Point perpendicularToSegment(Point cursor, Segment segment) {
        Point p1 = segment.getStartPoint();
        Point p2 = segment.getEndPoint();

        double dx = p2.getX() - p1.getX();
        double dy = p2.getY() - p1.getY();
        double lenSq = dx * dx + dy * dy;

        if (lenSq < 0.0001)
            return null;

        double t = ((cursor.getX() - p1.getX()) * dx + (cursor.getY() - p1.getY()) * dy) / lenSq;

        // Проверяем, что перпендикуляр попадает на отрезок
        if (t < 0 || t > 1)
            return null;

        return new Point(p1.getX() + t * dx, p1.getY() + t * dy);
    }

    private Point perpendicularToEllipse(Point cursor, Ellipse ellipse) {
        // Численный поиск ближайшей точки на эллипсе
        double bestT = 0;
        double bestDist = Double.MAX_VALUE;

        for (int i = 0; i < 72; i++) {
            double t = 2 * Math.PI * i / 72;
            Point p = ellipse.getPointAtAngle(t);
            double dist = distance(cursor, p);
            if (dist < bestDist) {
                bestDist = dist;
                bestT = t;
            }
        }

        for (double dt = -0.1; dt <= 0.1; dt += 0.01) {
            Point p = ellipse.getPointAtAngle(bestT + dt);
            double dist = distance(cursor, p);
            if (dist < bestDist) {
                bestDist = dist;
                bestT = bestT + dt;
            }
        }

        return ellipse.getPointAtAngle(bestT);
    }

    private Point perpendicularToArc(Point cursor, Arc arc) {
        Point center = arc.getCenter();
        double dx = cursor.getX() - center.getX();
        double dy = cursor.getY() - center.getY();
        double angle = Math.atan2(dy, dx);

        // Проверяем, попадает ли угол в дугу
        double startAngle = arc.getStartAngle();
        double sweepAngle = arc.getSweepAngle();

        double normalizedAngle = normalizeAngle(angle);
        double normalizedStart = normalizeAngle(startAngle);
        double normalizedEnd = normalizeAngle(startAngle + sweepAngle);

        boolean onArc;
        if (sweepAngle >= 0) {
            if (normalizedStart <= normalizedEnd) {
                onArc = normalizedAngle >= normalizedStart && normalizedAngle <= normalizedEnd;
            } else {
                onArc = normalizedAngle >= normalizedStart || normalizedAngle <= normalizedEnd;
            }
        } else {
            if (normalizedEnd <= normalizedStart) {
                onArc = normalizedAngle <= normalizedStart && normalizedAngle >= normalizedEnd;
            } else {
                onArc = normalizedAngle <= normalizedStart || normalizedAngle >= normalizedEnd;
            }
        }

        if (!onArc)
            return null;

        return new Point(
                center.getX() + arc.getRadius() * Math.cos(angle),
                center.getY() + arc.getRadius() * Math.sin(angle));
    }

    private Point perpendicularToRectangle(Point cursor, Rectangle rect) {
        Point[] corners = rect.getCorners();
        Point bestPoint = null;
        double bestDist = Double.MAX_VALUE;

        for (int i = 0; i < corners.length; i++) {
            Segment side = createTempSegment(corners[i], corners[(i + 1) % corners.length]);
            Point perp = perpendicularToSegment(cursor, side);
            if (perp != null) {
                double dist = distance(cursor, perp);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestPoint = perp;
                }
            }
        }
        return bestPoint;
    }

    private Point perpendicularToPolygon(Point cursor, Polygon poly) {
        Point[] vertices = poly.getVertices();
        Point bestPoint = null;
        double bestDist = Double.MAX_VALUE;

        for (int i = 0; i < vertices.length; i++) {
            Segment side = createTempSegment(vertices[i], vertices[(i + 1) % vertices.length]);
            Point perp = perpendicularToSegment(cursor, side);
            if (perp != null) {
                double dist = distance(cursor, perp);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestPoint = perp;
                }
            }
        }
        return bestPoint;
    }

    /**
     * Собирает точки касательной к окружностям/эллипсам.
     * Касательная работает ТОЛЬКО когда есть basePoint (первая точка построения
     * линии).
     * Показывает точки на кривой, где линия от basePoint будет касательной.
     */
    private void collectTangentSnaps(Point cursorPos, List<SnapPoint> points, double captureRadius) {
        // Касательная имеет смысл только при наличии начальной точки
        if (basePoint == null) {
            return;
        }

        for (Primitive primitive : model.getPrimitives()) {
            List<Point> tangentPoints = findTangentPoints(basePoint, primitive);
            for (Point tp : tangentPoints) {
                points.add(new SnapPoint(tp, SnapType.TANGENT, primitive));
            }
        }
    }

    /**
     * Находит точки касания от внешней точки к примитиву.
     */
    private List<Point> findTangentPoints(Point externalPoint, Primitive primitive) {
        List<Point> result = new ArrayList<>();

        if (primitive instanceof Circle circle) {
            result.addAll(tangentToCircle(externalPoint, circle));
        } else if (primitive instanceof Arc arc) {
            result.addAll(tangentToArc(externalPoint, arc));
        } else if (primitive instanceof Ellipse ellipse) {
            result.addAll(tangentToEllipse(externalPoint, ellipse));
        }

        return result;
    }

    private List<Point> tangentToCircle(Point external, Circle circle) {
        List<Point> result = new ArrayList<>();

        Point center = circle.getCenter();
        double r = circle.getRadius();

        double dx = external.getX() - center.getX();
        double dy = external.getY() - center.getY();
        double d = Math.sqrt(dx * dx + dy * dy);

        // Если точка на окружности (с погрешностью) - возвращаем точки касательной
        // вдоль направления касательной от этой точки
        double tolerance = r * 0.1; // 10% от радиуса
        if (d >= r - tolerance && d <= r + tolerance) {
            // Точка примерно на окружности - касательная перпендикулярна радиусу
            double angle = Math.atan2(dy, dx);
            // Направление касательной (перпендикуляр к радиусу)
            double tangentDirX = -Math.sin(angle);
            double tangentDirY = Math.cos(angle);
            double offset = r; // Смещение для визуализации
            result.add(new Point(
                    external.getX() + tangentDirX * offset,
                    external.getY() + tangentDirY * offset));
            result.add(new Point(
                    external.getX() - tangentDirX * offset,
                    external.getY() - tangentDirY * offset));
            return result;
        }

        // Точка внутри окружности - нет касательных
        if (d < r - tolerance)
            return result;

        // Точка снаружи - стандартный расчёт касательных
        double angle = Math.atan2(dy, dx);

        // Угол между радиусом и касательной
        double tangentAngle = Math.acos(r / d);

        result.add(new Point(
                center.getX() + r * Math.cos(angle + tangentAngle),
                center.getY() + r * Math.sin(angle + tangentAngle)));
        result.add(new Point(
                center.getX() + r * Math.cos(angle - tangentAngle),
                center.getY() + r * Math.sin(angle - tangentAngle)));

        return result;
    }

    private List<Point> tangentToArc(Point external, Arc arc) {
        List<Point> result = new ArrayList<>();

        // Находим касательные к полной окружности
        Circle fullCircle = createTempCircle(arc.getCenter(), arc.getRadius());
        List<Point> circleTangents = tangentToCircle(external, fullCircle);

        // Фильтруем те, что лежат на дуге
        for (Point tp : circleTangents) {
            if (isPointOnArc(tp, arc)) {
                result.add(tp);
            }
        }

        return result;
    }

    /**
     * Находит точки касания от внешней точки к эллипсу.
     * Касательная от внешней точки E к эллипсу в точке P(t) означает,
     * что вектор (P(t) - E) параллелен касательному вектору T(t),
     * т.е. векторное произведение (P - E) × T = 0.
     */
    private List<Point> tangentToEllipse(Point external, Ellipse ellipse) {
        List<Point> result = new ArrayList<>();

        double a = ellipse.getSemiMajorAxis();
        double b = ellipse.getSemiMinorAxis();
        double rotation = ellipse.getRotation();
        Point center = ellipse.getCenter();

        // Переводим внешнюю точку в локальные координаты эллипса
        double cos = Math.cos(-rotation);
        double sin = Math.sin(-rotation);
        double localExtX = (external.getX() - center.getX()) * cos - (external.getY() - center.getY()) * sin;
        double localExtY = (external.getX() - center.getX()) * sin + (external.getY() - center.getY()) * cos;

        // Проверяем, не внутри ли эллипса точка
        double normalized = (localExtX * localExtX) / (a * a) + (localExtY * localExtY) / (b * b);
        if (normalized < 0.9)
            return result; // Точка внутри - нет касательных

        // Сэмплируем эллипс и ищем точки, где векторное произведение меняет знак
        // Условие касательной: (P - E) × T = 0 (вектор от E к P параллелен касательной)
        int samples = 360;
        double[] crossProducts = new double[samples];

        for (int i = 0; i < samples; i++) {
            double t = 2 * Math.PI * i / samples;
            // Точка на эллипсе в локальных координатах
            double px = a * Math.cos(t);
            double py = b * Math.sin(t);
            // Касательный вектор в локальных координатах
            double tx = -a * Math.sin(t);
            double ty = b * Math.cos(t);
            // Вектор от внешней точки к точке на эллипсе
            double vx = px - localExtX;
            double vy = py - localExtY;
            // Векторное (перекрёстное) произведение: vx * ty - vy * tx
            crossProducts[i] = vx * ty - vy * tx;
        }

        for (int i = 0; i < samples; i++) {
            int next = (i + 1) % samples;
            if (crossProducts[i] * crossProducts[next] < 0) {
                // Уточнение методом бисекции
                double t1 = 2 * Math.PI * i / samples;
                double t2 = 2 * Math.PI * next / samples;
                if (next == 0)
                    t2 = 2 * Math.PI;

                for (int iter = 0; iter < 20; iter++) {
                    double tMid = (t1 + t2) / 2;
                    double px = a * Math.cos(tMid);
                    double py = b * Math.sin(tMid);
                    double tx = -a * Math.sin(tMid);
                    double ty = b * Math.cos(tMid);
                    double vx = px - localExtX;
                    double vy = py - localExtY;
                    double cross = vx * ty - vy * tx;

                    double px1 = a * Math.cos(t1);
                    double py1 = b * Math.sin(t1);
                    double tx1 = -a * Math.sin(t1);
                    double ty1 = b * Math.cos(t1);
                    double cross1 = (px1 - localExtX) * ty1 - (py1 - localExtY) * tx1;

                    if (cross1 * cross < 0) {
                        t2 = tMid;
                    } else {
                        t1 = tMid;
                    }
                }

                double tResult = (t1 + t2) / 2;
                Point tangentPoint = ellipse.getPointAtAngle(tResult);

                if (!isDuplicate(result, tangentPoint)) {
                    result.add(tangentPoint);
                }
            }
        }

        return result;
    }

    private double distance(Point p1, Point p2) {
        double dx = p1.getX() - p2.getX();
        double dy = p1.getY() - p2.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }
}


