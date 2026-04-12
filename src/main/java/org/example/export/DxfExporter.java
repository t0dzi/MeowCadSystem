package org.example.export;

import org.example.model.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Экспортёр чертежа в формат DXF.
 */
public class DxfExporter {

    private record PathGeometry(List<Point> points, boolean closed) {}

    /**
     * Версии формата DXF.
     */
    public enum DxfVersion {
        /** AutoCAD R12 — максимальная совместимость */
        R12("AC1009"),
        R2000("AC1015"),
        R2004("AC1018"),
        /** AutoCAD R2007 */
        R2007("AC1021");

        private final String versionCode;

        DxfVersion(String versionCode) {
            this.versionCode = versionCode;
        }

        public String getVersionCode() {
            return versionCode;
        }
    }

    /**
     * Единицы измерения DXF.
     */
    public enum DxfUnits {
        UNITLESS(0),
        INCHES(1),
        FEET(2),
        MILES(3),
        MILLIMETERS(4),
        CENTIMETERS(5),
        METERS(6),
        KILOMETERS(7);

        private final int code;

        DxfUnits(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }

    private static final int SPLINE_SAMPLES_PER_SEGMENT = 20;
    private static final int ELLIPSE_APPROXIMATION_STEPS = 72;
    private static final double TFLEX_CLOSED_COMPLEX_LINETYPE_PERIODS = 3.0;
    private static final double TFLEX_OPEN_COMPLEX_LINETYPE_PERIODS = 1.0;

    private final DxfVersion version;
    private final DxfUnits units;
    private final boolean explodeComplexLinetypes;

    private PrintWriter writer;

    public DxfExporter() {
        this(DxfVersion.R12, DxfUnits.MILLIMETERS, false);
    }

    public DxfExporter(DxfVersion version, DxfUnits units) {
        this(version, units, false);
    }

    public DxfExporter(DxfVersion version, DxfUnits units, boolean explodeComplexLinetypes) {
        this.version = version;
        this.units = units;
        this.explodeComplexLinetypes = explodeComplexLinetypes;
    }

    public void exportEmpty(File outputFile) throws IOException {
        export(null, outputFile);
    }

    public void export(CadModel model, File outputFile) throws IOException {
        Charset charset = supportsUtf8Strings() ? StandardCharsets.UTF_8 : StandardCharsets.US_ASCII;
        try (PrintWriter pw = new PrintWriter(
                new BufferedWriter(
                        new OutputStreamWriter(
                                new FileOutputStream(outputFile), charset)))) {
            this.writer = pw;

            writeHeaderSection(model);
            writeTablesSection(model);
            writeBlocksSection();
            writeEntitiesSection(model);
            writeEof();
        }
    }

    private void writeHeaderSection(CadModel model) {
        writeSectionStart("HEADER");

        writeVariable("$ACADVER", 1, version.getVersionCode());
        writeVariable("$INSUNITS", 70, String.valueOf(units.getCode()));
        writeVariable("$LUNITS", 70, "2");
        writeVariable("$LUPREC", 70, "4");
        writeVariable("$AUNITS", 70, "0");
        writeVariable("$AUPREC", 70, "2");

        if (supportsExtendedNames()) {
            writeVariable("$EXTNAMES", 290, "1");
        }

        if (model != null && !model.getPrimitives().isEmpty()) {
            double[] extents = calculateExtents(model);
            writeVariable2D("$EXTMIN", extents[0], extents[1]);
            writeVariable2D("$EXTMAX", extents[2], extents[3]);
        } else {
            writeVariable2D("$EXTMIN", 0.0, 0.0);
            writeVariable2D("$EXTMAX", 420.0, 297.0);
        }

        writeSectionEnd();
    }

    private void writeTablesSection(CadModel model) {
        writeSectionStart("TABLES");

        LinkedHashMap<String, double[]> linetypes = collectLinetypes(model);

        writeTableStart("LTYPE", linetypes.size());
        for (var entry : linetypes.entrySet()) {
            String description = getLinetypeDescription(entry.getKey());
            writeLinetype(entry.getKey(), description, entry.getValue());
        }
        writeTableEnd();

        List<Layer> layers = model != null && !model.getLayers().isEmpty()
                ? model.getLayers()
                : List.of(Layer.createDefault());

        writeTableStart("LAYER", layers.size());
        for (Layer layer : layers) {
            writeLayer(layer);
        }
        writeTableEnd();

        writeSectionEnd();
    }

