package org.example.export;

import org.example.model.*;
import org.example.model.Polygon;
import org.example.model.Rectangle;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Импортёр DXF-файлов (Drawing Exchange Format).
 *
 * Поддерживает секции:
 *  - HEADER  — версия, единицы, границы чертежа
 *  - TABLES  — слои (LAYER) и типы линий (LTYPE)
 *  - BLOCKS  — примитивы внутри блоков (кроме $MODEL_SPACE)
 *  - ENTITIES — LINE, CIRCLE, ARC, ELLIPSE, LWPOLYLINE, POLYLINE+VERTEX, SPLINE, POINT
 *
 * Формат: ASCII DXF (R12–R2007).
 */
public class DxfImporter {


    /** Полный результат импорта DXF-файла. */
    public static class ImportResult {
        private final List<Primitive>       primitives    = new ArrayList<>();
        private final List<Layer>           layers        = new ArrayList<>();

        private String headerVersion = "";
        private int    headerUnits   = 0;

        private double extMinX = 0, extMinY = 0;
        private double extMaxX = 0, extMaxY = 0;
        private final Map<String, double[]> ltypePatterns = new LinkedHashMap<>();
        private final Map<String, Integer>  layerLineweights = new LinkedHashMap<>();

        private int skippedEntities = 0;

        public List<Primitive>         getPrimitives()      { return primitives; }
        public List<Layer>             getLayers()          { return layers; }
        public String                  getHeaderVersion()   { return headerVersion; }
        public int                     getHeaderUnits()     { return headerUnits; }
        public Map<String, double[]>   getLtypePatterns()   { return ltypePatterns; }
        public Map<String, Integer>    getLayerLineweights(){ return layerLineweights; }
        public int                     getSkippedEntities() { return skippedEntities; }
        public int                     getTotalImported()   { return primitives.size(); }
        public double                  getExtMinX()         { return extMinX; }
        public double                  getExtMinY()         { return extMinY; }
        public double                  getExtMaxX()         { return extMaxX; }
        public double                  getExtMaxY()         { return extMaxY; }
    }


    private final StyleManager styleManager;

    public DxfImporter(StyleManager styleManager) {
        this.styleManager = styleManager;
    }

    public ImportResult importFile(File file) throws IOException {
        ImportResult result = new ImportResult();

        List<String[]> pairs = readPairs(file);

        parseHeader(pairs, result);
        parseTables(pairs, result);
        parseBlocks(pairs, result);
        parseEntities(pairs, result);

        return result;
    }

    private List<String[]> readPairs(File file) throws IOException {
        List<String[]> pairs = readPairsWithCharset(file, StandardCharsets.UTF_8);
        if (!hasSection(pairs)) {
            pairs = readPairsWithCharset(file, Charset.forName("windows-1252"));
        }
        return pairs;
    }

