package org.example.model;

import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PrimitiveSnapshot {

    private final Primitive primitive;
    private final LineStyleSnapshot lineStyle;
    private final String layerName;
    private final int colorAci;
    private final Map<String, Object> values;

    private PrimitiveSnapshot(Primitive primitive) {
        this.primitive = primitive;
        this.lineStyle = LineStyleSnapshot.capture(primitive.getLineStyle());
        this.layerName = primitive.getLayerName();
        this.colorAci = primitive.getColorAci();
        this.values = captureValues(primitive);
    }

    public static PrimitiveSnapshot capture(Primitive primitive) {
        return new PrimitiveSnapshot(primitive);
    }

    public boolean hasChanged() {
        PrimitiveSnapshot current = capture(primitive);
        return !Objects.equals(lineStyle, current.lineStyle)
                || !Objects.equals(layerName, current.layerName)
                || colorAci != current.colorAci
                || !Objects.equals(values, current.values);
    }

    public void restore() {
        restoreValues(primitive, values);
        primitive.setLineStyle(lineStyle != null ? lineStyle.style() : null);
        primitive.setLayerName(layerName);
        primitive.setColorAci(colorAci);
        if (lineStyle != null) {
            lineStyle.restore();
        }
    }

    private static Map<String, Object> captureValues(Primitive primitive) {
        Map<String, Object> values = new LinkedHashMap<>();

        if (primitive instanceof PointPrimitive point) {
            values.put("position", point(point.getPosition()));
        } else if (primitive instanceof Segment segment) {
            values.put("start", point(segment.getStartPoint()));
            values.put("end", point(segment.getEndPoint()));
        } else if (primitive instanceof Circle circle) {
            values.put("center", point(circle.getCenter()));
            values.put("radius", circle.getRadius());
        } else if (primitive instanceof Arc arc) {
            values.put("center", point(arc.getCenter()));
            values.put("radius", arc.getRadius());
            values.put("startAngle", arc.getStartAngle());
            values.put("endAngle", arc.getEndAngle());
        } else if (primitive instanceof Rectangle rectangle) {
            values.put("center", point(rectangle.getCenter()));
            values.put("width", rectangle.getWidth());
            values.put("height", rectangle.getHeight());
            values.put("cornerType", rectangle.getCornerType());
            values.put("cornerRadius", rectangle.getCornerRadius());
        } else if (primitive instanceof Ellipse ellipse) {
            values.put("center", point(ellipse.getCenter()));
            values.put("semiMajorAxis", ellipse.getSemiMajorAxis());
            values.put("semiMinorAxis", ellipse.getSemiMinorAxis());
            values.put("rotation", ellipse.getRotation());
        } else if (primitive instanceof Polygon polygon) {
            values.put("center", point(polygon.getCenter()));
            values.put("radius", polygon.getRadius());
            values.put("sides", polygon.getSides());
            values.put("inscriptionType", polygon.getInscriptionType());
            values.put("rotation", polygon.getRotation());
        } else if (primitive instanceof Polyline polyline) {
            values.put("vertices", points(polyline.getVertices()));
            values.put("closed", polyline.isClosed());
        } else if (primitive instanceof Spline spline) {
            values.put("controlPoints", points(spline.getControlPointsList()));
            values.put("closed", spline.isClosed());
            values.put("tension", spline.getTension());
        }

        if (primitive instanceof DimensionPrimitive dimension) {
            captureDimensionValues(dimension, values);
        }

        return values;
    }

    private static void captureDimensionValues(DimensionPrimitive dimension, Map<String, Object> values) {
        values.put("textOverride", dimension.getTextOverride());
        values.put("textPosition", point(dimension.getTextPosition()));
        values.put("textMoved", dimension.isTextPositionManuallyMoved());
        values.put("extensionLineStyle", LineStyleSnapshot.capture(dimension.getExtensionLineStyle()));
        values.put("dimensionLineColor", dimension.getDimensionLineColor());
        values.put("extensionLineColor", dimension.getExtensionLineColor());
        values.put("extensionLineOffset", dimension.getExtensionLineOffset());
        values.put("extensionLineOvershoot", dimension.getExtensionLineOvershoot());
        values.put("dimensionLineExtension", dimension.getDimensionLineExtension());
        values.put("arrowSize", dimension.getArrowSize());
        values.put("filledArrows", dimension.isFilledArrows());
        values.put("arrowType", dimension.getArrowType());
        values.put("fontVariant", dimension.getFontVariant());
        values.put("textPlacement", dimension.getTextPlacement());
        values.put("textHeight", dimension.getTextHeight());
        values.put("textGap", dimension.getTextGap());

        if (dimension instanceof LinearDimension linear) {
            values.put("dimensionLinePoint", point(linear.getDimensionLinePoint()));
            values.put("orientation", linear.getOrientation());
            values.put("textPositionFactor", linear.getTextPositionFactor());
        } else if (dimension instanceof RadialDimension radial) {
            values.put("leaderPoint", point(radial.getLeaderPoint()));
            values.put("kind", radial.getKind());
            values.put("shelfSide", radial.getShelfSide());
            values.put("cornerIndex", radial.getCornerIndex());
        } else if (dimension instanceof AngularDimension angular) {
            values.put("arcPoint", point(angular.getArcPoint()));
        }
    }

    private static void restoreValues(Primitive primitive, Map<String, Object> values) {
        if (primitive instanceof PointPrimitive point) {
            point.setPosition(toPoint(values.get("position")));
        } else if (primitive instanceof Segment segment) {
            segment.moveControlPoint(0, toPoint(values.get("start")));
            segment.moveControlPoint(1, toPoint(values.get("end")));
        } else if (primitive instanceof Circle circle) {
            circle.setCenter(toPoint(values.get("center")));
            circle.setRadius((double) values.get("radius"));
        } else if (primitive instanceof Arc arc) {
            arc.setCenter(toPoint(values.get("center")));
            arc.setRadius((double) values.get("radius"));
            arc.setStartAngle((double) values.get("startAngle"));
            arc.setEndAngle((double) values.get("endAngle"));
        } else if (primitive instanceof Rectangle rectangle) {
            rectangle.setCenter(toPoint(values.get("center")));
            rectangle.setWidth((double) values.get("width"));
            rectangle.setHeight((double) values.get("height"));
            rectangle.setCornerType((Rectangle.CornerType) values.get("cornerType"));
            rectangle.setCornerRadius((double) values.get("cornerRadius"));
        } else if (primitive instanceof Ellipse ellipse) {
            ellipse.setCenter(toPoint(values.get("center")));
            ellipse.setSemiMajorAxis((double) values.get("semiMajorAxis"));
            ellipse.setSemiMinorAxis((double) values.get("semiMinorAxis"));
            ellipse.setRotation((double) values.get("rotation"));
        } else if (primitive instanceof Polygon polygon) {
            polygon.setCenter(toPoint(values.get("center")));
            polygon.setRadius((double) values.get("radius"));
            polygon.setSides((int) values.get("sides"));
            polygon.setInscriptionType((Polygon.InscriptionType) values.get("inscriptionType"));
            polygon.setRotation((double) values.get("rotation"));
        } else if (primitive instanceof Polyline polyline) {
            polyline.setVertices(toPoints(values.get("vertices")));
            polyline.setClosed((boolean) values.get("closed"));
        } else if (primitive instanceof Spline spline) {
            spline.setControlPoints(toPoints(values.get("controlPoints")));
            spline.setClosed((boolean) values.get("closed"));
            spline.setTension((double) values.get("tension"));
        }

        if (primitive instanceof DimensionPrimitive dimension) {
            restoreDimensionValues(dimension, values);
        }
    }

    private static void restoreDimensionValues(DimensionPrimitive dimension, Map<String, Object> values) {
        dimension.setTextOverride((String) values.get("textOverride"));
        dimension.setExtensionLineStyle(((LineStyleSnapshot) values.get("extensionLineStyle")).style());
        ((LineStyleSnapshot) values.get("extensionLineStyle")).restore();
        dimension.setDimensionLineColor((Color) values.get("dimensionLineColor"));
        dimension.setExtensionLineColor((Color) values.get("extensionLineColor"));
        dimension.setExtensionLineOffset((double) values.get("extensionLineOffset"));
        dimension.setExtensionLineOvershoot((double) values.get("extensionLineOvershoot"));
        dimension.setDimensionLineExtension((double) values.get("dimensionLineExtension"));
        dimension.setArrowSize((double) values.get("arrowSize"));
        dimension.setFilledArrows((boolean) values.get("filledArrows"));
        dimension.setArrowType((DimensionPrimitive.ArrowType) values.get("arrowType"));
        dimension.setFontVariant((DimensionPrimitive.FontVariant) values.get("fontVariant"));
        dimension.setTextPlacement((DimensionPrimitive.TextPlacement) values.get("textPlacement"));
        dimension.setTextHeight((double) values.get("textHeight"));
        dimension.setTextGap((double) values.get("textGap"));

        if (dimension instanceof LinearDimension linear) {
            linear.setDimensionLinePoint(toPoint(values.get("dimensionLinePoint")));
            linear.setOrientation((LinearDimension.Orientation) values.get("orientation"));
            linear.setTextPositionFactor((double) values.get("textPositionFactor"));
        } else if (dimension instanceof RadialDimension radial) {
            radial.setLeaderPoint(toPoint(values.get("leaderPoint")));
            radial.setKind((RadialDimension.Kind) values.get("kind"));
            radial.setShelfSide((RadialDimension.ShelfSide) values.get("shelfSide"));
            radial.setCornerIndex((int) values.get("cornerIndex"));
        } else if (dimension instanceof AngularDimension angular) {
            angular.setArcPoint(toPoint(values.get("arcPoint")));
        }

        if ((boolean) values.get("textMoved")) {
            dimension.setTextPosition(toPoint(values.get("textPosition")));
        } else {
            dimension.resetTextPosition();
        }
    }

    private static DPoint point(Point point) {
        return point == null ? null : new DPoint(point.getX(), point.getY());
    }

    private static List<DPoint> points(List<Point> points) {
        List<DPoint> result = new ArrayList<>();
        for (Point point : points) {
            result.add(point(point));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<Point> toPoints(Object value) {
        List<Point> result = new ArrayList<>();
        for (DPoint point : (List<DPoint>) value) {
            result.add(toPoint(point));
        }
        return result;
    }

    private static Point toPoint(Object value) {
        DPoint point = (DPoint) value;
        return point == null ? null : new Point(point.x(), point.y());
    }

    private record DPoint(double x, double y) {
    }

    private record LineStyleSnapshot(
            LineStyle style,
            String name,
            LineType type,
            double thickness,
            double thicknessMm,
            double[] dashPattern,
            double dashLengthMm,
            double dashGapMm,
            double waveAmplitude,
            double waveLength,
            boolean system,
            LineStyle.ThicknessCategory thicknessCategory) {

        static LineStyleSnapshot capture(LineStyle style) {
            if (style == null) {
                return null;
            }
            double[] dash = style.getDashPattern();
            return new LineStyleSnapshot(
                    style,
                    style.getName(),
                    style.getType(),
                    style.getThickness(),
                    style.getThicknessMm(),
                    dash == null ? null : dash.clone(),
                    style.getDashLengthMm(),
                    style.getDashGapMm(),
                    style.getWaveAmplitude(),
                    style.getWaveLength(),
                    style.isSystem(),
                    style.getThicknessCategory());
        }

        void restore() {
            if (style == null) {
                return;
            }
            style.setName(name);
            style.setType(type);
            style.setThickness(thickness);
            style.setThicknessMm(thicknessMm);
            style.setDashPattern(dashPattern == null ? null : dashPattern.clone());
            style.setDashLengthMm(dashLengthMm);
            style.setDashGapMm(dashGapMm);
            style.setWaveAmplitude(waveAmplitude);
            style.setWaveLength(waveLength);
            style.setIsSystem(system);
            style.setThicknessCategory(thicknessCategory);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof LineStyleSnapshot other)) {
                return false;
            }
            return style == other.style
                    && Double.compare(thickness, other.thickness) == 0
                    && Double.compare(thicknessMm, other.thicknessMm) == 0
                    && Double.compare(dashLengthMm, other.dashLengthMm) == 0
                    && Double.compare(dashGapMm, other.dashGapMm) == 0
                    && Double.compare(waveAmplitude, other.waveAmplitude) == 0
                    && Double.compare(waveLength, other.waveLength) == 0
                    && system == other.system
                    && Objects.equals(name, other.name)
                    && type == other.type
                    && Arrays.equals(dashPattern, other.dashPattern)
                    && thicknessCategory == other.thicknessCategory;
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(style, name, type, thickness, thicknessMm, dashLengthMm, dashGapMm,
                    waveAmplitude, waveLength, system, thicknessCategory);
            result = 31 * result + Arrays.hashCode(dashPattern);
            return result;
        }
    }
}