    private LinkedHashMap<String, double[]> collectLinetypes(CadModel model) {
        LinkedHashMap<String, double[]> linetypes = new LinkedHashMap<>();
        linetypes.put("CONTINUOUS", new double[0]);

        if (model == null) {
            return linetypes;
        }

        Set<String> layerLinetypes = new LinkedHashSet<>();
        for (Layer layer : model.getLayers()) {
            String linetypeName = normalizeLinetypeName(layer.getLinetypeName());
            if (linetypeName != null) {
                layerLinetypes.add(linetypeName);
            }
        }

        for (String linetypeName : layerLinetypes) {
            if (!linetypes.containsKey(linetypeName)) {
                linetypes.put(linetypeName, defaultPatternForDxfName(linetypeName));
            }
        }

        for (Primitive primitive : model.getPrimitives()) {
            LineStyle style = primitive.getLineStyle();
            if (style == null) {
                continue;
            }

            String dxfName = mapLinetypeToDxf(style);
            if (dxfName == null || linetypes.containsKey(dxfName)) {
                continue;
            }

            linetypes.put(dxfName, patternForStyle(style, dxfName));
        }

        return linetypes;
    }

    private double[] patternForStyle(LineStyle style, String dxfName) {
        return switch (dxfName) {
            case "CONTINUOUS", "THIN", "WAVES", "ZIGZAG" -> defaultPatternForDxfName(dxfName);
            default -> convertDashPattern(style);
        };
    }

    private void writeBlocksSection() {
        writeSectionStart("BLOCKS");
        writeSectionEnd();
    }

    private void writeEntitiesSection(CadModel model) {
        writeSectionStart("ENTITIES");

        if (model != null) {
            for (Primitive primitive : model.getPrimitives()) {
                if (primitive == null) {
                    continue;
                }

                if (explodeComplexLinetypes && writeGeometryStyledPrimitive(primitive)) {
                    continue;
                }

                switch (primitive.getType()) {
                    case POINT -> writePointEntity((PointPrimitive) primitive);
                    case SEGMENT -> writeLineEntity((Segment) primitive);
                    case CIRCLE -> writeCircleEntity((Circle) primitive);
                    case ARC -> writeArcEntity((Arc) primitive);
                    case ELLIPSE -> writeEllipseEntity((Ellipse) primitive);
                    case POLYGON -> writePolygonEntity((Polygon) primitive);
                    case SPLINE -> writeSplineEntity((Spline) primitive);
                    case RECTANGLE -> writeRectangleEntity((Rectangle) primitive);
                }
            }
        }

        writeSectionEnd();
    }

    private boolean writeGeometryStyledPrimitive(Primitive primitive) {
        LineStyle style = primitive.getLineStyle();
        if (!isGeometryStyled(style)) {
            return false;
        }

        PathGeometry baseGeometry = samplePrimitivePath(primitive);
        if (baseGeometry == null) {
            return false;
        }

        List<Point> styledPoints = applyGeometryStyle(baseGeometry, style, primitive);
        if (styledPoints.size() < 2) {
            return false;
        }

        writePolylineVertices(styledPoints, baseGeometry.closed(), primitive, "CONTINUOUS");
        return true;
    }

    private void writeEntityCommon(Primitive primitive) {
        writeEntityCommon(primitive, null);
    }

    private void writeEntityCommon(Primitive primitive, String explicitLinetype) {
        writeGroup(8, encodeDxfString(primitive.getLayerName()));

        if (primitive.getColorAci() >= 1 && primitive.getColorAci() <= 255) {
            writeGroup(62, String.valueOf(primitive.getColorAci()));
        }

        LineStyle style = primitive.getLineStyle();
        if (style == null) {
            return;
        }

        String dxfLinetype = explicitLinetype != null ? explicitLinetype : mapLinetypeToDxf(style);
        if (dxfLinetype != null) {
            writeGroup(6, dxfLinetype);

            Double linetypeScale = determineEntityLinetypeScale(primitive, style, dxfLinetype);
            if (linetypeScale != null && Math.abs(linetypeScale - 1.0) > 1e-6) {
                writeGroup(48, formatDouble(linetypeScale));
            }
        }

        if (supportsLineweight()) {
            int lineWeight = (int) Math.round(style.getThicknessMm() * 100);
            if (lineWeight > 0) {
                writeGroup(370, String.valueOf(lineWeight));
            }
        }
    }

