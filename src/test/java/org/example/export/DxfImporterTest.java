package org.example.export;

import org.example.model.Circle;
import org.example.model.LineType;
import org.example.model.Primitive;
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
}
