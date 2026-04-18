package org.example.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LinearDimensionTest {

    private static final double EPS = 1e-9;

    @Test
    void horizontalDimensionUsesCommonYForDimensionLine() {
        LinearDimension dimension = new LinearDimension(
                DimensionAnchor.fixed(new Point(10, 20)),
                DimensionAnchor.fixed(new Point(50, 80)),
                new Point(30, 100),
                LinearDimension.Orientation.HORIZONTAL,
                new LineStyle("test", 1.0, null, false));

        Point start = dimension.getDimensionStart();
        Point end = dimension.getDimensionEnd();

        assertEquals(10.0, start.getX(), EPS);
        assertEquals(50.0, end.getX(), EPS);
        assertEquals(100.0, start.getY(), EPS);
        assertEquals(100.0, end.getY(), EPS);
    }

    @Test
    void verticalDimensionUsesCommonXForDimensionLine() {
        LinearDimension dimension = new LinearDimension(
                DimensionAnchor.fixed(new Point(10, 20)),
                DimensionAnchor.fixed(new Point(50, 80)),
                new Point(-15, 60),
                LinearDimension.Orientation.VERTICAL,
                new LineStyle("test", 1.0, null, false));

        Point start = dimension.getDimensionStart();
        Point end = dimension.getDimensionEnd();

        assertEquals(-15.0, start.getX(), EPS);
        assertEquals(-15.0, end.getX(), EPS);
        assertEquals(20.0, start.getY(), EPS);
        assertEquals(80.0, end.getY(), EPS);
    }
}