    private void writePointEntity(PointPrimitive point) {
        writeGroup(0, "POINT");
        writeEntityCommon(point);
        writePoint(point.getPosition(), 10);
    }

    private void writeLineEntity(Segment segment) {
        writeGroup(0, "LINE");
        writeEntityCommon(segment);
        writePoint(segment.getStartPoint(), 10);
        writePoint(segment.getEndPoint(), 11);
    }

    /**
     * Записывает окружность (CIRCLE).
     */
    private void writeCircleEntity(Circle circle) {
        writeGroup(0, "CIRCLE");
        writeEntityCommon(circle);
        writePoint(circle.getCenter(), 10);
        writeGroup(40, formatDouble(circle.getRadius()));
    }

    /**
     * Записывает дугу (ARC).
     */
    private void writeArcEntity(Arc arc) {
        writeGroup(0, "ARC");
        writeEntityCommon(arc);
        writePoint(arc.getCenter(), 10);
        writeGroup(40, formatDouble(arc.getRadius()));
        writeGroup(50, formatDouble(arc.getStartAngleDegrees()));
        writeGroup(51, formatDouble(arc.getEndAngleDegrees()));
    }

    /**
     * Записывает эллипс в поддерживаемом для версии виде.
     */
    private void writeEllipseEntity(Ellipse ellipse) {
        if (supportsEllipseEntity()) {
            writeGroup(0, "ELLIPSE");
            writeEntityCommon(ellipse);
            writePoint(ellipse.getCenter(), 10);

            double majorX = ellipse.getSemiMajorAxis() * Math.cos(ellipse.getRotation());
            double majorY = ellipse.getSemiMajorAxis() * Math.sin(ellipse.getRotation());
            writeGroup(11, formatDouble(majorX));
            writeGroup(21, formatDouble(majorY));
            writeGroup(31, formatDouble(0.0));

            double ratio = ellipse.getSemiMinorAxis() / ellipse.getSemiMajorAxis();
            writeGroup(40, formatDouble(ratio));
            writeGroup(41, formatDouble(0.0));
            writeGroup(42, formatDouble(2 * Math.PI));
            return;
        }

        writePolylineVertices(approximateEllipse(ellipse, ELLIPSE_APPROXIMATION_STEPS), true, ellipse);
    }

    private void writePolygonEntity(Polygon polygon) {
        writePolylineVertices(List.of(polygon.getVertices()), true, polygon);
    }

    private void writeRectangleEntity(Rectangle rect) {
        writePolylineVertices(List.of(rect.getCorners()), true, rect);
    }

    /**
     * Записывает сплайн либо как DXF SPLINE, либо как полилинию-аппроксимацию для R12.
     */
    private void writeSplineEntity(Spline spline) {
        if (!supportsSplineEntity()) {
            writePolylineVertices(spline.getSplinePoints(SPLINE_SAMPLES_PER_SEGMENT), spline.isClosed(), spline);
            return;
        }

        List<Point> controlPoints = spline.getControlPointsList();
        if (controlPoints.size() < 2) {
            return;
        }

        int degree = Math.min(3, controlPoints.size() - 1);
        List<Double> knots = buildClampedUniformKnots(controlPoints.size(), degree);

        writeGroup(0, "SPLINE");
        writeEntityCommon(spline);
        writeGroup(70, String.valueOf(spline.isClosed() ? 9 : 8));
        writeGroup(71, String.valueOf(degree));
        writeGroup(72, String.valueOf(knots.size()));
        writeGroup(73, String.valueOf(controlPoints.size()));
        writeGroup(74, "0");
        writeGroup(42, formatDouble(0.0000001));
        writeGroup(43, formatDouble(0.0000001));
        writeGroup(44, formatDouble(0.0000000001));

        for (double knot : knots) {
            writeGroup(40, formatDouble(knot));
        }

        for (Point point : controlPoints) {
            writePoint(point, 10);
        }
    }

