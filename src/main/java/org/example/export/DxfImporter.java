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
            case "DIMENSION"  -> { return parseDimension(pairs, i, result); }
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

    private int parseDimension(List<String[]> pairs, int i, ImportResult result) {
        int end = findEntityEnd(pairs, i);
        EntityCommon common = readCommon(pairs, i, end);
        Map<String, String> data = readDimensionXData(pairs, i, end);

        int flags = 0;
        String textOverride = "";
        Point definitionPoint = new Point(0, 0);
        Point textPoint = null;
        Point p13 = null;
        Point p14 = null;
        Point p15 = null;
        Point p16 = null;
        double rotation = 0.0;

        for (int j = i; j < end; j++) {
            String code = pairs.get(j)[0];
            String value = pairs.get(j)[1];
            switch (code) {
                case "1" -> textOverride = "<>".equals(value) ? "" : value;
                case "10" -> definitionPoint = new Point(parseDoubleSafe(value), pointY(pairs, j, definitionPoint.getY()));
                case "11" -> textPoint = new Point(parseDoubleSafe(value), pointY(pairs, j, 0.0));
                case "13" -> p13 = new Point(parseDoubleSafe(value), pointY(pairs, j, 0.0));
                case "14" -> p14 = new Point(parseDoubleSafe(value), pointY(pairs, j, 0.0));
                case "15" -> p15 = new Point(parseDoubleSafe(value), pointY(pairs, j, 0.0));
                case "16" -> p16 = new Point(parseDoubleSafe(value), pointY(pairs, j, 0.0));
                case "50" -> rotation = parseDoubleSafe(value);
                case "70" -> flags = parseIntSafe(value, 0);
            }
        }

        DimensionPrimitive dimension = buildDimension(flags, data, definitionPoint, textPoint,
                p13, p14, p15, p16, rotation);
        if (dimension == null) {
            result.skippedEntities++;
            return end;
        }

        applyCommon(dimension, common, result);
        applyDimensionSettings(dimension, data, textOverride, textPoint);
        result.primitives.add(dimension);
        return end;
    }

    private DimensionPrimitive buildDimension(int flags, Map<String, String> data, Point definitionPoint,
                                              Point textPoint, Point p13, Point p14, Point p15, Point p16,
                                              double rotation) {
        String exportedType = data.get("DIMTYPE");
        int baseType = flags & 7;

        if ("LINEAR".equals(exportedType) || baseType == 0 || baseType == 1) {
            Point first = p13 != null ? p13 : new Point(0, 0);
            Point second = p14 != null ? p14 : definitionPoint;
            Point dimLine = pointFromData(data, "DIM_LINE_X", "DIM_LINE_Y", definitionPoint);
            LinearDimension.Orientation orientation = parseEnum(data.get("ORIENTATION"),
                    inferLinearOrientation(baseType, rotation));
            LinearDimension dimension = new LinearDimension(
                    DimensionAnchor.fixed(first),
                    DimensionAnchor.fixed(second),
                    dimLine,
                    orientation,
                    styleManager.getDefaultStyle());
            if (data.containsKey("TEXT_FACTOR")) {
                dimension.setTextPositionFactor(parseDoubleSafe(data.get("TEXT_FACTOR")));
            } else if (textPoint != null) {
                dimension.setTextPosition(textPoint);
            }
            dimension.setExtensionLineOffset(parseDoubleSafe(data.getOrDefault("EXT_OFFSET", "0")));
            dimension.setExtensionLineOvershoot(parseDoubleSafe(data.getOrDefault("EXT_OVERSHOOT", "8")));
            dimension.setDimensionLineExtension(parseDoubleSafe(data.getOrDefault("DIM_EXTENSION", "0")));
            return dimension;
        }

        if ("RADIAL".equals(exportedType) || baseType == 3 || baseType == 4) {
            Point center = pointFromData(data, "CENTER_X", "CENTER_Y", p15 != null ? p15 : new Point(0, 0));
            Point leader = pointFromData(data, "LEADER_X", "LEADER_Y", p16 != null ? p16 : definitionPoint);
            double radius = parseDoubleSafe(data.getOrDefault("RADIUS", "0"));
            if (radius <= 0.0) {
                radius = Math.max(distance(center, definitionPoint), 1.0);
            }
            Circle reference = new Circle(center, radius, styleManager.getDefaultStyle());
            RadialDimension.Kind kind = parseEnum(data.get("RADIAL_KIND"),
                    baseType == 3 ? RadialDimension.Kind.DIAMETER : RadialDimension.Kind.RADIUS);
            RadialDimension dimension = new RadialDimension(reference, leader, kind, styleManager.getDefaultStyle());
            dimension.setShelfSide(parseEnum(data.get("SHELF_SIDE"), RadialDimension.ShelfSide.ALONG_LINE));
            if (textPoint != null) {
                dimension.setTextPosition(textPoint);
            }
            return dimension;
        }

        if ("ANGULAR".equals(exportedType) || baseType == 2 || baseType == 5) {
            Point vertex = pointFromData(data, "VERTEX_X", "VERTEX_Y", p13 != null ? p13 : new Point(0, 0));
            Point first = pointFromData(data, "FIRST_X", "FIRST_Y", p14 != null ? p14 : new Point(vertex.getX() + 1, vertex.getY()));
            Point second = pointFromData(data, "SECOND_X", "SECOND_Y", p15 != null ? p15 : new Point(vertex.getX(), vertex.getY() + 1));
            Point arcPoint = pointFromData(data, "ARC_X", "ARC_Y", definitionPoint);
            AngularDimension dimension = new AngularDimension(
                    DimensionAnchor.fixed(vertex),
                    DimensionAnchor.fixed(first),
                    DimensionAnchor.fixed(second),
                    arcPoint,
                    styleManager.getDefaultStyle());
            if (textPoint != null) {
                dimension.setTextPosition(textPoint);
            }
            return dimension;
        }

        return null;
    }

    private Map<String, String> readDimensionXData(List<String[]> pairs, int start, int end) {
        Map<String, String> data = new LinkedHashMap<>();
        boolean inCadSystemData = false;
        for (int i = start; i < end; i++) {
            String code = pairs.get(i)[0];
            String value = pairs.get(i)[1];
            if ("1001".equals(code)) {
                inCadSystemData = "CADSYSTEM".equalsIgnoreCase(value);
                continue;
            }
            if (inCadSystemData && "1000".equals(code)) {
                int separator = value.indexOf('=');
                if (separator > 0) {
                    data.put(value.substring(0, separator), value.substring(separator + 1));
                }
            }
        }
        return data;
    }

    private void applyDimensionSettings(DimensionPrimitive dimension, Map<String, String> data,
                                        String entityTextOverride, Point textPoint) {
        String textOverride = data.getOrDefault("TEXT_OVERRIDE", entityTextOverride);
        if (textOverride != null && !textOverride.isBlank() && !"<>".equals(textOverride)) {
            dimension.setTextOverride(textOverride);
        }
        if (data.containsKey("TEXT_HEIGHT")) {
            dimension.setTextHeight(parseDoubleSafe(data.get("TEXT_HEIGHT")));
        }
        if (data.containsKey("TEXT_GAP")) {
            dimension.setTextGap(parseDoubleSafe(data.get("TEXT_GAP")));
        }
        if (data.containsKey("ARROW_SIZE")) {
            dimension.setArrowSize(parseDoubleSafe(data.get("ARROW_SIZE")));
        }
        dimension.setTextPlacement(parseEnum(data.get("TEXT_PLACEMENT"), dimension.getTextPlacement()));
        dimension.setArrowType(parseEnum(data.get("ARROW_TYPE"), dimension.getArrowType()));
        dimension.setFontVariant(parseEnum(data.get("FONT_VARIANT"), dimension.getFontVariant()));
        if (data.containsKey("FILLED_ARROWS")) {
            dimension.setFilledArrows(Boolean.parseBoolean(data.get("FILLED_ARROWS")));
        }
        if (data.containsKey("TEXT_FONT")) {
            dimension.setTextFont(data.get("TEXT_FONT"));
        }
        if (textPoint != null && !dimension.isTextPositionManuallyMoved()) {
            dimension.setTextPosition(textPoint);
        }
    }

    private LinearDimension.Orientation inferLinearOrientation(int baseType, double rotation) {
        if (baseType == 1) {
            return LinearDimension.Orientation.ALIGNED;
        }
        double normalized = Math.abs(rotation % 180.0);
        return Math.abs(normalized - 90.0) < 1e-6
                ? LinearDimension.Orientation.VERTICAL
                : LinearDimension.Orientation.HORIZONTAL;
    }

    private Point pointFromData(Map<String, String> data, String xKey, String yKey, Point fallback) {
        if (data.containsKey(xKey) && data.containsKey(yKey)) {
            return new Point(parseDoubleSafe(data.get(xKey)), parseDoubleSafe(data.get(yKey)));
        }
        return fallback;
    }

    private double pointY(List<String[]> pairs, int xIndex, double fallback) {
        String yCode = String.valueOf(parseIntSafe(pairs.get(xIndex)[0], 0) + 10);
        if (xIndex + 1 < pairs.size() && yCode.equals(pairs.get(xIndex + 1)[0])) {
            return parseDoubleSafe(pairs.get(xIndex + 1)[1]);
        }
        return fallback;
    }

    private <T extends Enum<T>> T parseEnum(String value, T fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(fallback.getDeclaringClass(), value);
        } catch (IllegalArgumentException ex) {
            return fallback;
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
     *
     * Порядок распознавания для замкнутых полилиний:
     *  1. Rectangle — 4 точки с прямыми углами
     *  2. Polygon   — правильный многоугольник (до 20 вершин)
     *  3. Circle    — много точек, все на одном расстоянии от центроида
     *  4. Ellipse   — подгонка эллипсом методом наименьших квадратов
     *  5. Spline / Polyline — fallback
     *
     * Для открытых полилиний: Spline если preferSpline, иначе Polyline.
     */
    private int buildPolylinePrimitives(List<Point> vertices, boolean closed, boolean preferSpline,
                                        EntityCommon common, ImportResult result,
                                        int returnIndex) {
        if (vertices.size() < 2) { result.skippedEntities++; return returnIndex; }

        if (closed) {
            // 1. Прямоугольник
            if (vertices.size() == 4 && isRectangle(vertices)) {
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
                return returnIndex;
            }

            // 2. Правильный многоугольник (только небольшие — иначе это аппроксимация кривой)
            if (vertices.size() >= 3 && vertices.size() <= 20 && isRegularPolygon(vertices)) {
                Point  center   = centroid(vertices);
                double radius   = distance(center, vertices.get(0));
                double rotation = Math.atan2(
                        vertices.get(0).getY() - center.getY(),
                        vertices.get(0).getX() - center.getX());
                Polygon polygon = new Polygon(center, radius, vertices.size(),
                        Polygon.InscriptionType.INSCRIBED, rotation, styleManager.getDefaultStyle());
                applyCommon(polygon, common, result);
                result.primitives.add(polygon);
                return returnIndex;
            }

            // 3. Окружность (много точек, равноудалённых от центра)
            if (vertices.size() >= 8) {
                Circle circle = tryCreateCircleFromPolyline(vertices);
                if (circle != null) {
                    applyCommon(circle, common, result);
                    result.primitives.add(circle);
                    return returnIndex;
                }

                // 4. Эллипс (подгонка МНК)
                Ellipse ellipse = tryCreateEllipseFromPolyline(vertices);
                if (ellipse != null) {
                    ellipse.setLineStyle(styleManager.getDefaultStyle());
                    applyCommon(ellipse, common, result);
                    result.primitives.add(ellipse);
                    return returnIndex;
                }
            }

            // 5. Fallback для замкнутой кривой
            if (preferSpline && vertices.size() >= 4) {
                Spline spline = new Spline(vertices, true, 0.5, styleManager.getDefaultStyle());
                applyCommon(spline, common, result);
                result.primitives.add(spline);
            } else {
                Polyline polyline = new Polyline(vertices, true, styleManager.getDefaultStyle());
                applyCommon(polyline, common, result);
                result.primitives.add(polyline);
            }

        } else {
            // Открытая полилиния
            if (preferSpline && vertices.size() >= 4) {
                Spline spline = new Spline(vertices, false, 0.5, styleManager.getDefaultStyle());
                applyCommon(spline, common, result);
                result.primitives.add(spline);
            } else {
                Polyline polyline = new Polyline(vertices, false, styleManager.getDefaultStyle());
                applyCommon(polyline, common, result);
                result.primitives.add(polyline);
            }
        }
        return returnIndex;
    }

    /**
     * Пытается распознать окружность из замкнутой полилинии.
     * Все точки должны быть равноудалены от центроида с допуском 2%.
     */
    private Circle tryCreateCircleFromPolyline(List<Point> vertices) {
        if (vertices.size() < 8) return null;
        Point center = centroid(vertices);
        double sumR = 0;
        for (Point p : vertices) sumR += distance(center, p);
        double avgR = sumR / vertices.size();
        if (avgR < 0.001) return null;
        for (Point p : vertices) {
            if (Math.abs(distance(center, p) - avgR) / avgR > 0.02) return null;
        }
        return new Circle(center, avgR, styleManager.getDefaultStyle());
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

    private Ellipse tryCreateEllipseFromPolyline(List<Point> vertices) {
        if (vertices.size() < 8) {
            return null;
        }

        Point centroid = centroid(vertices);

        double sxx = 0.0;
        double syy = 0.0;
        double sxy = 0.0;
        for (Point point : vertices) {
            double dx = point.getX() - centroid.getX();
            double dy = point.getY() - centroid.getY();
            sxx += dx * dx;
            syy += dy * dy;
            sxy += dx * dy;
        }
        sxx /= vertices.size();
        syy /= vertices.size();
        sxy /= vertices.size();

        double trace = sxx + syy;
        double diff = sxx - syy;
        double root = Math.sqrt(diff * diff + 4.0 * sxy * sxy);
        double lambdaMajor = (trace + root) / 2.0;
        double lambdaMinor = (trace - root) / 2.0;
        if (lambdaMajor <= 1e-9 || lambdaMinor <= 1e-9) {
            return null;
        }

        double rotation = 0.5 * Math.atan2(2.0 * sxy, diff);
        double cos = Math.cos(rotation);
        double sin = Math.sin(rotation);

        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (Point point : vertices) {
            double dx = point.getX() - centroid.getX();
            double dy = point.getY() - centroid.getY();
            double localX = dx * cos + dy * sin;
            double localY = -dx * sin + dy * cos;
            minX = Math.min(minX, localX);
            maxX = Math.max(maxX, localX);
            minY = Math.min(minY, localY);
            maxY = Math.max(maxY, localY);
        }

        double localCenterX = (minX + maxX) / 2.0;
        double localCenterY = (minY + maxY) / 2.0;
        Point center = new Point(
                centroid.getX() + localCenterX * cos - localCenterY * sin,
                centroid.getY() + localCenterX * sin + localCenterY * cos);

        double xx2 = 0.0;
        double yy2 = 0.0;
        double x2y2 = 0.0;
        double x2 = 0.0;
        double y2 = 0.0;
        for (Point point : vertices) {
            double dx = point.getX() - center.getX();
            double dy = point.getY() - center.getY();
            double localX = dx * cos + dy * sin;
            double localY = -dx * sin + dy * cos;
            double vx = localX * localX;
            double vy = localY * localY;
            xx2 += vx * vx;
            yy2 += vy * vy;
            x2y2 += vx * vy;
            x2 += vx;
            y2 += vy;
        }

        double det = xx2 * yy2 - x2y2 * x2y2;
        if (Math.abs(det) < 1e-12) {
            return null;
        }

        double invA = (x2 * yy2 - y2 * x2y2) / det;
        double invB = (y2 * xx2 - x2 * x2y2) / det;
        if (invA <= 0.0 || invB <= 0.0) {
            return null;
        }

        double semiMajor = Math.sqrt(1.0 / invA);
        double semiMinor = Math.sqrt(1.0 / invB);
        if (semiMajor < semiMinor) {
            double tmp = semiMajor;
            semiMajor = semiMinor;
            semiMinor = tmp;
            rotation += Math.PI / 2.0;
            cos = Math.cos(rotation);
            sin = Math.sin(rotation);
        }

        if (semiMajor <= 0.001 || semiMinor <= 0.001) {
            return null;
        }

        double meanResidual = 0.0;
        double maxResidual = 0.0;
        for (Point point : vertices) {
            double dx = point.getX() - center.getX();
            double dy = point.getY() - center.getY();
            double localX = dx * cos + dy * sin;
            double localY = -dx * sin + dy * cos;
            double normalized = (localX * localX) / (semiMajor * semiMajor)
                    + (localY * localY) / (semiMinor * semiMinor);
            double residual = Math.abs(normalized - 1.0);
            meanResidual += residual;
            maxResidual = Math.max(maxResidual, residual);
        }
        meanResidual /= vertices.size();

        if (meanResidual > 0.12 || maxResidual > 0.25) {
            return null;
        }

        return new Ellipse(center, semiMajor, semiMinor, normalizeAngle(rotation), styleManager.getDefaultStyle());
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

    private double normalizeAngle(double angle) {
        double normalized = angle % Math.PI;
        if (normalized < 0) {
            normalized += Math.PI;
        }
        return normalized;
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
            case 5  -> javafx.scene.paint.Color.DARKBLUE;
            case 6  -> javafx.scene.paint.Color.MAGENTA;
            case 7  -> javafx.scene.paint.Color.WHITE;
            case 8  -> javafx.scene.paint.Color.DARKGRAY;
            case 9  -> javafx.scene.paint.Color.LIGHTGRAY;
            default -> null;
        };
    }
}