    private List<String[]> readPairsWithCharset(File file, Charset cs) throws IOException {
        List<String[]> pairs = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), cs))) {
            String codeLine, valueLine;
            while ((codeLine = reader.readLine()) != null) {
                valueLine = reader.readLine();
                if (valueLine == null) break;
                pairs.add(new String[]{ codeLine.trim(), decodeDxfString(valueLine.trim()) });
            }
        }
        return pairs;
    }

    private String decodeDxfString(String value) {
        if (value == null || value.indexOf("\\U+") < 0) {
            return value;
        }

        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            if (i + 5 < value.length() && value.charAt(i) == '\\'
                    && value.charAt(i + 1) == 'U' && value.charAt(i + 2) == '+') {
                String hex = value.substring(i + 3, i + 7);
                try {
                    result.append((char) Integer.parseInt(hex, 16));
                    i += 6;
                    continue;
                } catch (NumberFormatException ignored) {
                }
            }
            result.append(value.charAt(i));
        }
        return result.toString();
    }

    private boolean hasSection(List<String[]> pairs) {
        for (String[] pair : pairs) {
            if ("0".equals(pair[0]) && "SECTION".equals(pair[1])) return true;
        }
        return false;
    }

    private void parseHeader(List<String[]> pairs, ImportResult result) {
        int i = findSection(pairs, "HEADER", 0);
        if (i < 0) return;

        boolean readingExtMin = false, readingExtMax = false;

        while (i < pairs.size()) {
            String code  = pairs.get(i)[0];
            String value = pairs.get(i)[1];

            if ("0".equals(code) && "ENDSEC".equals(value)) break;

            switch (code) {
                case "9" -> {
                    readingExtMin = "$EXTMIN".equals(value);
                    readingExtMax = "$EXTMAX".equals(value);
                    if ("$ACADVER".equals(value) && i + 1 < pairs.size())
                        result.headerVersion = pairs.get(i + 1)[1];
                    if ("$INSUNITS".equals(value) && i + 1 < pairs.size())
                        result.headerUnits = parseIntSafe(pairs.get(i + 1)[1], 0);
                }
                case "10" -> {
                    if      (readingExtMin) result.extMinX = parseDoubleSafe(value);
                    else if (readingExtMax) result.extMaxX = parseDoubleSafe(value);
                }
                case "20" -> {
                    if      (readingExtMin) result.extMinY = parseDoubleSafe(value);
                    else if (readingExtMax) result.extMaxY = parseDoubleSafe(value);
                }
            }
            i++;
        }
    }

    private void parseTables(List<String[]> pairs, ImportResult result) {
        int i = findSection(pairs, "TABLES", 0);
        if (i < 0) return;

        while (i < pairs.size()) {
            String code  = pairs.get(i)[0];
            String value = pairs.get(i)[1];

            if ("0".equals(code) && "ENDSEC".equals(value)) break;

            if ("0".equals(code)) {
                if ("LAYER".equals(value)) {
                    i = parseLayerEntry(pairs, i + 1, result);
                    continue;
                }
                if ("LTYPE".equals(value)) {
                    i = parseLtypeEntry(pairs, i + 1, result);
                    continue;
                }
            }
            i++;
        }
    }

    private int parseLayerEntry(List<String[]> pairs, int i, ImportResult result) {
        String name        = "0";
        int    color       = 7;
        String linetype    = "CONTINUOUS";
        int    lineweight  = -1;

        while (i < pairs.size()) {
            String code  = pairs.get(i)[0];
            String value = pairs.get(i)[1];
            if ("0".equals(code)) break;
            switch (code) {
                case "2"   -> name       = normalizeLayerName(value);
                case "62"  -> color      = parseIntSafe(value, 7);
                case "6"   -> linetype   = value;
                case "370" -> lineweight = parseIntSafe(value, -1);
            }
            i++;
        }

        Layer layer = new Layer(name, Math.abs(color), linetype);
        if (color < 0) layer.setVisible(false);
        upsertLayer(result, layer);
        result.layerLineweights.put(name, lineweight);
        return i;
    }

    private int parseLtypeEntry(List<String[]> pairs, int i, ImportResult result) {
        String       name    = null;
        List<Double> pattern = new ArrayList<>();

        while (i < pairs.size()) {
            String code  = pairs.get(i)[0];
            String value = pairs.get(i)[1];
            if ("0".equals(code)) break;
            switch (code) {
                case "2"  -> name = value;
                case "49" -> pattern.add(parseDoubleSafe(value));
            }
            i++;
        }

        if (name != null && !name.isEmpty()) {
            double[] pat = new double[pattern.size()];
            for (int k = 0; k < pattern.size(); k++) pat[k] = pattern.get(k);
            result.ltypePatterns.put(name.toUpperCase(), pat);
        }
        return i;
    }

    /**
     * Читает секцию BLOCKS — примитивы внутри блоков-определений.
     * Пропускает специальные блоки *Model_Space и *Paper_Space,
     * их содержимое дублируется в секции ENTITIES.
     */
    private void parseBlocks(List<String[]> pairs, ImportResult result) {
        int i = findSection(pairs, "BLOCKS", 0);
        if (i < 0) return;

        String currentBlockName = "";

        while (i < pairs.size()) {
            String code  = pairs.get(i)[0];
            String value = pairs.get(i)[1];

            if ("0".equals(code) && "ENDSEC".equals(value)) break;

            if ("0".equals(code) && "BLOCK".equals(value)) {
                // Читаем имя блока (группа 2)
                int j = i + 1;
                currentBlockName = "";
                while (j < pairs.size() && !"0".equals(pairs.get(j)[0])) {
                    if ("2".equals(pairs.get(j)[0])) {
                        currentBlockName = pairs.get(j)[1];
                    }
                    j++;
                }
                i = j;
                continue;
            }

            if ("0".equals(code) && "ENDBLK".equals(value)) {
                currentBlockName = "";
                i++;
                continue;
            }

            boolean isSystemBlock = currentBlockName.startsWith("*Model_Space")
                                 || currentBlockName.startsWith("*model_space")
                                 || currentBlockName.startsWith("*MODEL_SPACE")
                                 || currentBlockName.startsWith("*Paper_Space")
                                 || currentBlockName.startsWith("*paper_space")
                                 || currentBlockName.startsWith("*PAPER_SPACE");

            if (!isSystemBlock && "0".equals(code)) {
                i = dispatchEntity(value, pairs, i + 1, result);
                continue;
            }

            i++;
        }
    }

    private void parseEntities(List<String[]> pairs, ImportResult result) {
        int i = findSection(pairs, "ENTITIES", 0);
        if (i < 0) return;

        while (i < pairs.size()) {
            String code  = pairs.get(i)[0];
            String value = pairs.get(i)[1];

            if ("0".equals(code) && "ENDSEC".equals(value)) break;

            if ("0".equals(code)) {
                i = dispatchEntity(value, pairs, i + 1, result);
                continue;
            }
            i++;
        }
    }

    /**
     * Маршрутизирует разбор по типу сущности.
     * Возвращает индекс, с которого следует продолжить разбор.
     */
    private int dispatchEntity(String entityType, List<String[]> pairs, int i, ImportResult result) {
        switch (entityType) {
            case "LINE"       -> { return parseLine(pairs, i, result); }
            case "CIRCLE"     -> { return parseCircle(pairs, i, result); }
            case "ARC"        -> { return parseArc(pairs, i, result); }
            case "ELLIPSE"    -> { return parseEllipse(pairs, i, result); }
            case "LWPOLYLINE" -> { return parseLWPolyline(pairs, i, result); }
            case "POLYLINE"   -> { return parsePOLYLINE(pairs, i, result); }
            case "SPLINE"     -> { return parseSpline(pairs, i, result); }
            case "POINT"      -> { return parsePoint(pairs, i, result); }
            default -> {
                result.skippedEntities++;
                return findEntityEnd(pairs, i);
            }
        }
    }


    private static class EntityCommon {
        String layerName    = "0";
        String linetypeName = null;
        /** ACI: 1–255 или −1 (не задан). */
        int    colorIndex   = -1;
        /** TrueColor: 0xRRGGBB или −1. */
        int    trueColor    = -1;
        /** Lineweight: −1 если не задано / BYLAYER. */
        int    lineWeight   = -1;
    }

    private EntityCommon readCommon(List<String[]> pairs, int start, int end) {
        EntityCommon common = new EntityCommon();
        for (int i = start; i < end; i++) {
            switch (pairs.get(i)[0]) {
                case "8"   -> common.layerName    = normalizeLayerName(pairs.get(i)[1]);
                case "6"   -> common.linetypeName = pairs.get(i)[1];
                case "62"  -> common.colorIndex   = parseIntSafe(pairs.get(i)[1], -1);
                case "420" -> common.trueColor    = parseIntSafe(pairs.get(i)[1], -1);
                case "370" -> common.lineWeight   = parseIntSafe(pairs.get(i)[1], -1);
            }
        }
        return common;
    }

    private int findEntityEnd(List<String[]> pairs, int start) {
        int i = start;
        while (i < pairs.size() && !"0".equals(pairs.get(i)[0])) i++;
        return i;
    }

    private LineStyle resolveStyle(EntityCommon common, ImportResult result) {
        String effectiveLinetype = resolveEffectiveLinetype(common, result);
        String normalizedLinetype = normalizeLinetypeName(effectiveLinetype);

        if (normalizedLinetype != null) {
            LineStyle byLinetype = switch (normalizedLinetype) {
                case "WAVES" -> findStyle(LineType.WAVY, null);
                case "ZIGZAG" -> findStyle(LineType.ZIGZAG, null);
                case "THIN" -> findStyle(LineType.SOLID, LineStyle.ThicknessCategory.THIN);
                case "HIDDEN", "DASHED", "HIDDEN2" -> findStyle(LineType.DASHED, null);
                case "CENTER2" -> findStyle(LineType.DASH_DOT, LineStyle.ThicknessCategory.THICK);
                case "CENTER", "DASHDOT" -> findStyle(LineType.DASH_DOT, LineStyle.ThicknessCategory.THIN);
                case "PHANTOM", "DASHDOT2", "DIVIDE", "DIVIDE2" -> findStyle(LineType.DASH_DOT_DOT, null);
                default -> null;
            };
            if (byLinetype != null) {
                return byLinetype;
            }
        }

        int effectiveLineweight = resolveEffectiveLineweight(common, result);
        if (effectiveLineweight >= 0 && effectiveLineweight < 60) {
            LineStyle thinSolid = findStyle(LineType.SOLID, LineStyle.ThicknessCategory.THIN);
            if (thinSolid != null) {
                return thinSolid;
            }
        }

        return styleManager.getDefaultStyle();
    }

    private void applyCommon(Primitive p, EntityCommon common, ImportResult result) {
        p.setLayerName(common.layerName);
        p.setLineStyle(resolveStyle(common, result));

        int effectiveColorIndex = resolveEffectiveColorIndex(common, result);
        if (effectiveColorIndex >= 1 && effectiveColorIndex <= 255) {
            p.setColorAci(effectiveColorIndex);
        }
    }

    private int parseLine(List<String[]> pairs, int i, ImportResult result) {
        int end = findEntityEnd(pairs, i);
        EntityCommon common = readCommon(pairs, i, end);

        double x1 = 0, y1 = 0, x2 = 0, y2 = 0;
        for (int j = i; j < end; j++) {
            switch (pairs.get(j)[0]) {
                case "10" -> x1 = parseDoubleSafe(pairs.get(j)[1]);
                case "20" -> y1 = parseDoubleSafe(pairs.get(j)[1]);
                case "11" -> x2 = parseDoubleSafe(pairs.get(j)[1]);
                case "21" -> y2 = parseDoubleSafe(pairs.get(j)[1]);
            }
        }

        Segment seg = new Segment(new Point(x1, y1), new Point(x2, y2),
                Segment.CreationMode.CARTESIAN, styleManager.getDefaultStyle());
        applyCommon(seg, common, result);
        result.primitives.add(seg);
        return end;
    }

    private int parseCircle(List<String[]> pairs, int i, ImportResult result) {
        int end = findEntityEnd(pairs, i);
        EntityCommon common = readCommon(pairs, i, end);

        double cx = 0, cy = 0, radius = 0;
        for (int j = i; j < end; j++) {
            switch (pairs.get(j)[0]) {
                case "10" -> cx     = parseDoubleSafe(pairs.get(j)[1]);
                case "20" -> cy     = parseDoubleSafe(pairs.get(j)[1]);
                case "40" -> radius = parseDoubleSafe(pairs.get(j)[1]);
            }
        }

        if (radius <= 0) { result.skippedEntities++; return end; }

        Circle circle = new Circle(new Point(cx, cy), radius, styleManager.getDefaultStyle());
        applyCommon(circle, common, result);
        result.primitives.add(circle);
        return end;
    }

    /** ARC → Arc (углы из DXF в градусах → радианы) */
    private int parseArc(List<String[]> pairs, int i, ImportResult result) {
        int end = findEntityEnd(pairs, i);
        EntityCommon common = readCommon(pairs, i, end);

        double cx = 0, cy = 0, radius = 0;
        double startDeg = 0, endDeg = 360;
        for (int j = i; j < end; j++) {
            switch (pairs.get(j)[0]) {
                case "10" -> cx       = parseDoubleSafe(pairs.get(j)[1]);
                case "20" -> cy       = parseDoubleSafe(pairs.get(j)[1]);
                case "40" -> radius   = parseDoubleSafe(pairs.get(j)[1]);
                case "50" -> startDeg = parseDoubleSafe(pairs.get(j)[1]);
                case "51" -> endDeg   = parseDoubleSafe(pairs.get(j)[1]);
            }
        }

        if (radius <= 0) { result.skippedEntities++; return end; }

        Arc arc = new Arc(new Point(cx, cy), radius,
                Math.toRadians(startDeg), Math.toRadians(endDeg),
                styleManager.getDefaultStyle());
        applyCommon(arc, common, result);
        result.primitives.add(arc);
        return end;
    }

    /**
     * ELLIPSE → Ellipse.
     * DXF хранит вектор большой оси (11/21) и отношение малой к большой (40).
     * WCS-координаты без дополнительных преобразований.
     */
    private int parseEllipse(List<String[]> pairs, int i, ImportResult result) {
        int end = findEntityEnd(pairs, i);
        EntityCommon common = readCommon(pairs, i, end);

        double cx = 0, cy = 0;
        double majorX = 1, majorY = 0;
        double ratio  = 1.0;
        for (int j = i; j < end; j++) {
            switch (pairs.get(j)[0]) {
                case "10" -> cx     = parseDoubleSafe(pairs.get(j)[1]);
                case "20" -> cy     = parseDoubleSafe(pairs.get(j)[1]);
                case "11" -> majorX = parseDoubleSafe(pairs.get(j)[1]);
                case "21" -> majorY = parseDoubleSafe(pairs.get(j)[1]);
                case "40" -> ratio  = parseDoubleSafe(pairs.get(j)[1]);
            }
        }

        double semiMajor = Math.sqrt(majorX * majorX + majorY * majorY);
        double semiMinor = semiMajor * Math.max(0.001, ratio);
        double rotation  = Math.atan2(majorY, majorX);

        if (semiMajor <= 0) { result.skippedEntities++; return end; }

        Ellipse ellipse = new Ellipse(new Point(cx, cy), semiMajor, semiMinor, rotation,
                styleManager.getDefaultStyle());
        applyCommon(ellipse, common, result);
        result.primitives.add(ellipse);
        return end;
    }

    /**
     * LWPOLYLINE → Rectangle | Polygon | набор Segment.
     * Флаг 70, бит 0 = замкнутый.
     */
    private int parseLWPolyline(List<String[]> pairs, int i, ImportResult result) {
        int end = findEntityEnd(pairs, i);
        EntityCommon common = readCommon(pairs, i, end);

        boolean closed = false;
        List<Point> vertices = new ArrayList<>();

        for (int j = i; j < end; j++) {
            if ("70".equals(pairs.get(j)[0]))
                closed = (parseIntSafe(pairs.get(j)[1], 0) & 1) == 1;
        }

        double currentX = 0;
        for (int j = i; j < end; j++) {
            if ("10".equals(pairs.get(j)[0])) {
                currentX = parseDoubleSafe(pairs.get(j)[1]);
            } else if ("20".equals(pairs.get(j)[0])) {
                vertices.add(new Point(currentX, parseDoubleSafe(pairs.get(j)[1])));
            }
        }

        return buildPolylinePrimitives(vertices, closed, false, common, result, end);
    }

    /**
     * POLYLINE (R12) — формат: POLYLINE → multiple VERTEX → SEQEND.
     * Каждая вершина задаётся как отдельная сущность с кодом 0 = VERTEX,
     * координаты группы 10/20.
     */
    private int parsePOLYLINE(List<String[]> pairs, int i, ImportResult result) {
        int polyEnd = findEntityEnd(pairs, i);
        EntityCommon common = readCommon(pairs, i, polyEnd);

        boolean closed = false;
        int flags = 0;
        for (int j = i; j < polyEnd; j++) {
            if ("70".equals(pairs.get(j)[0])) {
                flags = parseIntSafe(pairs.get(j)[1], 0);
                closed = (flags & 1) == 1;
            }
        }

        List<Point> vertices = new ArrayList<>();
        int k = polyEnd; // polyEnd указывает на следующую "0"-запись (VERTEX / SEQEND)

        while (k < pairs.size()) {
            String code  = pairs.get(k)[0];
            String value = pairs.get(k)[1];

            if ("0".equals(code)) {
                if ("SEQEND".equals(value)) {
                    k = findEntityEnd(pairs, k + 1); // пропускаем SEQEND
                    break;
                }
                if ("VERTEX".equals(value)) {
                    int vEnd = findEntityEnd(pairs, k + 1);
                    double vx = 0, vy = 0;
                    for (int j = k + 1; j < vEnd; j++) {
                        switch (pairs.get(j)[0]) {
                            case "10" -> vx = parseDoubleSafe(pairs.get(j)[1]);
                            case "20" -> vy = parseDoubleSafe(pairs.get(j)[1]);
                        }
                    }
                    vertices.add(new Point(vx, vy));
                    k = vEnd;
                    continue;
                }
                break;
            }
            k++;
        }

        boolean preferSpline = (flags & (2 | 4 | 128)) != 0 || vertices.size() > 20;
        return buildPolylinePrimitives(vertices, closed, preferSpline, common, result, k);
    }

    /**
     * Общий метод построения примитивов из списка вершин полилинии.
     * Распознаёт Rectangle (4 точки, прям. углы), правильный Polygon,
     * или строит набор отрезков.
     */
    private int buildPolylinePrimitives(List<Point> vertices, boolean closed, boolean preferSpline,
                                        EntityCommon common, ImportResult result,
                                        int returnIndex) {
        if (vertices.size() < 2) { result.skippedEntities++; return returnIndex; }

        if (preferSpline && vertices.size() >= 4) {
            Spline spline = new Spline(vertices, closed, 0.5, styleManager.getDefaultStyle());
            applyCommon(spline, common, result);
            result.primitives.add(spline);

        } else if (closed && vertices.size() == 4 && isRectangle(vertices)) {
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            for (Point v : vertices) {
                minX = Math.min(minX, v.getX()); minY = Math.min(minY, v.getY());
                maxX = Math.max(maxX, v.getX()); maxY = Math.max(maxY, v.getY());
            }
            Rectangle rect = new Rectangle(
                    new Point((minX + maxX) / 2, (minY + maxY) / 2),
                    maxX - minX, maxY - minY, styleManager.getDefaultStyle());
            applyCommon(rect, common, result);
            result.primitives.add(rect);

        } else if (closed && vertices.size() >= 3 && isRegularPolygon(vertices)) {
            Point  center   = centroid(vertices);
            double radius   = distance(center, vertices.get(0));
            double rotation = Math.atan2(
                    vertices.get(0).getY() - center.getY(),
                    vertices.get(0).getX() - center.getX());
            Polygon polygon = new Polygon(center, radius, vertices.size(),
                    Polygon.InscriptionType.INSCRIBED, rotation, styleManager.getDefaultStyle());
            applyCommon(polygon, common, result);
            result.primitives.add(polygon);

        } else {
            for (int j = 0; j < vertices.size() - 1; j++) {
                Segment seg = new Segment(vertices.get(j), vertices.get(j + 1),
                        Segment.CreationMode.CARTESIAN, styleManager.getDefaultStyle());
                applyCommon(seg, common, result);
                result.primitives.add(seg);
            }
            if (closed && vertices.size() > 2) {
                Segment seg = new Segment(vertices.get(vertices.size() - 1), vertices.get(0),
                        Segment.CreationMode.CARTESIAN, styleManager.getDefaultStyle());
                applyCommon(seg, common, result);
                result.primitives.add(seg);
            }
        }
        return returnIndex;
    }

    /**
     * SPLINE → Spline.
     *
     * DXF SPLINE (R2000+):
     *  - Группа 70 — флаги (бит 0 = closed, бит 2 = periodic)
     *  - Группа 73 — количество управляющих точек
     *  - Группы 10/20 — управляющие точки (WCS)
     *
     * Для R12, где SPLINE отсутствует, следует использовать POLYLINE с флагом кривой.
     */
    private int parseSpline(List<String[]> pairs, int i, ImportResult result) {
        int end = findEntityEnd(pairs, i);
        EntityCommon common = readCommon(pairs, i, end);

        boolean     closed        = false;
        List<Point> controlPoints = new ArrayList<>();
        double      currentX      = 0;

        for (int j = i; j < end; j++) {
            switch (pairs.get(j)[0]) {
                case "70" -> closed = (parseIntSafe(pairs.get(j)[1], 0) & 1) == 1;
                case "10" -> currentX = parseDoubleSafe(pairs.get(j)[1]);
                case "20" -> controlPoints.add(new Point(currentX, parseDoubleSafe(pairs.get(j)[1])));
            }
        }

        if (controlPoints.size() < 2) { result.skippedEntities++; return end; }

        Spline spline = new Spline(controlPoints, closed, 0.5, styleManager.getDefaultStyle());
        applyCommon(spline, common, result);
        result.primitives.add(spline);
        return end;
    }

    private int parsePoint(List<String[]> pairs, int i, ImportResult result) {
        int end = findEntityEnd(pairs, i);
        EntityCommon common = readCommon(pairs, i, end);

        double x = 0, y = 0;
        for (int j = i; j < end; j++) {
            switch (pairs.get(j)[0]) {
                case "10" -> x = parseDoubleSafe(pairs.get(j)[1]);
                case "20" -> y = parseDoubleSafe(pairs.get(j)[1]);
            }
        }

        PointPrimitive point = new PointPrimitive(new Point(x, y), styleManager.getDefaultStyle());
        applyCommon(point, common, result);
        result.primitives.add(point);
        return end;
    }


    /**
     * Находит начало содержимого секции.
     * Возвращает индекс первой пары ПОСЛЕ строки с именем секции, или -1.
     */
    private int findSection(List<String[]> pairs, String sectionName, int from) {
        for (int i = from; i < pairs.size() - 1; i++) {
            if ("0".equals(pairs.get(i)[0]) && "SECTION".equals(pairs.get(i)[1])) {
                i++;
                if ("2".equals(pairs.get(i)[0]) && sectionName.equals(pairs.get(i)[1])) {
                    return i + 1;
                }
            }
        }
        return -1;
    }

    /** Маппинг DXF linetype name → внутренний LineType. */
    private LineType mapDxfToLineType(String dxfLinetype) {
        String normalized = normalizeLinetypeName(dxfLinetype);
        if (normalized == null) return LineType.SOLID;
        return switch (normalized) {
            case "CONTINUOUS", "BYLAYER", "BYBLOCK" -> LineType.SOLID;
            case "THIN"                              -> LineType.SOLID;
            case "WAVES"                             -> LineType.WAVY;
            case "ZIGZAG"                            -> LineType.ZIGZAG;
            case "DASHED", "HIDDEN", "HIDDEN2"       -> LineType.DASHED;
            case "DASHDOT", "CENTER", "CENTER2"       -> LineType.DASH_DOT;
            case "DASHDOT2", "DIVIDE", "DIVIDE2"      -> LineType.DASH_DOT_DOT;
            default                                    -> LineType.SOLID;
        };
    }

    private String normalizeLayerName(String layerName) {
        if (layerName == null || layerName.isBlank()) {
            return Layer.DEFAULT_LAYER_NAME;
        }
        return "DEFPOINTS".equalsIgnoreCase(layerName) ? Layer.DEFAULT_LAYER_NAME : layerName;
    }

    private String normalizeLinetypeName(String linetypeName) {
        if (linetypeName == null || linetypeName.isBlank()) {
            return null;
        }
        String normalized = linetypeName.trim().toUpperCase(Locale.ROOT);
        int suffixIdx = normalized.indexOf('_');
        if (suffixIdx > 0) {
            normalized = normalized.substring(0, suffixIdx);
        }
        return normalized;
    }

    private void upsertLayer(ImportResult result, Layer layer) {
        for (int i = 0; i < result.layers.size(); i++) {
            if (result.layers.get(i).getName().equals(layer.getName())) {
                result.layers.set(i, layer);
                return;
            }
        }
        result.layers.add(layer);
    }

    private Layer findLayer(ImportResult result, String layerName) {
        String normalizedLayerName = normalizeLayerName(layerName);
        for (Layer layer : result.layers) {
            if (layer.getName().equals(normalizedLayerName)) {
                return layer;
            }
        }
        return null;
    }

    private String resolveEffectiveLinetype(EntityCommon common, ImportResult result) {
        String normalized = normalizeLinetypeName(common.linetypeName);
        if (normalized == null || "BYLAYER".equals(normalized) || "BYBLOCK".equals(normalized)) {
            Layer layer = findLayer(result, common.layerName);
            return layer != null ? layer.getLinetypeName() : "CONTINUOUS";
        }
        return common.linetypeName;
    }

    private int resolveEffectiveColorIndex(EntityCommon common, ImportResult result) {
        if (common.colorIndex >= 1 && common.colorIndex <= 255) {
            return common.colorIndex;
        }
        Layer layer = findLayer(result, common.layerName);
        return layer != null ? layer.getColorIndex() : -1;
    }

    private int resolveEffectiveLineweight(EntityCommon common, ImportResult result) {
        if (common.lineWeight >= 0) {
            return common.lineWeight;
        }
        return result.layerLineweights.getOrDefault(common.layerName, -1);
    }

    private LineStyle findStyle(LineType type, LineStyle.ThicknessCategory thicknessCategory) {
        for (LineStyle style : styleManager.getStyles()) {
            if (style.getType() != type) {
                continue;
            }
            if (thicknessCategory == null || style.getThicknessCategory() == thicknessCategory) {
                return style;
            }
        }
        return null;
    }

    private boolean isRectangle(List<Point> v) {
        if (v.size() != 4) return false;
        for (int i = 0; i < 4; i++) {
            Point a = v.get(i), b = v.get((i + 1) % 4), c = v.get((i + 2) % 4);
            double dx1 = b.getX() - a.getX(), dy1 = b.getY() - a.getY();
            double dx2 = c.getX() - b.getX(), dy2 = c.getY() - b.getY();
            double dot     = dx1 * dx2 + dy1 * dy2;
            double lenProd = Math.sqrt((dx1*dx1 + dy1*dy1) * (dx2*dx2 + dy2*dy2));
            if (lenProd > 0 && Math.abs(dot) > 0.01 * lenProd) return false;
        }
        return true;
    }

    private boolean isRegularPolygon(List<Point> v) {
        if (v.size() < 3) return false;
        Point  center  = centroid(v);
        double refDist = distance(center, v.get(0));
        if (refDist < 0.001) return false;

        for (Point p : v) {
            if (Math.abs(distance(center, p) - refDist) / refDist > 0.02) return false;
        }
        double refEdge = distance(v.get(0), v.get(1));
        if (refEdge < 0.001) return false;
        for (int i = 1; i < v.size(); i++) {
            double edge = distance(v.get(i), v.get((i + 1) % v.size()));
            if (Math.abs(edge - refEdge) / refEdge > 0.02) return false;
        }
        return true;
    }

    private Point centroid(List<Point> points) {
        double sx = 0, sy = 0;
        for (Point p : points) { sx += p.getX(); sy += p.getY(); }
        return new Point(sx / points.size(), sy / points.size());
    }

    private double distance(Point a, Point b) {
        double dx = b.getX() - a.getX(), dy = b.getY() - a.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private double parseDoubleSafe(String s) {
        if (s == null || s.isEmpty()) return 0;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0; }
    }

    private int parseIntSafe(String s, int def) {
        if (s == null || s.isEmpty()) return def;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    /**
     * Стандартная палитра AutoCAD ACI (цвета 1–9).
     * Индекс 0 — не используется (BYBLOCK), 7 = белый/чёрный зависит от фона.
     * Для цветов 10–255 возвращаем null (будет использован цвет слоя/приложения).
     */
    public static javafx.scene.paint.Color aciToColor(int aci) {
        return switch (aci) {
            case 1  -> javafx.scene.paint.Color.RED;
            case 2  -> javafx.scene.paint.Color.YELLOW;
            case 3  -> javafx.scene.paint.Color.LIME;
            case 4  -> javafx.scene.paint.Color.CYAN;
            case 5  -> javafx.scene.paint.Color.BLUE;
            case 6  -> javafx.scene.paint.Color.MAGENTA;
            case 7  -> javafx.scene.paint.Color.WHITE;
            case 8  -> javafx.scene.paint.Color.DARKGRAY;
            case 9  -> javafx.scene.paint.Color.LIGHTGRAY;
            default -> null;
        };
    }
}


