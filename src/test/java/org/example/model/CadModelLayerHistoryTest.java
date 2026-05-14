package org.example.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CadModelLayerHistoryTest {

    @Test
    void hiddenLayerMakesPrimitiveInvisibleToTools() {
        CadModel model = new CadModel();
        Segment segment = new Segment(
                new Point(0, 0),
                new Point(10, 0),
                Segment.CreationMode.CARTESIAN,
                new LineStyle("Solid", 1.0, null, false, LineType.SOLID));

        model.addPrimitive(segment);
        model.assignPrimitiveToLayer(segment, CadModel.LAYER_2);

        assertTrue(model.isPrimitiveLayerVisible(segment));

        model.setLayerVisible(CadModel.LAYER_2, false);

        assertFalse(model.isPrimitiveLayerVisible(segment));
    }

    @Test
    void removingPrimitiveAlsoRemovesDependentLinearDimensionAndUndoRestoresBoth() {
        CadModel model = new CadModel();
        LineStyle style = new LineStyle("Solid", 1.0, null, false, LineType.SOLID);
        Segment segment = new Segment(
                new Point(0, 0),
                new Point(20, 0),
                Segment.CreationMode.CARTESIAN,
                style);
        LinearDimension dimension = new LinearDimension(
                DimensionAnchor.controlPoint(segment, 0, segment.getStartPoint()),
                DimensionAnchor.controlPoint(segment, 1, segment.getEndPoint()),
                new Point(0, 8),
                LinearDimension.Orientation.HORIZONTAL,
                style);

        model.addPrimitive(segment);
        model.addPrimitive(dimension);

        model.removePrimitive(segment);

        assertEquals(0, model.getPrimitives().size());

        assertTrue(model.undo());
        assertEquals(2, model.getPrimitives().size());
        assertTrue(model.getPrimitives().contains(segment));
        assertTrue(model.getPrimitives().contains(dimension));
    }
}
