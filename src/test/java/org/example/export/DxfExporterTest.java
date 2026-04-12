package org.example.export;

import org.example.model.CadModel;
import org.example.model.Circle;
import org.example.model.Ellipse;
import org.example.model.Layer;
import org.example.model.LineStyle;
import org.example.model.LineType;
import org.example.model.Point;
import org.example.model.Segment;
import org.example.model.Spline;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DxfExporterTest {

    @Test
    void exportsLocaleIndependentNumbersAndPreservesLayerData() throws Exception {
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("ru-RU"));
        try {
            CadModel model = new CadModel();
            Layer layer = new Layer("Слой-1", 5, "DASHED");
            model.addLayer(layer);
            model.setActiveLayerName(layer.getName());

            LineStyle style = new LineStyle("Solid", 1.0, null, false, LineType.SOLID);
            style.setThicknessMm(0.5);

            Segment segment = new Segment(
                    new Point(1.25, 2.5),
                    new Point(3.75, 4.125),
                    Segment.CreationMode.CARTESIAN,
                    style);
            segment.setColorAci(3);
            model.addPrimitive(segment);

            Path file = Files.createTempFile("export-", ".dxf");
            new DxfExporter(DxfExporter.DxfVersion.R2007, DxfExporter.DxfUnits.MILLIMETERS)
                    .export(model, file.toFile());

            String dxf = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");

            assertTrue(dxf.contains("Слой-1"));
            assertTrue(dxf.contains("\n 62\n3\n"));
            assertTrue(dxf.contains("\n 10\n1.250000\n"));
            assertTrue(dxf.contains("\n 20\n2.500000\n"));
            assertTrue(dxf.contains("\n  2\nDASHED\n"));
            assertFalse(dxf.contains("1,250000"));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void fallsBackToR12CompatibleEntities() throws Exception {
        CadModel model = new CadModel();
        LineStyle style = new LineStyle("Dashed", 1.0, null, false, LineType.DASHED);

        Ellipse ellipse = new Ellipse(new Point(10, 15), 6, 3, Math.toRadians(30), style);
        Spline spline = new Spline(List.of(
                new Point(0, 0),
                new Point(5, 10),
                new Point(10, 5),
                new Point(15, 15)), style);

        model.addPrimitive(ellipse);
        model.addPrimitive(spline);

        Path file = Files.createTempFile("export-r12-", ".dxf");
        new DxfExporter(DxfExporter.DxfVersion.R12, DxfExporter.DxfUnits.MILLIMETERS)
                .export(model, file.toFile());

        String dxf = Files.readString(file, StandardCharsets.US_ASCII).replace("\r\n", "\n");

        assertTrue(dxf.contains("AC1009"));
        assertTrue(dxf.contains("POLYLINE"));
        assertFalse(dxf.contains("LWPOLYLINE"));
        assertFalse(dxf.contains("\nELLIPSE\n"));
        assertFalse(dxf.contains("\nSPLINE\n"));
    }

    @Test
    void preservesNativeEntitiesForWavyAndZigzagByDefault() throws Exception {
        CadModel model = new CadModel();

        LineStyle wavy = new LineStyle("Wavy", 1.0, new double[] { 2.5, 10.0 }, false, LineType.WAVY);
        wavy.setWaveAmplitude(2.5);
        wavy.setWaveLength(10.0);

        LineStyle zigzag = new LineStyle("Zigzag", 1.0, new double[] { 6.0, 8.0, 4.0 }, false, LineType.ZIGZAG);

        model.addPrimitive(new Circle(new Point(20, 20), 10, wavy));
        model.addPrimitive(new Segment(
                new Point(0, 0),
                new Point(40, 0),
                Segment.CreationMode.CARTESIAN,
                zigzag));

        Path file = Files.createTempFile("export-styled-", ".dxf");
        new DxfExporter(DxfExporter.DxfVersion.R12, DxfExporter.DxfUnits.MILLIMETERS)
                .export(model, file.toFile());

        String dxf = Files.readString(file, StandardCharsets.US_ASCII).replace("\r\n", "\n");

        assertTrue(dxf.contains("\nCIRCLE\n"));
        assertTrue(dxf.contains("\nLINE\n"));
        assertFalse(dxf.contains("POLYLINE"));
        assertTrue(dxf.contains("\n  6\nWAVES\n"));
        assertTrue(dxf.contains("\n  6\nZIGZAG\n"));
        assertTrue(dxf.contains("\n 48\n0.500000\n"));
        assertTrue(dxf.contains("\n 48\n0.250000\n"));

        String wavesLinetype = extractLinetypeRecord(dxf, "WAVES");
        String zigzagLinetype = extractLinetypeRecord(dxf, "ZIGZAG");
        assertTrue(wavesLinetype.contains("\n 73\n0\n"));
        assertTrue(zigzagLinetype.contains("\n 73\n0\n"));
        assertFalse(wavesLinetype.contains("\n 49\n"));
        assertFalse(zigzagLinetype.contains("\n 49\n"));
    }

    private String extractLinetypeRecord(String dxf, String name) {
        String marker = "\n  0\nLTYPE\n  2\n" + name + "\n";
        int start = dxf.indexOf(marker);
        if (start < 0) {
            return "";
        }

        int next = dxf.indexOf("\n  0\n", start + marker.length());
        if (next < 0) {
            return dxf.substring(start);
        }

        return dxf.substring(start, next);
    }
}
