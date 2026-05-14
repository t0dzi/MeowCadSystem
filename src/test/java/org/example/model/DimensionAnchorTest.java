package org.example.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DimensionAnchorTest {

    @Test
    void parametricSegmentAnchorFollowsSegment() {
        Segment segment = new Segment(
                new Point(0, 0),
                new Point(10, 0),
                Segment.CreationMode.CARTESIAN,
                null);

        DimensionAnchor anchor = DimensionAnchor.parametricSegmentPoint(segment, 0.25, new Point(2.5, 0));
        assertEquals(2.5, anchor.resolve().getX(), 1e-6);
        assertEquals(0.0, anchor.resolve().getY(), 1e-6);

        segment.moveControlPoint(1, new Point(20, 0));
        assertEquals(5.0, anchor.resolve().getX(), 1e-6);
        assertEquals(0.0, anchor.resolve().getY(), 1e-6);
    }

    @Test
    void parametricPolylineAnchorFollowsPolylineEdge() {
        Polyline polyline = new Polyline(List.of(
                new Point(0, 0),
                new Point(10, 0),
                new Point(10, 10)), false, null);

        DimensionAnchor anchor = DimensionAnchor.parametricPolylinePoint(polyline, 1, 0.5, new Point(10, 5));
        assertEquals(10.0, anchor.resolve().getX(), 1e-6);
        assertEquals(5.0, anchor.resolve().getY(), 1e-6);

        polyline.moveControlPoint(2, new Point(10, 20));
        assertEquals(10.0, anchor.resolve().getX(), 1e-6);
        assertEquals(10.0, anchor.resolve().getY(), 1e-6);
    }
}