    private boolean isGeometryStyled(LineStyle style) {
        return style != null && (style.getType() == LineType.WAVY || style.getType() == LineType.ZIGZAG);
    }

    private PathGeometry samplePrimitivePath(Primitive primitive) {
        return switch (primitive.getType()) {
            case POINT -> null;
            case SEGMENT -> new PathGeometry(List.of(
                    ((Segment) primitive).getStartPoint(),
                    ((Segment) primitive).getEndPoint()), false);
            case CIRCLE -> new PathGeometry(sampleCircle((Circle) primitive, 180), true);
            case ARC -> new PathGeometry(sampleArc((Arc) primitive, 96), false);
            case ELLIPSE -> new PathGeometry(approximateEllipse((Ellipse) primitive, 180), true);
            case POLYGON -> new PathGeometry(List.of(((Polygon) primitive).getVertices()), true);
            case RECTANGLE -> new PathGeometry(List.of(((Rectangle) primitive).getCorners()), true);
            case SPLINE -> new PathGeometry(
                    ((Spline) primitive).getSplinePoints(SPLINE_SAMPLES_PER_SEGMENT),
                    ((Spline) primitive).isClosed());
        };
    }

    private List<Point> sampleCircle(Circle circle, int steps) {
        List<Point> points = new ArrayList<>(steps);
        Point center = circle.getCenter();
        double radius = circle.getRadius();
        for (int i = 0; i < steps; i++) {
            double angle = (2 * Math.PI * i) / steps;
            points.add(new Point(
                    center.getX() + radius * Math.cos(angle),
                    center.getY() + radius * Math.sin(angle)));
        }
        return points;
    }

    private List<Point> sampleArc(Arc arc, int steps) {
        List<Point> points = new ArrayList<>(steps + 1);
        Point center = arc.getCenter();
        double radius = arc.getRadius();
        double sweep = arc.getSweepAngle();
        int sampleCount = Math.max(8, (int) Math.ceil(steps * sweep / (2 * Math.PI)));
        for (int i = 0; i <= sampleCount; i++) {
            double t = (double) i / sampleCount;
            double angle = arc.getStartAngle() + sweep * t;
            points.add(new Point(
                    center.getX() + radius * Math.cos(angle),
                    center.getY() + radius * Math.sin(angle)));
        }
        return points;
    }

    private List<Point> applyGeometryStyle(PathGeometry geometry, LineStyle style, Primitive primitive) {
        return switch (style.getType()) {
            case WAVY -> createWavyPath(geometry, style);
            case ZIGZAG -> createZigzagPath(geometry, style, primitive);
            default -> geometry.points();
        };
    }

    private List<Point> createWavyPath(PathGeometry geometry, LineStyle style) {
        double totalLength = getPathLength(geometry.points(), geometry.closed());
        if (totalLength < 1e-6) {
            return geometry.points();
        }

        double amplitude = Math.max(0.1, style.getWaveAmplitude());
        double waveLength = Math.max(0.5, style.getWaveLength());
        int periods = geometry.closed()
                ? Math.max(3, (int) Math.round(totalLength / waveLength))
                : Math.max(1, (int) Math.round(totalLength / waveLength));
        int samples = Math.max(geometry.points().size() * 8, periods * 24);

        return createOffsetPath(geometry, samples, s -> amplitude * Math.sin(2 * Math.PI * periods * s / totalLength));
    }

    private List<Point> createZigzagPath(PathGeometry geometry, LineStyle style, Primitive primitive) {
        double totalLength = getPathLength(geometry.points(), geometry.closed());
        if (totalLength < 1e-6) {
            return geometry.points();
        }

        double[] params = style.getDashPattern();
        double height = params != null && params.length >= 1 ? Math.max(0.1, params[0]) : 8.0;
        double width = params != null && params.length >= 2 ? Math.max(0.5, params[1]) : 10.0;
        int preferredCount = params != null && params.length >= 3 ? Math.max(1, (int) Math.round(params[2])) : 0;
        int autoCount = Math.max(1, (int) Math.round(totalLength / Math.max(2.0 * width, 1.0)));
        int periods = primitive.getType() == PrimitiveType.SEGMENT && preferredCount > 0
                ? preferredCount
                : Math.max(Math.max(preferredCount, 1), autoCount);
        int samples = Math.max(geometry.points().size() * 8, periods * 8);

        return createOffsetPath(geometry, samples, s -> height * triangleWave(periods * s / totalLength));
    }

