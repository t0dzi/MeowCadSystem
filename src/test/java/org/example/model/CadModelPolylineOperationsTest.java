package org.example.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CadModelPolylineOperationsTest {

    @Test
    void gluesConnectedSegmentsIntoPolyline() {
        CadModel model = new CadModel();
        LineStyle style = new LineStyle("Solid", 1.0, null, false, LineType.SOLID);

        Segment first = new Segment(new Point(0, 0), new Point(10, 0), Segment.CreationMode.CARTESIAN, style);
        Segment second = new Segment(new Point(10, 0), new Point(15, 5), Segment.CreationMode.CARTESIAN, style);

        model.addPrimitive(first);
        model.addPrimitive(second);
        model.setSelectedPrimitive(first);
        model.togglePrimitiveSelection(second);

        assertTrue(model.glueSelectedPrimitives());
        assertEquals(1, model.getPrimitives().size());
        Polyline polyline = assertInstanceOf(Polyline.class, model.getPrimitives().get(0));
        assertEquals(3, polyline.getVertexCount());
        assertFalse(polyline.isClosed());
        assertEquals(polyline, model.getSelectedPrimitive());
    }

    @Test
    void unglluesPolylineIntoSegments() {
        CadModel model = new CadModel();
        LineStyle style = new LineStyle("Solid", 1.0, null, false, LineType.SOLID);
        Polyline polyline = new Polyline(java.util.List.of(
                new Point(0, 0),
                new Point(10, 0),
                new Point(15, 5)), false, style);

        model.addPrimitive(polyline);
        model.setSelectedPrimitive(polyline);

        assertTrue(model.unglueSelectedPrimitives());
        assertEquals(2, model.getPrimitives().size());
        assertInstanceOf(Segment.class, model.getPrimitives().get(0));
        assertInstanceOf(Segment.class, model.getPrimitives().get(1));
    }
}
