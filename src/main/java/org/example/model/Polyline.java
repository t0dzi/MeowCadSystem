package org.example.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Polyline extends Primitive {

    private final List<Point> vertices;
    private boolean closed;

    public Polyline(List<Point> vertices, LineStyle lineStyle) {
        this(vertices, false, lineStyle);
    }

    public Polyline(List<Point> vertices, boolean closed, LineStyle lineStyle) {
        super(lineStyle);
        this.vertices = new ArrayList<>();
        setVertices(vertices);
        this.closed = closed;
    }

    public List<Point> getVertices() {
        return Collections.unmodifiableList(vertices);
    }

    public Point getVertex(int index) {
        if (index < 0 || index >= vertices.size()) {
            return null;
        }
        return vertices.get(index);
    }

    public int getVertexCount() {
        return vertices.size();
    }

    public void setVertices(List<Point> points) {
        vertices.clear();
        if (points == null) {
            return;
        }
        for (Point point : points) {
            if (point != null) {
                vertices.add(new Point(point.getX(), point.getY()));
            }
        }
    }

    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    public double getLength() {
        if (vertices.size() < 2) {
            return 0.0;
        }

        double length = 0.0;
        for (int i = 0; i < vertices.size() - 1; i++) {
            length += distance(vertices.get(i), vertices.get(i + 1));
        }
        if (closed && vertices.size() > 2) {
            length += distance(vertices.get(vertices.size() - 1), vertices.get(0));
        }
        return length;
    }

    public List<Segment> explodeToSegments() {
        List<Segment> segments = new ArrayList<>();
        if (vertices.size() < 2) {
            return segments;
        }

        for (int i = 0; i < vertices.size() - 1; i++) {
            segments.add(new Segment(vertices.get(i), vertices.get(i + 1),
                    Segment.CreationMode.CARTESIAN, lineStyle));
        }
        if (closed && vertices.size() > 2) {
            segments.add(new Segment(vertices.get(vertices.size() - 1), vertices.get(0),
                    Segment.CreationMode.CARTESIAN, lineStyle));
        }
        return segments;
    }

    @Override
    public PrimitiveType getType() {
        return PrimitiveType.POLYLINE;
    }

    @Override
    public List<ControlPoint> getControlPoints() {
        List<ControlPoint> points = new ArrayList<>();
        for (int i = 0; i < vertices.size(); i++) {
            points.add(new ControlPoint(vertices.get(i), ControlPoint.Type.ENDPOINT, i,
                    "Вершина " + (i + 1)));
        }
        return points;
    }

    @Override
    public void moveControlPoint(int pointIndex, Point newPosition) {
        if (pointIndex >= 0 && pointIndex < vertices.size()) {
            vertices.set(pointIndex, newPosition);
        }
    }

    @Override
    public void translate(double dx, double dy) {
        for (int i = 0; i < vertices.size(); i++) {
            Point point = vertices.get(i);
            vertices.set(i, new Point(point.getX() + dx, point.getY() + dy));
        }
    }

    @Override
    public boolean containsPoint(Point point, double tolerance) {
        if (vertices.size() < 2) {
            return false;
        }

        for (int i = 0; i < vertices.size() - 1; i++) {
            if (distanceToSegment(point, vertices.get(i), vertices.get(i + 1)) < tolerance) {
                return true;
            }
        }

        return closed && vertices.size() > 2
                && distanceToSegment(point, vertices.get(vertices.size() - 1), vertices.get(0)) < tolerance;
    }

    @Override
    public double[] getBoundingBox() {
        if (vertices.isEmpty()) {
            return new double[] { 0, 0, 0, 0 };
        }

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        for (Point point : vertices) {
            minX = Math.min(minX, point.getX());
            minY = Math.min(minY, point.getY());
            maxX = Math.max(maxX, point.getX());
            maxY = Math.max(maxY, point.getY());
        }

        return new double[] { minX, minY, maxX, maxY };
    }

    @Override
    public Map<String, Object> getProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put("Кол-во вершин", vertices.size());
        props.put("Замкнута", closed);
        props.put("Длина", getLength());
        return props;
    }

    @Override
    public boolean setProperty(String propertyName, Object value) {
        if ("Замкнута".equals(propertyName)) {
            closed = value instanceof Boolean ? (Boolean) value : Boolean.parseBoolean(String.valueOf(value));
            return true;
        }
        return false;
    }

    @Override
    public Point getCenter() {
        if (vertices.isEmpty()) {
            return new Point(0, 0);
        }

        double sumX = 0.0;
        double sumY = 0.0;
        for (Point vertex : vertices) {
            sumX += vertex.getX();
            sumY += vertex.getY();
        }
        return new Point(sumX / vertices.size(), sumY / vertices.size());
    }
}