    private List<Point> createOffsetPath(PathGeometry geometry, int samples, java.util.function.DoubleUnaryOperator offsetFunction) {
        double[] cumulativeLengths = buildCumulativeLengths(geometry.points(), geometry.closed());
        double totalLength = cumulativeLengths[cumulativeLengths.length - 1];
        List<Point> result = new ArrayList<>(geometry.closed() ? samples : samples + 1);
        int pointCount = geometry.closed() ? samples : samples + 1;

        for (int i = 0; i < pointCount; i++) {
            double distance = totalLength * i / samples;
            double[] pointAndNormal = pointOnPath(geometry.points(), geometry.closed(), cumulativeLengths, distance);
            double offset = offsetFunction.applyAsDouble(distance);
            result.add(new Point(
                    pointAndNormal[0] + pointAndNormal[2] * offset,
                    pointAndNormal[1] + pointAndNormal[3] * offset));
        }

        return result;
    }

    private double[] buildCumulativeLengths(List<Point> points, boolean closed) {
        int segmentCount = closed ? points.size() : points.size() - 1;
        double[] lengths = new double[segmentCount + 1];
        for (int i = 0; i < segmentCount; i++) {
            Point start = points.get(i);
            Point end = points.get((i + 1) % points.size());
            lengths[i + 1] = lengths[i] + distance(start, end);
        }
        return lengths;
    }

    private double[] pointOnPath(List<Point> points, boolean closed, double[] cumulativeLengths, double targetLength) {
        double totalLength = cumulativeLengths[cumulativeLengths.length - 1];
        if (totalLength < 1e-9) {
            Point p = points.get(0);
            return new double[] { p.getX(), p.getY(), 0.0, 0.0 };
        }

        if (closed) {
            targetLength = targetLength % totalLength;
            if (targetLength < 0) {
                targetLength += totalLength;
            }
        } else {
            targetLength = Math.max(0.0, Math.min(targetLength, totalLength));
        }

        int segmentCount = cumulativeLengths.length - 1;
        for (int i = 0; i < segmentCount; i++) {
            if (targetLength <= cumulativeLengths[i + 1] || i == segmentCount - 1) {
                Point start = points.get(i);
                Point end = points.get((i + 1) % points.size());
                double segmentLength = cumulativeLengths[i + 1] - cumulativeLengths[i];
                if (segmentLength < 1e-9) {
                    return new double[] { start.getX(), start.getY(), 0.0, 0.0 };
                }

                double localT = (targetLength - cumulativeLengths[i]) / segmentLength;
                localT = Math.max(0.0, Math.min(localT, 1.0));
                double dx = end.getX() - start.getX();
                double dy = end.getY() - start.getY();
                double x = start.getX() + dx * localT;
                double y = start.getY() + dy * localT;
                double nx = -dy / segmentLength;
                double ny = dx / segmentLength;
                return new double[] { x, y, nx, ny };
            }
        }

        Point p = points.get(points.size() - 1);
        return new double[] { p.getX(), p.getY(), 0.0, 0.0 };
    }

    private double getPathLength(List<Point> points, boolean closed) {
        double[] cumulativeLengths = buildCumulativeLengths(points, closed);
        return cumulativeLengths[cumulativeLengths.length - 1];
    }

