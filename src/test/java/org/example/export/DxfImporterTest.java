package org.example.export;

import org.example.model.AngularDimension;
import org.example.model.CadModel;
import org.example.model.Circle;
import org.example.model.DimensionAnchor;
import org.example.model.Ellipse;
import org.example.model.LinearDimension;
import org.example.model.LineType;
import org.example.model.Point;
import org.example.model.Polyline;
import org.example.model.Primitive;
import org.example.model.RadialDimension;
import org.example.model.Spline;
import org.example.model.StyleManager;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DxfImporterTest {

    @Test
    void importsTflexByLayerStyleAndDefpoints() throws Exception {
        String dxf = """
                  0
                SECTION
                  2
                TABLES
                  0
                TABLE
                  2
                LTYPE
                 70
                3
                  0
                LTYPE
                  2
                CONTINUOUS
                 70
                0
                  3
                Solid line
                 72
                65
                 73
                0
                 40
                0.0
                  0
                LTYPE
                  2
                WAVES
                 70
                0
                  3
                T-FLEX waves
                 72
                65
                 73
                0
                 40
                0.0
                  0
                LTYPE
                  2
                ZIGZAG
                 70
                0
                  3
                T-FLEX zigzag
                 72
                65
                 73
                0
                 40
                0.0
                  0
                ENDTAB
                  0
                TABLE
                  2
                LAYER
                 70
                2
                  0
                LAYER
                  2
                DETAIL
                 70
                0
                 62
                5
                  6
                WAVES
                370
                25
                  0
                LAYER
                  2
                Defpoints
                 70
                0
                 62
                3
                  6
                CONTINUOUS
                  0
                ENDTAB
                  0
                ENDSEC
                  0
                SECTION
                  2
                ENTITIES
                  0
                CIRCLE
                  8
                DETAIL
                  6
                BYLAYER
                 62
                256
                 10
                10.0
                 20
                20.0
                 40
                5.0
                  0
                LINE
                  8
                Defpoints
                  6
                ZIGZAG_per6_scale0.872385
                 10
                0.0
                 20
                0.0
                 11
                5.0
                 21
                1.0
                  0
                ENDSEC
                  0
                EOF
                """;

        Path file = Files.createTempFile("import-tflex-", ".dxf");
        Files.writeString(file, dxf, StandardCharsets.UTF_8);

        DxfImporter importer = new DxfImporter(new StyleManager());
        DxfImporter.ImportResult result = importer.importFile(file.toFile());

        assertEquals(2, result.getPrimitives().size());
        assertEquals(2, result.getLayers().size());

        Primitive circle = result.getPrimitives().get(0);
        Primitive line = result.getPrimitives().get(1);

        assertInstanceOf(Circle.class, circle);
        assertEquals("DETAIL", circle.getLayerName());
        assertEquals(LineType.WAVY, circle.getLineStyle().getType());
        assertEquals(5, circle.getColorAci());

        assertEquals("0", line.getLayerName());
        assertEquals(LineType.ZIGZAG, line.getLineStyle().getType());
        assertEquals(3, line.getColorAci());
        assertTrue(result.getLayers().stream().anyMatch(layer -> layer.getName().equals("0")));
    }

    @Test
    void importsDensePolylineAsSpline() throws Exception {
        StringBuilder dxf = new StringBuilder();
        dxf.append("  0\nSECTION\n  2\nENTITIES\n");
        dxf.append("  0\nPOLYLINE\n  8\n0\n 70\n128\n");
        for (int i = 0; i < 24; i++) {
            double x = i;
            double y = Math.sin(i / 3.0);
            dxf.append("  0\nVERTEX\n");
            dxf.append(" 10\n").append(x).append("\n");
            dxf.append(" 20\n").append(y).append("\n");
        }
        dxf.append("  0\nSEQEND\n  0\nENDSEC\n  0\nEOF\n");

        Path file = Files.createTempFile("import-poly-", ".dxf");
        Files.writeString(file, dxf.toString(), StandardCharsets.UTF_8);

        DxfImporter importer = new DxfImporter(new StyleManager());
        DxfImporter.ImportResult result = importer.importFile(file.toFile());

        assertEquals(1, result.getPrimitives().size());
        Primitive primitive = result.getPrimitives().get(0);
        assertInstanceOf(Spline.class, primitive);
        assertEquals(LineType.SOLID, primitive.getLineStyle().getType());
        assertEquals(24, ((Spline) primitive).getControlPointsList().size());
    }

    @Test
    void importsOpenPolylineAsSinglePrimitive() throws Exception {
        String dxf = """
                  0
                SECTION
                  2
                ENTITIES
                  0
                LWPOLYLINE
                  8
                0
                 90
                3
                 70
                0
                 10
                0.0
                 20
                0.0
                 10
                10.0
                 20
                0.0
                 10
                10.0
                 20
                5.0
                  0
                ENDSEC
                  0
                EOF
                """;

        Path file = Files.createTempFile("import-lwpoly-", ".dxf");
        Files.writeString(file, dxf, StandardCharsets.UTF_8);

        DxfImporter importer = new DxfImporter(new StyleManager());
        DxfImporter.ImportResult result = importer.importFile(file.toFile());

        assertEquals(1, result.getPrimitives().size());
        Primitive primitive = result.getPrimitives().get(0);
        assertInstanceOf(Polyline.class, primitive);
        assertEquals(3, ((Polyline) primitive).getVertexCount());
    }

    @Test
    void importsClosedEllipseApproximationAsEllipse() throws Exception {
        StringBuilder dxf = new StringBuilder();
        dxf.append("  0\nSECTION\n  2\nENTITIES\n");
        dxf.append("  0\nLWPOLYLINE\n  8\n0\n 90\n24\n 70\n1\n");

        Point center = new Point(12.0, -4.0);
        double semiMajor = 8.0;
        double semiMinor = 3.5;
        double rotation = Math.toRadians(25.0);

        for (int i = 0; i < 24; i++) {
            double angle = 2.0 * Math.PI * i / 24.0;
            double x = semiMajor * Math.cos(angle);
            double y = semiMinor * Math.sin(angle);
            double worldX = center.getX() + x * Math.cos(rotation) - y * Math.sin(rotation);
            double worldY = center.getY() + x * Math.sin(rotation) + y * Math.cos(rotation);
            dxf.append(" 10\n").append(worldX).append("\n");
            dxf.append(" 20\n").append(worldY).append("\n");
        }

        dxf.append("  0\nENDSEC\n  0\nEOF\n");

        Path file = Files.createTempFile("import-ellipse-poly-", ".dxf");
        Files.writeString(file, dxf.toString(), StandardCharsets.UTF_8);

        DxfImporter importer = new DxfImporter(new StyleManager());
        DxfImporter.ImportResult result = importer.importFile(file.toFile());

        assertEquals(1, result.getPrimitives().size());
        Primitive primitive = result.getPrimitives().get(0);
        Ellipse ellipse = assertInstanceOf(Ellipse.class, primitive);
        assertEquals(center.getX(), ellipse.getCenter().getX(), 0.05);
        assertEquals(center.getY(), ellipse.getCenter().getY(), 0.05);
        assertEquals(semiMajor, ellipse.getSemiMajorAxis(), 0.05);
        assertEquals(semiMinor, ellipse.getSemiMinorAxis(), 0.05);
    }

    @Test
    void importsDimensionsExportedByApplication() throws Exception {
        StyleManager styles = new StyleManager();
        CadModel model = new CadModel();

        LinearDimension linear = new LinearDimension(
                DimensionAnchor.fixed(new Point(0, 0)),
                DimensionAnchor.fixed(new Point(40, 0)),
                new Point(0, 12),
                LinearDimension.Orientation.HORIZONTAL,
                styles.getDefaultStyle());
        linear.setTextOverride("L=40");
        linear.setTextHeight(9);
        linear.setArrowSize(4);
        linear.setTextPositionFactor(0.75);
        model.addPrimitive(linear);

        Circle circle = new Circle(new Point(80, 20), 12, styles.getDefaultStyle());
        RadialDimension radial = new RadialDimension(
                circle,
                new Point(100, 32),
                RadialDimension.Kind.DIAMETER,
                styles.getDefaultStyle());
        radial.setShelfSide(RadialDimension.ShelfSide.RIGHT);
        model.addPrimitive(radial);

        AngularDimension angular = new AngularDimension(
                DimensionAnchor.fixed(new Point(120, 0)),
                DimensionAnchor.fixed(new Point(150, 0)),
                DimensionAnchor.fixed(new Point(120, 30)),
                new Point(140, 20),
                styles.getDefaultStyle());
        angular.setTextOverride("90deg");
        model.addPrimitive(angular);

        Path file = Files.createTempFile("dimensions-roundtrip-", ".dxf");
        new DxfExporter(DxfExporter.DxfVersion.R2007, DxfExporter.DxfUnits.MILLIMETERS)
                .export(model, file.toFile());

        DxfImporter.ImportResult result = new DxfImporter(new StyleManager()).importFile(file.toFile());

        assertEquals(3, result.getPrimitives().size());

        LinearDimension importedLinear = assertInstanceOf(LinearDimension.class, result.getPrimitives().get(0));
        assertEquals(LinearDimension.Orientation.HORIZONTAL, importedLinear.getOrientation());
        assertEquals("L=40", importedLinear.getTextOverride());
        assertEquals(40.0, importedLinear.getMeasuredValue(), 0.001);
        assertEquals(9.0, importedLinear.getTextHeight(), 0.001);
        assertEquals(4.0, importedLinear.getArrowSize(), 0.001);
        assertEquals(0.75, importedLinear.getTextPositionFactor(), 0.001);

        RadialDimension importedRadial = assertInstanceOf(RadialDimension.class, result.getPrimitives().get(1));
        assertEquals(RadialDimension.Kind.DIAMETER, importedRadial.getKind());
        assertEquals(RadialDimension.ShelfSide.RIGHT, importedRadial.getShelfSide());
        assertEquals(24.0, importedRadial.getMeasuredValue(), 0.001);

        AngularDimension importedAngular = assertInstanceOf(AngularDimension.class, result.getPrimitives().get(2));
        assertEquals("90deg", importedAngular.getTextOverride());
        assertEquals(90.0, importedAngular.getMeasuredValue(), 0.001);
    }
}