    private double distance(Point a, Point b) {
        double dx = b.getX() - a.getX();
        double dy = b.getY() - a.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private double triangleWave(double phase) {
        double normalized = phase - Math.floor(phase);
        if (normalized < 0.25) {
            return normalized * 4.0;
        }
        if (normalized < 0.75) {
            return 2.0 - normalized * 4.0;
        }
        return normalized * 4.0 - 4.0;
    }

    private void writePolylineVertices(List<Point> vertices, boolean closed, Primitive primitive) {
        writePolylineVertices(vertices, closed, primitive, null);
    }

    private void writePolylineVertices(List<Point> vertices, boolean closed, Primitive primitive, String explicitLinetype) {
        if (vertices.size() < 2) {
            return;
        }

        if (usesLightweightPolyline()) {
            writeGroup(0, "LWPOLYLINE");
            writeEntityCommon(primitive, explicitLinetype);
            writeGroup(90, String.valueOf(vertices.size()));
            writeGroup(70, closed ? "1" : "0");
            for (Point point : vertices) {
                writeGroup(10, formatDouble(point.getX()));
                writeGroup(20, formatDouble(point.getY()));
            }
            return;
        }

        writeGroup(0, "POLYLINE");
        writeEntityCommon(primitive, explicitLinetype);
        writeGroup(66, "1");
        writeGroup(70, closed ? "1" : "0");
        writeGroup(10, formatDouble(0.0));
        writeGroup(20, formatDouble(0.0));
        writeGroup(30, formatDouble(0.0));

        for (Point point : vertices) {
            writeGroup(0, "VERTEX");
            writeGroup(8, encodeDxfString(primitive.getLayerName()));
            if (primitive.getColorAci() >= 1 && primitive.getColorAci() <= 255) {
                writeGroup(62, String.valueOf(primitive.getColorAci()));
            }
            writePoint(point, 10);
        }

        writeGroup(0, "SEQEND");
    }

    private List<Point> approximateEllipse(Ellipse ellipse, int steps) {
        List<Point> points = new ArrayList<>(steps);
        for (int i = 0; i < steps; i++) {
            double angle = (2 * Math.PI * i) / steps;
            points.add(ellipse.getPointAtAngle(angle));
        }
        return points;
    }

    private List<Double> buildClampedUniformKnots(int controlPointCount, int degree) {
        int knotCount = controlPointCount + degree + 1;
        int lastInteriorValue = Math.max(0, controlPointCount - degree);
        List<Double> knots = new ArrayList<>(knotCount);

        for (int i = 0; i < knotCount; i++) {
            if (i <= degree) {
                knots.add(0.0);
            } else if (i >= controlPointCount) {
                knots.add((double) lastInteriorValue);
            } else {
                knots.add((double) (i - degree));
            }
        }

        return knots;
    }

    private Double determineEntityLinetypeScale(Primitive primitive, LineStyle style, String dxfLinetype) {
        if (primitive == null || style == null || dxfLinetype == null) {
            return null;
        }

        PathGeometry geometry = samplePrimitivePath(primitive);
        if (geometry == null) {
            return null;
        }

        double totalLength = getPathLength(geometry.points(), geometry.closed());
        if (totalLength < 1e-6) {
            return null;
        }

        double referencePeriods = geometry.closed()
                ? TFLEX_CLOSED_COMPLEX_LINETYPE_PERIODS
                : TFLEX_OPEN_COMPLEX_LINETYPE_PERIODS;

        return switch (dxfLinetype) {
            case "WAVES" -> {
                double waveLength = Math.max(0.5, style.getWaveLength());
                double desiredPeriods = geometry.closed()
                        ? Math.max(3.0, Math.round(totalLength / waveLength))
                        : Math.max(1.0, Math.round(totalLength / waveLength));
                yield clampLinetypeScale(referencePeriods / desiredPeriods);
            }
            case "ZIGZAG" -> {
                double[] params = style.getDashPattern();
                int preferredCount = params != null && params.length >= 3
                        ? Math.max(1, (int) Math.round(params[2]))
                        : 0;
                double width = params != null && params.length >= 2
                        ? Math.max(0.5, params[1])
                        : 10.0;
                double autoPeriods = Math.max(1.0, Math.round(totalLength / Math.max(2.0 * width, 1.0)));
                double desiredPeriods = primitive.getType() == PrimitiveType.SEGMENT && preferredCount > 0
                        ? preferredCount
                        : Math.max(Math.max(preferredCount, 1), autoPeriods);
                yield clampLinetypeScale(referencePeriods / desiredPeriods);
            }
            default -> null;
        };
    }

    private double clampLinetypeScale(double value) {
        return Math.max(0.02, Math.min(10.0, value));
    }

    /**
     * Маппинг типа линии из модели в DXF имя типа линии.
     */
    private String mapLinetypeToDxf(LineStyle style) {
        if (style == null || style.getType() == null) {
            return null;
        }
        return switch (style.getType()) {
            case SOLID -> "CONTINUOUS";
            case WAVY -> "WAVES";
            case ZIGZAG -> "ZIGZAG";
            case DASHED -> "HIDDEN";
            case DASH_DOT -> style.getThicknessCategory() == LineStyle.ThicknessCategory.THICK ? "CENTER2" : "CENTER";
            case DASH_DOT_DOT -> "PHANTOM";
        };
    }

    private String normalizeLinetypeName(String linetypeName) {
        if (linetypeName == null || linetypeName.isBlank()) {
            return "CONTINUOUS";
        }
        return linetypeName.toUpperCase(Locale.ROOT);
    }


    /**
     * Записывает группу DXF (код + значение).
     */
    private void writeGroup(int groupCode, String value) {
        writer.printf(Locale.ROOT, "%3d%n", groupCode);
        writer.println(value);
    }

    private void writePoint(Point point, int baseGroupCode) {
        writeGroup(baseGroupCode, formatDouble(point.getX()));
        writeGroup(baseGroupCode + 10, formatDouble(point.getY()));
        writeGroup(baseGroupCode + 20, formatDouble(0.0));
    }

    private void writeSectionStart(String sectionName) {
        writeGroup(0, "SECTION");
        writeGroup(2, sectionName);
    }

    private void writeSectionEnd() {
        writeGroup(0, "ENDSEC");
    }

    private void writeTableStart(String tableName, int maxEntries) {
        writeGroup(0, "TABLE");
        writeGroup(2, tableName);
        writeGroup(70, String.valueOf(maxEntries));
    }

    private void writeTableEnd() {
        writeGroup(0, "ENDTAB");
    }

    private void writeVariable(String name, int groupCode, String value) {
        writeGroup(9, name);
        writeGroup(groupCode, value);
    }

    /**
     * Записывает системную переменную HEADER с 2D координатами.
     */
    private void writeVariable2D(String name, double x, double y) {
        writeGroup(9, name);
        writeGroup(10, formatDouble(x));
        writeGroup(20, formatDouble(y));
    }

    private void writeLinetype(String name, String description, double[] pattern) {
        writeGroup(0, "LTYPE");
        writeGroup(2, name);
        writeGroup(70, "0");
        writeGroup(3, encodeDxfString(description));
        writeGroup(72, "65");
        writeGroup(73, String.valueOf(pattern.length));

        double totalLength = 0;
        for (double d : pattern) {
            totalLength += Math.abs(d);
        }
        writeGroup(40, formatDouble(totalLength));
        for (double d : pattern) {
            writeGroup(49, formatDouble(d));
            writeGroup(74, "0");
        }
    }

    /**
     * Конвертирует dash-паттерн из LineStyle приложения в формат DXF.
     */
    private double[] convertDashPattern(LineStyle style) {
        double[] appPattern = style.getDashPattern();
        if (appPattern == null || appPattern.length == 0) {
            LineType lt = style.getType();
            double dash = style.getDashLengthMm();
            double gap = style.getDashGapMm();
            return switch (lt) {
                case DASHED -> new double[] { dash, -gap };
                case DASH_DOT -> new double[] { dash, -gap, 0.0, -gap };
                case DASH_DOT_DOT -> new double[] { dash, -gap, 0.0, -gap, 0.0, -gap };
                default -> new double[0];
            };
        }

        double[] dxf = new double[appPattern.length];
        for (int i = 0; i < appPattern.length; i++) {
            double val = appPattern[i];
            if (i % 2 == 0) {
                dxf[i] = (val < 1.5) ? 0.0 : val;
            } else {
                dxf[i] = -Math.abs(val);
            }
        }
        return dxf;
    }

    private double[] defaultPatternForDxfName(String dxfName) {
        return switch (dxfName) {
            case "WAVES", "ZIGZAG", "THIN" -> new double[0];
            case "DASHED", "HIDDEN", "HIDDEN2" -> new double[] { 6.0, -2.0 };
            case "DASHDOT", "CENTER", "CENTER2" -> new double[] { 6.0, -2.0, 0.0, -2.0 };
            case "DASHDOT2", "DIVIDE", "DIVIDE2", "PHANTOM" -> new double[] { 6.0, -2.0, 0.0, -2.0, 0.0, -2.0 };
            default -> new double[0];
        };
    }

    /**
     * Возвращает описание типа линии для DXF.
     */
    private String getLinetypeDescription(String dxfName) {
        return switch (dxfName) {
            case "CONTINUOUS" -> "Solid line";
            case "THIN" -> "Thin line";
            case "WAVES" -> "T-FLEX waves";
            case "ZIGZAG" -> "T-FLEX zigzag";
            case "DASHED", "HIDDEN" -> "__ __ __ __";
            case "DASHDOT", "CENTER", "CENTER2" -> "__.__.__.__";
            case "DASHDOT2", "PHANTOM" -> "__..__..__..__";
            default -> dxfName;
        };
    }

    private void writeLayer(Layer layer) {
        int flags = 0;
        if (layer.isLocked()) {
            flags |= 4;
        }

        int color = layer.getColorIndex();
        if (!layer.isVisible()) {
            color = -Math.abs(color);
        }

        writeGroup(0, "LAYER");
        writeGroup(2, encodeDxfString(layer.getName()));
        writeGroup(70, String.valueOf(flags));
        writeGroup(62, String.valueOf(color));
        writeGroup(6, normalizeLinetypeName(layer.getLinetypeName()));
    }

    private void writeEof() {
        writeGroup(0, "EOF");
    }

    /**
     * Форматирует число с плавающей точкой для DXF.
     */
    private String formatDouble(double value) {
        if (Math.abs(value) < 1e-12) {
            value = 0.0;
        }
        if (value == Math.rint(value)) {
            return String.format(Locale.ROOT, "%.1f", value);
        }
        return String.format(Locale.ROOT, "%.6f", value);
    }

    /**
     * Кодирует строку для DXF.
     * Для R2007+ можно писать UTF-8 напрямую, для старых версий не-ASCII символы
     * сохраняются как \U+XXXX.
     */
    private String encodeDxfString(String value) {
        if (value == null) {
            return "";
        }
        if (supportsUtf8Strings()) {
            return value;
        }

        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch <= 0x7F) {
                builder.append(ch);
            } else {
                builder.append(String.format(Locale.ROOT, "\\U+%04X", (int) ch));
            }
        }
        return builder.toString();
    }

    /**
     * Вычисляет границы чертежа (minX, minY, maxX, maxY).
     */
    private double[] calculateExtents(CadModel model) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        for (Primitive primitive : model.getPrimitives()) {
            if (writeGeometryBounds(primitive)) {
                PathGeometry geometry = samplePrimitivePath(primitive);
                List<Point> styledPoints = applyGeometryStyle(geometry, primitive.getLineStyle(), primitive);
                for (Point point : styledPoints) {
                    minX = Math.min(minX, point.getX());
                    minY = Math.min(minY, point.getY());
                    maxX = Math.max(maxX, point.getX());
                    maxY = Math.max(maxY, point.getY());
                }
            } else {
                double[] bbox = primitive.getBoundingBox();
                minX = Math.min(minX, bbox[0]);
                minY = Math.min(minY, bbox[1]);
                maxX = Math.max(maxX, bbox[2]);
                maxY = Math.max(maxY, bbox[3]);
            }
        }

        return new double[] { minX, minY, maxX, maxY };
    }

    private boolean writeGeometryBounds(Primitive primitive) {
        return isGeometryStyled(primitive.getLineStyle()) && samplePrimitivePath(primitive) != null;
    }

    private boolean supportsUtf8Strings() {
        return version.ordinal() >= DxfVersion.R2007.ordinal();
    }

    private boolean supportsExtendedNames() {
        return version.ordinal() >= DxfVersion.R2000.ordinal();
    }

    private boolean supportsLineweight() {
        return version.ordinal() >= DxfVersion.R2000.ordinal();
    }

    private boolean supportsEllipseEntity() {
        return version.ordinal() >= DxfVersion.R2000.ordinal();
    }

    private boolean supportsSplineEntity() {
        return version.ordinal() >= DxfVersion.R2000.ordinal();
    }

    private boolean usesLightweightPolyline() {
        return version.ordinal() >= DxfVersion.R2000.ordinal();
    }
}

