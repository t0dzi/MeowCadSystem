package org.example.view;

import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.TextAlignment;
import org.example.model.*;

import java.util.List;

/**
 * Отвечает также за пересчёт из мировых
 * координат в пиксели.
 * Отвечает за отрисовку всех геометрических примитивов на холсте.
 */
public class CanvasPainter {
    private final GraphicsContext gc;
    private final CadModel model;
    private final AppSettings settings;
    private final CameraModel camera;

    private DrawingState drawingState;

    private SnapPoint currentSnapPoint;

    /** Количество сегментов для аппроксимации кривых */
    private static final int CURVE_SEGMENTS = 64;

    public CanvasPainter(GraphicsContext gc, CadModel model, AppSettings settings, CameraModel camera) {
        this.gc = gc;
        this.model = model;
        this.settings = settings;
        this.camera = camera;
    }

    public void setDrawingState(DrawingState drawingState) {
        this.drawingState = drawingState;
    }

    public void setCurrentSnapPoint(SnapPoint snapPoint) {
        this.currentSnapPoint = snapPoint;
    }

    public Point toScreen(Point worldPoint) {
        double wX = worldPoint.getX();
        double wY = worldPoint.getY();
        double rad = Math.toRadians(camera.getAngle());
        double rx = wX * Math.cos(rad) - wY * Math.sin(rad);
        double ry = wX * Math.sin(rad) + wY * Math.cos(rad);
        rx *= camera.getScale();
        ry *= camera.getScale();
        rx += camera.getX();
        ry += camera.getY();
        double originX = gc.getCanvas().getWidth() / 2;
        double originY = gc.getCanvas().getHeight() / 2;
        return new Point(originX + rx, originY - ry);
    }

    public Point toWorld(double screenX, double screenY) {
        double originX = gc.getCanvas().getWidth() / 2;
        double originY = gc.getCanvas().getHeight() / 2;
        double rx = screenX - originX;
        double ry = originY - screenY;
        rx -= camera.getX();
        ry -= camera.getY();
        rx /= camera.getScale();
        ry /= camera.getScale();
        double rad = Math.toRadians(-camera.getAngle());
        double wX = rx * Math.cos(rad) - ry * Math.sin(rad);
        double wY = rx * Math.sin(rad) + ry * Math.cos(rad);
        return new Point(wX, wY);
    }

    public Point getControlPointScreenPosition(Primitive primitive, ControlPoint controlPoint) {
        if (primitive instanceof DimensionPrimitive dimension
                && controlPoint.getType() == ControlPoint.Type.CONTROL) {
            return getDimensionTextScreenPosition(dimension);
        }
        return toScreen(controlPoint.getPosition());
    }

    public double projectLinearDimensionTextFactor(LinearDimension dimension, double screenX, double screenY) {
        Point dimStartScreen = toScreen(dimension.getDimensionStart());
        Point dimEndScreen = toScreen(dimension.getDimensionEnd());
        Point segment = subtract(dimEndScreen, dimStartScreen);
        double length = distance(dimStartScreen, dimEndScreen);
        if (length < 1e-6) {
            return 0.5;
        }

        Point direction = normalize(segment);
        Point normal = getStableScreenNormal(direction);
        double screenOffset = switch (dimension.getTextPlacement()) {
            case ABOVE_LINE -> dimension.getTextGap();
            case ON_LINE -> 0.0;
            case BELOW_LINE -> -dimension.getTextGap();
        };
        Point textTrackStart = offsetPoint(dimStartScreen, normal, screenOffset);
        double textWidth = getDimensionTextBoundsWithoutPosition(dimension)[2];
        double arrowSize = getArrowScreenSize(dimension.getArrowSize());
        double textGapLength = getDimensionTextGapLength(dimension, direction);
        boolean arrowsOutside = length < textGapLength + arrowSize * 2.6;
        double margin = arrowsOutside ? textWidth / 2.0 : textWidth / 2.0 + arrowSize * 1.1;

        double along = dot(subtract(new Point(screenX, screenY), textTrackStart), direction);
        double minAlong = margin;
        double maxAlong = length - margin;
        if (maxAlong < minAlong) {
            return 0.5;
        }
        return clamp(along, minAlong, maxAlong) / length;
    }

    /**
     * Находит точку на полилинии по дистанции от начала.
     * Возвращает [x, y, nx, ny] где (nx, ny) - нормаль к кривой.
     */
    private double[] pointOnPolyline(List<Point> points, double[] cumLengths, double targetLen) {
        if (targetLen <= 0) {
            Point first = points.get(0);
            Point second = points.size() > 1 ? points.get(1) : points.get(0);
            double dx = second.getX() - first.getX();
            double dy = second.getY() - first.getY();
            double len = Math.sqrt(dx * dx + dy * dy);
            if (len < 1e-9)
                return new double[] { first.getX(), first.getY(), 0, 0 };
            // Нормаль наружу: (-dy, dx) / len, но в экранных координатах Y инвертирован
            return new double[] { first.getX(), first.getY(), dy / len, -dx / len };
        }

        for (int i = 1; i < cumLengths.length; i++) {
            if (cumLengths[i] >= targetLen) {
                double segLen = cumLengths[i] - cumLengths[i - 1];
                if (segLen < 1e-9)
                    continue;
                double t = (targetLen - cumLengths[i - 1]) / segLen;

                Point p0 = points.get(i - 1);
                Point p1 = points.get(i);

                double x = p0.getX() + t * (p1.getX() - p0.getX());
                double y = p0.getY() + t * (p1.getY() - p0.getY());
                double dx = p1.getX() - p0.getX();
                double dy = p1.getY() - p0.getY();
                double len = Math.sqrt(dx * dx + dy * dy);
                if (len < 1e-9)
                    return new double[] { x, y, 0, 0 };
                return new double[] { x, y, dy / len, -dx / len };
            }
        }

        Point last = points.get(points.size() - 1);
        Point prev = points.size() > 1 ? points.get(points.size() - 2) : last;
        double dx = last.getX() - prev.getX();
        double dy = last.getY() - prev.getY();
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-9)
            return new double[] { last.getX(), last.getY(), 0, 0 };
        return new double[] { last.getX(), last.getY(), dy / len, -dx / len };
    }

    public void redrawAll() {
        double width = gc.getCanvas().getWidth();
        double height = gc.getCanvas().getHeight();

        gc.setFill(settings.getBackgroundColor());
        gc.fillRect(0, 0, width, height);

        drawGrid();

        gc.setLineCap(javafx.scene.shape.StrokeLineCap.BUTT);
        gc.setLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);

        for (Primitive primitive : model.getPrimitives()) {
            boolean isSelected = primitive == model.getSelectedPrimitive();
            drawPrimitive(primitive, isSelected);
        }

        if (drawingState != null) {
            drawPreview();
        }

        gc.setLineDashes((double[]) null);
        gc.setLineJoin(javafx.scene.shape.StrokeLineJoin.MITER);
        drawAxes();

        Primitive selected = model.getSelectedPrimitive();
        if (selected != null) {
            drawControlPoints(selected);
        }

        drawCurrentSnapPoint();
    }

    private void drawCurrentSnapPoint() {
        if (currentSnapPoint == null) {
            return;
        }

        gc.setLineDashes((double[]) null);
        gc.setLineJoin(javafx.scene.shape.StrokeLineJoin.MITER);
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.SQUARE);
        gc.setGlobalAlpha(1.0);

        drawSnapPoint(currentSnapPoint);
    }

    private void drawPreview() {
        if (drawingState.getCurrentTool() == DrawingState.Tool.SELECT)
            return;

        List<Point> points = drawingState.getCollectedPoints();
        Point mousePos = drawingState.getCurrentMousePosition();

        if (points.isEmpty() && mousePos == null)
            return;

        gc.setStroke(Color.DODGERBLUE);
        gc.setLineWidth(1.5);
        gc.setLineDashes(5, 5);

        DrawingState.CreationMethod method = drawingState.getCreationMethod();
        if (method == null)
            return;

        switch (method) {
            case SEGMENT_TWO_POINTS -> drawSegmentPreview(points, mousePos);
            case CIRCLE_CENTER_RADIUS, CIRCLE_CENTER_DIAMETER -> drawCircleCenterPreview(points, mousePos);
            case CIRCLE_TWO_POINTS -> drawCircleTwoPointsPreview(points, mousePos);
            case CIRCLE_THREE_POINTS -> drawCircleThreePointsPreview(points, mousePos);
            case ARC_THREE_POINTS -> drawArcThreePointsPreview(points, mousePos);
            case ARC_CENTER_ANGLES -> drawArcCenterPreview(points, mousePos);
            case RECT_TWO_POINTS -> drawRectTwoPointsPreview(points, mousePos);
            case RECT_CORNER_SIZE, RECT_CENTER_SIZE -> drawRectSizePreview(points, mousePos, method);
            case ELLIPSE_CENTER_AXES -> drawEllipsePreview(points, mousePos);
            case POLYGON_CENTER_RADIUS -> drawPolygonPreview(points, mousePos);
            case SPLINE_POINTS -> drawSplinePreview(points, mousePos);
            case DIMENSION_LINEAR_HORIZONTAL, DIMENSION_LINEAR_VERTICAL, DIMENSION_LINEAR_ALIGNED ->
                    drawLinearDimensionPreview(points, mousePos, method);
            case DIMENSION_RADIUS, DIMENSION_DIAMETER -> drawRadialDimensionPreview(points, mousePos, method);
            case DIMENSION_ANGLE -> drawAngularDimensionPreview(points, mousePos);
        }

        drawCollectedPoints(points);

        gc.setLineDashes((double[]) null);
    }

    private void drawSegmentPreview(List<Point> points, Point mousePos) {
        if (points.isEmpty() && mousePos != null)
            return;

        Point start = points.isEmpty() ? mousePos : points.get(0);
        Point end = points.size() > 0 && mousePos != null ? mousePos : (points.size() > 1 ? points.get(1) : start);

        if (start != null && end != null) {
            Point s1 = toScreen(start);
            Point s2 = toScreen(end);
            gc.strokeLine(s1.getX(), s1.getY(), s2.getX(), s2.getY());
        }
    }

    private void drawCircleCenterPreview(List<Point> points, Point mousePos) {
        if (points.isEmpty())
            return;

        Point center = points.get(0);
        Point radiusPoint = mousePos != null ? mousePos : center;
        double radius = distance(center, radiusPoint);

        Point screenCenter = toScreen(center);
        double screenRadius = radius * camera.getScale();

        gc.strokeOval(
                screenCenter.getX() - screenRadius,
                screenCenter.getY() - screenRadius,
                screenRadius * 2,
                screenRadius * 2);

        // Линия радиуса
        Point screenRadiusPoint = toScreen(radiusPoint);
        gc.strokeLine(screenCenter.getX(), screenCenter.getY(),
                screenRadiusPoint.getX(), screenRadiusPoint.getY());
    }

    private void drawCircleTwoPointsPreview(List<Point> points, Point mousePos) {
        if (points.isEmpty())
            return;

        Point p1 = points.get(0);
        Point p2 = mousePos != null ? mousePos : p1;

        double cx = (p1.getX() + p2.getX()) / 2;
        double cy = (p1.getY() + p2.getY()) / 2;
        double radius = distance(p1, p2) / 2;

        Point screenCenter = toScreen(new Point(cx, cy));
        double screenRadius = radius * camera.getScale();

        gc.strokeOval(
                screenCenter.getX() - screenRadius,
                screenCenter.getY() - screenRadius,
                screenRadius * 2,
                screenRadius * 2);
    }

    private void drawCircleThreePointsPreview(List<Point> points, Point mousePos) {
        List<Point> allPoints = new java.util.ArrayList<>(points);
        if (mousePos != null && allPoints.size() < 3) {
            allPoints.add(mousePos);
        }

        if (allPoints.size() < 2)
            return;

        for (int i = 0; i < allPoints.size() - 1; i++) {
            Point s1 = toScreen(allPoints.get(i));
            Point s2 = toScreen(allPoints.get(i + 1));
            gc.strokeLine(s1.getX(), s1.getY(), s2.getX(), s2.getY());
        }

        // Если есть 3 точки, рисуем окружность
        if (allPoints.size() >= 3) {
            Circle preview = Circle.fromThreePoints(allPoints.get(0), allPoints.get(1), allPoints.get(2), null);
            if (preview != null) {
                Point screenCenter = toScreen(preview.getCenter());
                double screenRadius = preview.getRadius() * camera.getScale();
                gc.strokeOval(
                        screenCenter.getX() - screenRadius,
                        screenCenter.getY() - screenRadius,
                        screenRadius * 2,
                        screenRadius * 2);
            }
        }
    }

    private void drawArcThreePointsPreview(List<Point> points, Point mousePos) {
        List<Point> allPoints = new java.util.ArrayList<>(points);
        if (mousePos != null && allPoints.size() < 3) {
            allPoints.add(mousePos);
        }

        if (allPoints.size() < 2)
            return;

        for (int i = 0; i < allPoints.size() - 1; i++) {
            Point s1 = toScreen(allPoints.get(i));
            Point s2 = toScreen(allPoints.get(i + 1));
            gc.strokeLine(s1.getX(), s1.getY(), s2.getX(), s2.getY());
        }

        // Если есть 3 точки, рисуем дугу
        if (allPoints.size() >= 3) {
            Arc preview = Arc.fromThreePoints(allPoints.get(0), allPoints.get(1), allPoints.get(2), null);
            if (preview != null) {
                drawArc(preview);
            }
        }
    }

    private void drawArcCenterPreview(List<Point> points, Point mousePos) {
        if (points.isEmpty())
            return;

        Point center = points.get(0);
        Point screenCenter = toScreen(center);

        if (points.size() == 1 && mousePos != null) {
            // Показываем радиус
            double radius = distance(center, mousePos);
            double screenRadius = radius * camera.getScale();
            gc.strokeOval(
                    screenCenter.getX() - screenRadius,
                    screenCenter.getY() - screenRadius,
                    screenRadius * 2,
                    screenRadius * 2);
            Point screenMouse = toScreen(mousePos);
            gc.strokeLine(screenCenter.getX(), screenCenter.getY(),
                    screenMouse.getX(), screenMouse.getY());
        } else if (points.size() >= 2) {
            double radius = distance(center, points.get(1));
            double startAngle = Math.atan2(
                    points.get(1).getY() - center.getY(),
                    points.get(1).getX() - center.getX());
            Point endPoint = mousePos != null ? mousePos : points.get(1);
            double endAngle = Math.atan2(
                    endPoint.getY() - center.getY(),
                    endPoint.getX() - center.getX());

            Arc preview = new Arc(center, radius, startAngle, endAngle, null);
            drawArc(preview);
        }
    }

    private void drawRectTwoPointsPreview(List<Point> points, Point mousePos) {
        if (points.isEmpty())
            return;

        Point p1 = points.get(0);
        Point p2 = mousePos != null ? mousePos : p1;

        Rectangle preview = Rectangle.fromTwoPoints(p1, p2, null);
        preview.setCornerType(drawingState.getRectangleCornerType());
        preview.setCornerRadius(drawingState.getRectangleCornerRadius());
        drawRectangle(preview);
    }

    private void drawRectSizePreview(List<Point> points, Point mousePos, DrawingState.CreationMethod method) {
        if (points.isEmpty())
            return;

        Point basePoint = points.get(0);
        Point sizePoint = mousePos != null ? mousePos : basePoint;

        double width = Math.abs(sizePoint.getX() - basePoint.getX()) * 2;
        double height = Math.abs(sizePoint.getY() - basePoint.getY()) * 2;

        Rectangle preview;
        if (method == DrawingState.CreationMethod.RECT_CENTER_SIZE) {
            preview = new Rectangle(basePoint, width, height, null);
        } else {
            preview = Rectangle.fromCornerAndSize(basePoint, width, height, null);
        }
        preview.setCornerType(drawingState.getRectangleCornerType());
        preview.setCornerRadius(drawingState.getRectangleCornerRadius());
        drawRectangle(preview);
    }

    private void drawEllipsePreview(List<Point> points, Point mousePos) {
        if (points.isEmpty())
            return;

        Point center = points.get(0);

        if (points.size() == 1) {
            Point axisPoint = mousePos != null ? mousePos : center;
            double semiMajor = distance(center, axisPoint);
            double rotation = Math.atan2(
                    axisPoint.getY() - center.getY(),
                    axisPoint.getX() - center.getX());
            double semiMinor = semiMajor * 0.6;
            Ellipse preview = new Ellipse(center, semiMajor, semiMinor, rotation, null);
            drawEllipse(preview);
        } else if (points.size() >= 2) {
            Point majorPoint = points.get(1);
            double semiMajor = distance(center, majorPoint);
            double rotation = Math.atan2(
                    majorPoint.getY() - center.getY(),
                    majorPoint.getX() - center.getX());
            Point minorPoint = mousePos != null ? mousePos : majorPoint;
            double semiMinor = distance(center, minorPoint);
            Ellipse preview = new Ellipse(center, semiMajor, semiMinor, rotation, null);
            drawEllipse(preview);
        }
    }

    private void drawPolygonPreview(List<Point> points, Point mousePos) {
        if (points.isEmpty())
            return;

        Point center = points.get(0);
        Point vertexPoint = mousePos != null ? mousePos : center;

        double radius = distance(center, vertexPoint);
        double rotation = Math.atan2(
                vertexPoint.getY() - center.getY(),
                vertexPoint.getX() - center.getX());

        Polygon preview = new Polygon(center, radius, drawingState.getPolygonSides(),
                drawingState.getPolygonType(), rotation, null);
        drawPolygon(preview);

        // Рисуем окружность для наглядности разницы вписанный/описанный
        gc.setLineDashes(5, 5);
        Point screenCenter = toScreen(center);
        double screenRadius = radius * camera.getScale();
        gc.strokeOval(
                screenCenter.getX() - screenRadius,
                screenCenter.getY() - screenRadius,
                screenRadius * 2,
                screenRadius * 2);
        gc.setLineDashes((double[]) null);
    }

    private void drawSplinePreview(List<Point> points, Point mousePos) {
        List<Point> allPoints = new java.util.ArrayList<>(points);
        if (mousePos != null) {
            allPoints.add(mousePos);
        }

        if (allPoints.size() < 2) {
            return;
        }

        Spline preview = new Spline(allPoints, null);
        drawSpline(preview);
    }

    private void drawLinearDimension(LinearDimension dimension) {
        Point first = dimension.getFirstPoint();
        Point second = dimension.getSecondPoint();
        Point dimStart = dimension.getDimensionStart();
        Point dimEnd = dimension.getDimensionEnd();
        Point normal = dimension.getNormal();
        Color dimensionColor = resolveDimensionLineColor(dimension);
        Color extensionColor = resolveExtensionLineColor(dimension);

        double sign = Math.signum(dimension.getSignedOffset());
        if (Math.abs(sign) < 1e-9) {
            sign = 1.0;
        }

        Point firstExtStart = new Point(
                first.getX() + normal.getX() * sign * dimension.getExtensionLineOffset(),
                first.getY() + normal.getY() * sign * dimension.getExtensionLineOffset());
        Point secondExtStart = new Point(
                second.getX() + normal.getX() * sign * dimension.getExtensionLineOffset(),
                second.getY() + normal.getY() * sign * dimension.getExtensionLineOffset());
        Point firstExtEnd = new Point(
                dimStart.getX() + normal.getX() * sign * dimension.getExtensionLineOvershoot(),
                dimStart.getY() + normal.getY() * sign * dimension.getExtensionLineOvershoot());
        Point secondExtEnd = new Point(
                dimEnd.getX() + normal.getX() * sign * dimension.getExtensionLineOvershoot(),
                dimEnd.getY() + normal.getY() * sign * dimension.getExtensionLineOvershoot());

        drawWorldLineWithStyle(firstExtStart, firstExtEnd, dimension.getExtensionLineStyle(), extensionColor);
        drawWorldLineWithStyle(secondExtStart, secondExtEnd, dimension.getExtensionLineStyle(), extensionColor);

        Point drawnDimStart = dimension.getRenderedDimensionStart();
        Point drawnDimEnd = dimension.getRenderedDimensionEnd();

        Point dimStartScreen = toScreen(dimStart);
        Point dimEndScreen = toScreen(dimEnd);
        Point drawStartScreen = toScreen(drawnDimStart);
        Point drawEndScreen = toScreen(drawnDimEnd);
        Point direction = normalize(subtract(drawEndScreen, drawStartScreen));
        double dimensionLength = distance(dimStartScreen, dimEndScreen);
        double arrowScreenSize = getArrowScreenSize(dimension.getArrowSize());
        double textGapLength = getDimensionTextGapLength(dimension, direction);
        boolean arrowsOutside = dimensionLength < textGapLength + arrowScreenSize * 2.6;

        Point lineStart = drawStartScreen;
        Point lineEnd = drawEndScreen;
        if (arrowsOutside) {
            lineStart = offsetPoint(drawStartScreen, direction, -arrowScreenSize * 1.8);
            lineEnd = offsetPoint(drawEndScreen, direction, arrowScreenSize * 1.8);
        }

        drawWorldLineWithStyle(screenToWorld(lineStart), screenToWorld(lineEnd), dimension.getLineStyle(), dimensionColor);
        gc.setStroke(dimensionColor);

        if (arrowsOutside) {
            drawArrowScreen(dimStartScreen, lineStart, dimension);
            drawArrowScreen(dimEndScreen, lineEnd, dimension);
        } else {
            drawArrowScreen(dimStartScreen, dimEndScreen, dimension);
            drawArrowScreen(dimEndScreen, dimStartScreen, dimension);
        }

        drawDimensionText(dimension);
    }

    private void drawRadialDimension(RadialDimension dimension) {
        Point centerPoint = dimension.getCenterPoint();
        Point leaderPoint = dimension.getTextPosition();
        Point attachmentPoint = dimension.getAttachmentPoint();
        Color dimensionColor = resolveDimensionLineColor(dimension);

        if (dimension.getKind() == RadialDimension.Kind.DIAMETER) {
            Point oppositePoint = dimension.getOppositeDiameterPoint();
            drawWorldLineWithStyle(oppositePoint, attachmentPoint, dimension.getLineStyle(), dimensionColor);
            drawWorldLineWithStyle(attachmentPoint, leaderPoint, dimension.getLineStyle(), dimensionColor);

            Point oppositeScreen = toScreen(oppositePoint);
            Point centerScreen = toScreen(dimension.getCenterPoint());
            Point attachScreen = toScreen(attachmentPoint);

            gc.setStroke(dimensionColor);
            drawArrowScreen(oppositeScreen, centerScreen, dimension);
            drawArrowScreen(attachScreen, centerScreen, dimension);
        } else {
            drawWorldLineWithStyle(centerPoint, attachmentPoint, dimension.getLineStyle(), dimensionColor);
            drawWorldLineWithStyle(attachmentPoint, leaderPoint, dimension.getLineStyle(), dimensionColor);
            gc.setStroke(dimensionColor);
            drawArrowScreen(toScreen(attachmentPoint), toScreen(centerPoint), dimension);
        }

        if (dimension.getShelfSide() != RadialDimension.ShelfSide.ALONG_LINE) {
            Point leaderScreen = toScreen(leaderPoint);
            Point shelfEndScreen = getRadialShelfEndScreen(dimension, leaderScreen);
            drawWorldLineWithStyle(leaderPoint, screenToWorld(shelfEndScreen), dimension.getLineStyle(), dimensionColor);
        }

        gc.setStroke(dimensionColor);
        drawDimensionText(dimension);
    }

    private void drawAngularDimension(AngularDimension dimension) {
        Point vertex = dimension.getVertexPoint();
        Point arcStart = dimension.getArcStartPoint();
        Point arcEnd = dimension.getArcEndPoint();
        Color dimensionColor = resolveDimensionLineColor(dimension);
        Color extensionColor = resolveExtensionLineColor(dimension);

        Point startDir = normalize(subtract(arcStart, vertex));
        Point endDir = normalize(subtract(arcEnd, vertex));

        Point firstExtEnd = new Point(
                arcStart.getX() + startDir.getX() * dimension.getExtensionLineOvershoot(),
                arcStart.getY() + startDir.getY() * dimension.getExtensionLineOvershoot());
        Point secondExtEnd = new Point(
                arcEnd.getX() + endDir.getX() * dimension.getExtensionLineOvershoot(),
                arcEnd.getY() + endDir.getY() * dimension.getExtensionLineOvershoot());

        drawWorldLineWithStyle(vertex, firstExtEnd, dimension.getExtensionLineStyle(), extensionColor);
        drawWorldLineWithStyle(vertex, secondExtEnd, dimension.getExtensionLineStyle(), extensionColor);

        drawCircularArc(
                vertex,
                dimension.getRadiusValue(),
                dimension.getStartAngle(),
                dimension.getSweepAngle(),
                dimension.getLineStyle(),
                dimensionColor);

        List<Point> arcSamples = sampleAngularArc(dimension, 24);
        if (arcSamples.size() >= 3) {
            gc.setStroke(dimensionColor);
            drawArrowScreen(toScreen(arcSamples.get(0)), toScreen(arcSamples.get(1)), dimension);
            drawArrowScreen(toScreen(arcSamples.get(arcSamples.size() - 1)), toScreen(arcSamples.get(arcSamples.size() - 2)), dimension);
        }

        gc.setStroke(dimensionColor);
        drawDimensionText(dimension);
    }

    private void drawCircularArc(Point center, double radius, double startAngle, double sweepAngle,
            LineStyle style, Color strokeColor) {
        Paint previousStroke = gc.getStroke();
        double previousWidth = gc.getLineWidth();

        if (strokeColor != null) {
            gc.setStroke(strokeColor);
        }

        if (style == null) {
            gc.setLineWidth(1.0);
            gc.setLineDashes((double[]) null);
        } else {
            gc.setLineWidth(style.getThickness());
            applyLineStyle(style);
        }

        if (style != null && (style.getType() == LineType.WAVY || style.getType() == LineType.ZIGZAG)) {
            drawCircularArcStyled(center, radius, startAngle, sweepAngle, style);
        } else {
            Point screenCenter = toScreen(center);
            double screenRadius = radius * camera.getScale();
            double startDeg = Math.toDegrees(startAngle) + camera.getAngle();
            double sweepDeg = Math.toDegrees(sweepAngle);

            gc.strokeArc(
                    screenCenter.getX() - screenRadius,
                    screenCenter.getY() - screenRadius,
                    screenRadius * 2,
                    screenRadius * 2,
                    startDeg,
                    sweepDeg,
                    javafx.scene.shape.ArcType.OPEN);
        }

        gc.setLineDashes((double[]) null);
        gc.setLineWidth(previousWidth);
        gc.setStroke(previousStroke);
    }

    private void drawCircularArcStyled(Point center, double radius, double startAngle, double sweepAngle, LineStyle style) {
        Point screenCenter = toScreen(center);
        double screenRadius = radius * camera.getScale();
        double arcLength = Math.abs(sweepAngle) * screenRadius;

        double startDeg = Math.toDegrees(startAngle) + camera.getAngle();
        double sweepDeg = Math.toDegrees(sweepAngle);
        LineType type = style.getType();

        if (type == LineType.WAVY) {
            double amplitude = style.getWaveAmplitude() * camera.getScale();
            double waveLength = style.getWaveLength() * camera.getScale();

            int numWaves = Math.max(2, (int) Math.round(arcLength / waveLength));
            int steps = numWaves * 24;

            gc.beginPath();
            for (int i = 0; i <= steps; i++) {
                double t = (double) i / steps;
                double waveOffset = amplitude * Math.sin(numWaves * t * 2 * Math.PI);
                double r = screenRadius + waveOffset;
                double angleDeg = startDeg + sweepDeg * t;
                double angleRad = Math.toRadians(angleDeg);
                double x = screenCenter.getX() + r * Math.cos(angleRad);
                double y = screenCenter.getY() - r * Math.sin(angleRad);

                if (i == 0) {
                    gc.moveTo(x, y);
                } else {
                    gc.lineTo(x, y);
                }
            }
            gc.stroke();

        } else if (type == LineType.ZIGZAG) {
            double zigHeight = 8.0 * camera.getScale();
            double zigWidth = 16.0 * camera.getScale();
            int numZigzags = 3;

            double[] params = style.getDashPattern();
            if (params != null && params.length >= 3) {
                zigHeight = params[0] * camera.getScale();
                zigWidth = params[1] * camera.getScale();
                numZigzags = Math.max(1, (int) params[2]);
            }

            int numSamples = 120;
            List<Point> arcPoints = new java.util.ArrayList<>();
            for (int i = 0; i <= numSamples; i++) {
                double t = (double) i / numSamples;
                double angleDeg = startDeg + sweepDeg * t;
                double angleRad = Math.toRadians(angleDeg);
                double x = screenCenter.getX() + screenRadius * Math.cos(angleRad);
                double y = screenCenter.getY() - screenRadius * Math.sin(angleRad);
                arcPoints.add(new Point(x, y));
            }

            double[] cumLengths = new double[arcPoints.size()];
            cumLengths[0] = 0;
            for (int i = 1; i < arcPoints.size(); i++) {
                Point prev = arcPoints.get(i - 1);
                Point curr = arcPoints.get(i);
                double segLen = Math.sqrt(
                        Math.pow(curr.getX() - prev.getX(), 2) +
                                Math.pow(curr.getY() - prev.getY(), 2));
                cumLengths[i] = cumLengths[i - 1] + segLen;
            }
            double totalLength = cumLengths[arcPoints.size() - 1];

            double[] zigzagPositions = new double[numZigzags];
            for (int z = 0; z < numZigzags; z++) {
                double tCenter = (double) (z + 1) / (numZigzags + 1);
                zigzagPositions[z] = tCenter * totalLength;
            }

            List<double[]> resultPoints = new java.util.ArrayList<>();
            resultPoints.add(new double[] { arcPoints.get(0).getX(), arcPoints.get(0).getY() });

            double pathStep = Math.max(4, totalLength / 120);
            double s = pathStep;
            int zigzagIdx = 0;

            while (s < totalLength) {
                if (zigzagIdx < numZigzags) {
                    double nextZigzag = zigzagPositions[zigzagIdx];
                    if (s >= nextZigzag - zigWidth / 2) {
                        double centerDist = nextZigzag;

                        double beforeDist = centerDist - zigWidth / 2;
                        double[] ptBefore = pointOnPolyline(arcPoints, cumLengths, beforeDist);
                        resultPoints.add(new double[] { ptBefore[0], ptBefore[1] });

                        double d1 = centerDist - zigWidth / 4;
                        double[] pt1 = pointOnPolyline(arcPoints, cumLengths, d1);
                        resultPoints.add(new double[] { pt1[0] + pt1[2] * zigHeight, pt1[1] + pt1[3] * zigHeight });

                        double d2 = centerDist + zigWidth / 4;
                        double[] pt2 = pointOnPolyline(arcPoints, cumLengths, d2);
                        resultPoints.add(new double[] { pt2[0] - pt2[2] * zigHeight, pt2[1] - pt2[3] * zigHeight });

                        double afterDist = centerDist + zigWidth / 2;
                        double[] ptAfter = pointOnPolyline(arcPoints, cumLengths, afterDist);
                        resultPoints.add(new double[] { ptAfter[0], ptAfter[1] });

                        s = afterDist + pathStep;
                        zigzagIdx++;
                        continue;
                    }
                }

                double[] pt = pointOnPolyline(arcPoints, cumLengths, s);
                resultPoints.add(new double[] { pt[0], pt[1] });
                s += pathStep;
            }

            Point lastPt = arcPoints.get(arcPoints.size() - 1);
            resultPoints.add(new double[] { lastPt.getX(), lastPt.getY() });

            gc.beginPath();
            gc.moveTo(resultPoints.get(0)[0], resultPoints.get(0)[1]);
            for (int i = 1; i < resultPoints.size(); i++) {
                gc.lineTo(resultPoints.get(i)[0], resultPoints.get(i)[1]);
            }
            gc.stroke();
        }
    }

    private List<Point> sampleAngularArc(AngularDimension dimension, int steps) {
        List<Point> points = new java.util.ArrayList<>();
        Point vertex = dimension.getVertexPoint();
        double radius = dimension.getRadiusValue();
        double start = dimension.getStartAngle();
        double sweep = dimension.getSweepAngle();
        int sampleCount = Math.max(8, steps);

        for (int i = 0; i <= sampleCount; i++) {
            double t = (double) i / sampleCount;
            double angle = start + sweep * t;
            points.add(new Point(
                    vertex.getX() + radius * Math.cos(angle),
                    vertex.getY() + radius * Math.sin(angle)));
        }
        return points;
    }

    private void drawWorldLineWithStyle(Point worldStart, Point worldEnd, LineStyle style) {
        double previousWidth = gc.getLineWidth();
        if (style == null) {
            gc.setLineWidth(1.0);
            gc.setLineDashes((double[]) null);
        } else {
            gc.setLineWidth(style.getThickness());
            applyLineStyle(style);
        }
        drawStyledLine(worldStart, worldEnd, style);
        gc.setLineWidth(previousWidth);
    }

    private void drawWorldLineWithStyle(Point worldStart, Point worldEnd, LineStyle style, Color strokeColor) {
        Paint previousStroke = gc.getStroke();
        if (strokeColor != null) {
            gc.setStroke(strokeColor);
        }
        drawWorldLineWithStyle(worldStart, worldEnd, style);
        gc.setStroke(previousStroke);
    }

    private Color resolveDimensionLineColor(DimensionPrimitive dimension) {
        Color currentStroke = getCurrentStrokeColor();
        if (Color.ORANGERED.equals(currentStroke)) {
            return currentStroke;
        }
        return dimension.getDimensionLineColor() != null ? dimension.getDimensionLineColor() : currentStroke;
    }

    private Color resolveExtensionLineColor(DimensionPrimitive dimension) {
        Color currentStroke = getCurrentStrokeColor();
        if (Color.ORANGERED.equals(currentStroke)) {
            return currentStroke;
        }
        if (dimension.getExtensionLineColor() != null) {
            return dimension.getExtensionLineColor();
        }
        return resolveDimensionLineColor(dimension);
    }

    private Color getCurrentStrokeColor() {
        return gc.getStroke() instanceof Color color ? color : settings.getSegmentColor();
    }

    private void drawDimensionText(DimensionPrimitive dimension) {
        Point screenText = getDimensionTextScreenPosition(dimension);
        String text = dimension.getDisplayText();
        double fontSize = getDimensionFontSize(dimension);
        double angle = getDimensionTextAngle(dimension);
        double widthFactor = dimension.getFontVariant().getWidthFactor();

        gc.save();
        gc.setLineDashes((double[]) null);
        gc.translate(screenText.getX(), screenText.getY());
        gc.rotate(angle);
        gc.scale(widthFactor, 1.0);
        gc.setFont(Font.font("Arial", FontPosture.ITALIC, fontSize));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.setFill(gc.getStroke());
        gc.fillText(text, 0, 0);
        gc.restore();
    }

    private double getDimensionFontSize(DimensionPrimitive dimension) {
        return Math.max(12.0, Math.min(28.0, dimension.getTextHeight()));
    }

    private double[] getDimensionTextBounds(DimensionPrimitive dimension, Point screenText) {
        double fontSize = getDimensionFontSize(dimension);
        double estimatedTextWidth = Math.max(fontSize,
                dimension.getDisplayText().length() * fontSize * 0.62 * dimension.getFontVariant().getWidthFactor());
        double width = estimatedTextWidth + 10.0;
        double height = fontSize * 1.35;
        return new double[] {
                screenText.getX() - width / 2.0,
                screenText.getY() - height / 2.0,
                width,
                height
        };
    }

    private double getDimensionTextGapLength(DimensionPrimitive dimension, Point direction) {
        Point textScreen = getDimensionTextScreenPosition(dimension);
        double[] bounds = getDimensionTextBounds(dimension, textScreen);
        double halfWidth = bounds[2] / 2.0;
        double halfHeight = bounds[3] / 2.0;
        return 2.0 * (Math.abs(direction.getX()) * halfWidth + Math.abs(direction.getY()) * halfHeight) + 8.0;
    }

    private Point getDimensionTextScreenPosition(DimensionPrimitive dimension) {
        if (dimension instanceof LinearDimension linearDimension) {
            return getLinearDimensionTextScreenPosition(linearDimension);
        }

        if (dimension instanceof AngularDimension angularDimension) {
            return getAngularDimensionTextScreenPosition(angularDimension);
        }

        if (dimension instanceof RadialDimension radialDimension) {
            Point leaderScreen = toScreen(radialDimension.getTextPosition());
            if (radialDimension.getShelfSide() == RadialDimension.ShelfSide.ALONG_LINE) {
                Point attachmentScreen = toScreen(radialDimension.getAttachmentPoint());
                Point direction = normalize(subtract(leaderScreen, attachmentScreen));
                Point normal = new Point(-direction.getY(), direction.getX());
                if (normal.getY() > 0) {
                    normal = new Point(-normal.getX(), -normal.getY());
                }
                double offset = switch (radialDimension.getTextPlacement()) {
                    case ABOVE_LINE -> getDimensionFontSize(radialDimension) * 0.45 + radialDimension.getTextGap();
                    case ON_LINE -> 0.0;
                    case BELOW_LINE -> -(getDimensionFontSize(radialDimension) * 0.45 + radialDimension.getTextGap());
                };
                return new Point(
                        leaderScreen.getX() + normal.getX() * offset,
                        leaderScreen.getY() + normal.getY() * offset);
            }

            Point shelfEnd = getRadialShelfEndScreen(radialDimension, leaderScreen);
            Point center = midpointPoint(leaderScreen, shelfEnd);
            double offsetY = switch (radialDimension.getTextPlacement()) {
                case ABOVE_LINE -> -(getDimensionFontSize(radialDimension) * 0.55 + radialDimension.getTextGap());
                case ON_LINE -> 0.0;
                case BELOW_LINE -> getDimensionFontSize(radialDimension) * 0.55 + radialDimension.getTextGap();
            };
            return new Point(center.getX(), center.getY() + offsetY);
        }

        return toScreen(dimension.getTextPosition());
    }

    private Point getLinearDimensionTextScreenPosition(LinearDimension dimension) {
        Point startScreen = toScreen(dimension.getDimensionStart());
        Point endScreen = toScreen(dimension.getDimensionEnd());
        Point directionScreen = normalize(subtract(endScreen, startScreen));
        double lengthScreen = distance(startScreen, endScreen);
        Point anchorScreen = addPoint(
                startScreen,
                scalePoint(directionScreen, lengthScreen * dimension.getTextPositionFactor()));
        Point normalScreen = getStableScreenNormal(directionScreen);
        double screenOffset = switch (dimension.getTextPlacement()) {
            case ABOVE_LINE -> dimension.getTextGap();
            case ON_LINE -> 0.0;
            case BELOW_LINE -> -dimension.getTextGap();
        };
        return offsetPoint(anchorScreen, normalScreen, screenOffset);
    }

    private Point getAngularDimensionTextScreenPosition(AngularDimension dimension) {
        Point vertexScreen = toScreen(dimension.getVertexPoint());
        Point directionScreen = normalize(subtract(toScreen(dimension.getTextPosition()), vertexScreen));
        double radialOffset = switch (dimension.getTextPlacement()) {
            case ABOVE_LINE -> dimension.getTextGap();
            case ON_LINE -> 0.0;
            case BELOW_LINE -> -dimension.getTextGap();
        };

        double textRadiusScreen = dimension.getRadiusValue() * camera.getScale()
                + Math.max(getDimensionFontSize(dimension), getArrowScreenSize(dimension.getArrowSize()) * 1.5)
                + radialOffset;

        return new Point(
                vertexScreen.getX() + directionScreen.getX() * textRadiusScreen,
                vertexScreen.getY() + directionScreen.getY() * textRadiusScreen);
    }

    private Point getStableScreenNormal(Point directionScreen) {
        Point normal = new Point(-directionScreen.getY(), directionScreen.getX());
        boolean shouldFlip = normal.getY() > 1e-9
                || (Math.abs(normal.getY()) <= 1e-9 && normal.getX() < 0.0);
        return shouldFlip ? scalePoint(normal, -1.0) : normal;
    }

    private Point getRadialShelfEndScreen(RadialDimension dimension, Point leaderScreen) {
        Point textScreen = leaderScreen;
        double[] bounds = getDimensionTextBoundsWithoutPosition(dimension);
        double shelfLength = Math.max(bounds[2] + 18.0, getDimensionFontSize(dimension) * 2.2);
        double direction = dimension.getShelfSide() == RadialDimension.ShelfSide.LEFT ? -1.0 : 1.0;
        return new Point(leaderScreen.getX() + direction * shelfLength, leaderScreen.getY());
    }

    private double getDimensionTextAngle(DimensionPrimitive dimension) {
        if (dimension instanceof LinearDimension linearDimension) {
            Point start = toScreen(linearDimension.getRenderedDimensionStart());
            Point end = toScreen(linearDimension.getRenderedDimensionEnd());
            return getReadableScreenAngle(start, end);
        }

        if (dimension instanceof RadialDimension radialDimension) {
            if (radialDimension.getShelfSide() == RadialDimension.ShelfSide.ALONG_LINE) {
                Point start = toScreen(radialDimension.getAttachmentPoint());
                Point end = toScreen(radialDimension.getTextPosition());
                return getReadableScreenAngle(start, end);
            }
            return 0.0;
        }

        if (dimension instanceof AngularDimension angularDimension) {
            Point vertexScreen = toScreen(angularDimension.getVertexPoint());
            Point textScreen = getDimensionTextScreenPosition(angularDimension);
            Point radialDirection = normalize(subtract(textScreen, vertexScreen));
            Point tangentEnd = new Point(
                    textScreen.getX() - radialDirection.getY(),
                    textScreen.getY() + radialDirection.getX());
            return getReadableScreenAngle(textScreen, tangentEnd);
        }

        return 0.0;
    }

    private double getReadableScreenAngle(Point start, Point end) {
        double rawAngle = Math.toDegrees(Math.atan2(end.getY() - start.getY(), end.getX() - start.getX()));
        return normalizeReadableTextAngle(rawAngle);
    }

    private double normalizeReadableTextAngle(double angle) {
        double normalized = angle % 360.0;
        if (normalized > 180.0) {
            normalized -= 360.0;
        } else if (normalized <= -180.0) {
            normalized += 360.0;
        }

        if (normalized > 90.0) {
            normalized -= 180.0;
        } else if (normalized <= -90.0) {
            normalized += 180.0;
        }

        return normalized;
    }

    private double[] getDimensionTextBoundsWithoutPosition(DimensionPrimitive dimension) {
        double fontSize = getDimensionFontSize(dimension);
        double estimatedTextWidth = Math.max(fontSize,
                dimension.getDisplayText().length() * fontSize * 0.62 * dimension.getFontVariant().getWidthFactor());
        return new double[] { 0.0, 0.0, estimatedTextWidth + 10.0, fontSize * 1.35 };
    }

    private double getArrowScreenSize(double arrowSize) {
        return Math.max(8.0, Math.min(18.0, arrowSize));
    }

    private Point offsetPoint(Point point, Point direction, double distance) {
        return new Point(
                point.getX() + direction.getX() * distance,
                point.getY() + direction.getY() * distance);
    }

    private Point midpointPoint(Point first, Point second) {
        return new Point(
                (first.getX() + second.getX()) / 2.0,
                (first.getY() + second.getY()) / 2.0);
    }

    private Point screenToWorld(Point screenPoint) {
        return toWorld(screenPoint.getX(), screenPoint.getY());
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void drawArrowScreen(Point tip, Point toward, DimensionPrimitive dimension) {
        double dx = toward.getX() - tip.getX();
        double dy = toward.getY() - tip.getY();
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < 1e-6) {
            return;
        }

        double size = getArrowScreenSize(dimension.getArrowSize());
        double ux = dx / length;
        double uy = dy / length;
        double px = -uy;
        double py = ux;

        double x1 = tip.getX() + ux * size + px * size * 0.45;
        double y1 = tip.getY() + uy * size + py * size * 0.45;
        double x2 = tip.getX() + ux * size - px * size * 0.45;
        double y2 = tip.getY() + uy * size - py * size * 0.45;

        switch (dimension.getArrowType()) {
            case CLOSED -> {
                if (dimension.isFilledArrows()) {
                    gc.setFill(gc.getStroke());
                    gc.fillPolygon(
                            new double[] { tip.getX(), x1, x2 },
                            new double[] { tip.getY(), y1, y2 },
                            3);
                } else {
                    gc.strokePolygon(
                            new double[] { tip.getX(), x1, x2 },
                            new double[] { tip.getY(), y1, y2 },
                            3);
                }
            }
            case OPEN -> {
                gc.strokeLine(tip.getX(), tip.getY(), x1, y1);
                gc.strokeLine(tip.getX(), tip.getY(), x2, y2);
            }
            case SLASH -> {
                double slashHalf = size * 0.8;
                double centerX = tip.getX() + ux * size * 0.15;
                double centerY = tip.getY() + uy * size * 0.15;
                gc.strokeLine(
                        centerX - ux * slashHalf * 0.35 - px * slashHalf,
                        centerY - uy * slashHalf * 0.35 - py * slashHalf,
                        centerX + ux * slashHalf * 0.35 + px * slashHalf,
                        centerY + uy * slashHalf * 0.35 + py * slashHalf);
            }
            case DOT -> {
                double radius = Math.max(1.8, size * 0.18);
                gc.strokeOval(tip.getX() - radius, tip.getY() - radius, radius * 2.0, radius * 2.0);
            }
        }
    }

    private void drawLinearDimensionPreview(List<Point> points, Point mousePos, DrawingState.CreationMethod method) {
        if (points.size() < 2 || mousePos == null) {
            if (points.size() == 1 && mousePos != null) {
                Point s1 = toScreen(points.get(0));
                Point s2 = toScreen(mousePos);
                gc.strokeLine(s1.getX(), s1.getY(), s2.getX(), s2.getY());
            }
            return;
        }

        LinearDimension.Orientation orientation = switch (method) {
            case DIMENSION_LINEAR_HORIZONTAL -> LinearDimension.Orientation.HORIZONTAL;
            case DIMENSION_LINEAR_VERTICAL -> LinearDimension.Orientation.VERTICAL;
            default -> LinearDimension.Orientation.ALIGNED;
        };

        LinearDimension preview = new LinearDimension(
                DimensionAnchor.fixed(points.get(0)),
                DimensionAnchor.fixed(points.get(1)),
                mousePos,
                orientation,
                model.getCurrentStyle());
        drawLinearDimension(preview);
    }

    private void drawRadialDimensionPreview(List<Point> points, Point mousePos, DrawingState.CreationMethod method) {
        Primitive referenced = drawingState.getReferencePrimitive();
        if (!(referenced instanceof Circle || referenced instanceof Arc) || points.isEmpty() || mousePos == null) {
            return;
        }

        RadialDimension.Kind kind = method == DrawingState.CreationMethod.DIMENSION_DIAMETER
                ? RadialDimension.Kind.DIAMETER
                : RadialDimension.Kind.RADIUS;
        RadialDimension preview = new RadialDimension(referenced, mousePos, kind, model.getCurrentStyle());
        preview.setShelfSide(drawingState.getRadialShelfSide());
        drawRadialDimension(preview);
    }

    private void drawAngularDimensionPreview(List<Point> points, Point mousePos) {
        if (points.isEmpty()) {
            return;
        }

        if (points.size() == 1 && mousePos != null) {
            Point s1 = toScreen(points.get(0));
            Point s2 = toScreen(mousePos);
            gc.strokeLine(s1.getX(), s1.getY(), s2.getX(), s2.getY());
            return;
        }

        if (points.size() == 2 && mousePos != null) {
            Point vertex = toScreen(points.get(0));
            Point first = toScreen(points.get(1));
            Point second = toScreen(mousePos);
            gc.strokeLine(vertex.getX(), vertex.getY(), first.getX(), first.getY());
            gc.strokeLine(vertex.getX(), vertex.getY(), second.getX(), second.getY());
            return;
        }

        if (points.size() >= 3 && mousePos != null) {
            AngularDimension preview = new AngularDimension(
                    DimensionAnchor.fixed(points.get(0)),
                    DimensionAnchor.fixed(points.get(1)),
                    DimensionAnchor.fixed(points.get(2)),
                    mousePos,
                    model.getCurrentStyle());
            drawAngularDimension(preview);
        }
    }

    private void drawCollectedPoints(List<Point> points) {
        gc.setFill(Color.DODGERBLUE);
        double size = 8;

        for (Point p : points) {
            Point screen = toScreen(p);
            gc.fillOval(
                    screen.getX() - size / 2,
                    screen.getY() - size / 2,
                    size, size);
        }
    }

    private double distance(Point p1, Point p2) {
        double dx = p2.getX() - p1.getX();
        double dy = p2.getY() - p1.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private Point subtract(Point p1, Point p2) {
        return new Point(p1.getX() - p2.getX(), p1.getY() - p2.getY());
    }

    private Point addPoint(Point p1, Point p2) {
        return new Point(p1.getX() + p2.getX(), p1.getY() + p2.getY());
    }

    private Point scalePoint(Point point, double factor) {
        return new Point(point.getX() * factor, point.getY() * factor);
    }

    private double dot(Point p1, Point p2) {
        return p1.getX() * p2.getX() + p1.getY() * p2.getY();
    }

    private Point normalize(Point p) {
        double len = Math.sqrt(p.getX() * p.getX() + p.getY() * p.getY());
        if (len < 1e-9) {
            return new Point(1, 0);
        }
        return new Point(p.getX() / len, p.getY() / len);
    }

    private void drawPrimitive(Primitive primitive, boolean isSelected) {
        if (isSelected) {
            gc.setStroke(Color.ORANGERED);
        } else {
            // Используем ACI-цвет, если он задан на примитиве
            Color aciColor = primitive.getColorAci() >= 1
                    ? resolveAciStrokeColor(primitive.getColorAci())
                    : null;
            gc.setStroke(aciColor != null ? aciColor : settings.getSegmentColor());
        }

        LineStyle style = primitive.getLineStyle();
        if (style == null) {
            gc.setLineWidth(1.0);
            gc.setLineDashes((double[]) null);
        } else {
            gc.setLineWidth(style.getThickness());
            applyLineStyle(style);
        }

        switch (primitive.getType()) {
            case POINT   -> drawPoint((PointPrimitive) primitive);
            case SEGMENT -> drawSegment((Segment) primitive);
            case CIRCLE  -> drawCircle((Circle) primitive);
            case ARC     -> drawArc((Arc) primitive);
            case RECTANGLE -> drawRectangle((Rectangle) primitive);
            case ELLIPSE -> drawEllipse((Ellipse) primitive);
            case POLYGON -> drawPolygon((Polygon) primitive);
            case SPLINE  -> drawSpline((Spline) primitive);
            case LINEAR_DIMENSION -> drawLinearDimension((LinearDimension) primitive);
            case RADIAL_DIMENSION -> drawRadialDimension((RadialDimension) primitive);
            case ANGULAR_DIMENSION -> drawAngularDimension((AngularDimension) primitive);
        }
    }

    private Color resolveAciStrokeColor(int aci) {
        if (aci == 7) {
            Color background = settings.getBackgroundColor();
            double luminance = 0.2126 * background.getRed()
                    + 0.7152 * background.getGreen()
                    + 0.0722 * background.getBlue();
            return luminance >= 0.5 ? Color.BLACK : Color.WHITE;
        }
        return org.example.export.DxfImporter.aciToColor(aci);
    }


    private void applyLineStyle(LineStyle style) {
        LineType type = style.getType();

        if (type == LineType.WAVY || type == LineType.ZIGZAG) {
            gc.setLineDashes((double[]) null);
        } else {
            double[] originalPattern = style.getDashPattern();
            if (originalPattern != null) {
                double zoom = camera.getScale();
                double[] scaledPattern = new double[originalPattern.length];
                for (int i = 0; i < originalPattern.length; i++) {
                    scaledPattern[i] = originalPattern[i] * zoom;
                }
                gc.setLineDashes(scaledPattern);
            } else {
                gc.setLineDashes((double[]) null);
            }
        }
    }

    private void drawPoint(PointPrimitive point) {
        Point s = toScreen(point.getPosition());
        double size = 5;
        gc.strokeLine(s.getX() - size, s.getY(), s.getX() + size, s.getY());
        gc.strokeLine(s.getX(), s.getY() - size, s.getX(), s.getY() + size);
    }

    private void drawSegment(Segment segment) {
        LineStyle style = segment.getLineStyle();

        if (style != null && style.getType() == LineType.WAVY) {
            drawWavyLine(segment);
        } else if (style != null && style.getType() == LineType.ZIGZAG) {
            drawZigzagLineGeneric(segment.getStartPoint(), segment.getEndPoint(), style);
        } else {
            drawSimpleLine(segment);
        }
    }

    private void drawSimpleLine(Segment segment) {
        Point s1 = toScreen(segment.getStartPoint());
        Point s2 = toScreen(segment.getEndPoint());
        gc.strokeLine(s1.getX(), s1.getY(), s2.getX(), s2.getY());
    }

    private void drawStyledLine(Point worldStart, Point worldEnd, LineStyle style) {
        if (style == null) {
            Point s1 = toScreen(worldStart);
            Point s2 = toScreen(worldEnd);
            gc.strokeLine(s1.getX(), s1.getY(), s2.getX(), s2.getY());
            return;
        }

        LineType type = style.getType();
        if (type == LineType.WAVY) {
            drawWavyLineGeneric(worldStart, worldEnd, style);
        } else if (type == LineType.ZIGZAG) {
            drawZigzagLineGeneric(worldStart, worldEnd, style);
        } else {
            Point s1 = toScreen(worldStart);
            Point s2 = toScreen(worldEnd);
            gc.strokeLine(s1.getX(), s1.getY(), s2.getX(), s2.getY());
        }
    }

    private void drawWavyLineGeneric(Point worldStart, Point worldEnd, LineStyle style) {
        Point p1 = toScreen(worldStart);
        Point p2 = toScreen(worldEnd);
        double x1 = p1.getX(), y1 = p1.getY();
        double x2 = p2.getX(), y2 = p2.getY();
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.sqrt(dx * dx + dy * dy);

        if (length < 1)
            return;

        double amplitude = 3.0;
        double waveLength = 12.0;

        if (style != null) {
            amplitude = style.getWaveAmplitude();
            waveLength = style.getWaveLength();
        }

        double zoom = camera.getScale();
        double scaledAmp = amplitude * zoom;
        double scaledWaveLen = waveLength * zoom;
        if (scaledWaveLen < 4)
            scaledWaveLen = 4;

        double angle = Math.atan2(dy, dx);
        double perpX = Math.cos(angle + Math.PI / 2);
        double perpY = Math.sin(angle + Math.PI / 2);

        gc.beginPath();
        gc.moveTo(x1, y1);

        int steps = Math.max(20, (int) (length / 2));
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            double baseX = x1 + t * dx;
            double baseY = y1 + t * dy;

            double wavePhase = (t * length / scaledWaveLen) * 2 * Math.PI;
            double offset = scaledAmp * Math.sin(wavePhase);

            double finalX = baseX + offset * perpX;
            double finalY = baseY + offset * perpY;
            gc.lineTo(finalX, finalY);
        }
        gc.stroke();
    }

    private void drawZigzagLineGeneric(Point worldStart, Point worldEnd, LineStyle style) {
        Point p1 = toScreen(worldStart);
        Point p2 = toScreen(worldEnd);
        double x1 = p1.getX(), y1 = p1.getY();
        double x2 = p2.getX(), y2 = p2.getY();
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.sqrt(dx * dx + dy * dy);

        if (length < 5) {
            gc.strokeLine(x1, y1, x2, y2);
            return;
        }

        // Зигзаг по ЕСКД - это линия с изломами в центре
        double zigHeight = 8.0;
        double zigWidth = 10.0;
        int zigCount = 1;

        double[] params = style != null ? style.getDashPattern() : null;
        if (params != null && params.length >= 3) {
            zigHeight = params[0];
            zigWidth = params[1];
            zigCount = (int) params[2];
        }

        double angle = Math.atan2(dy, dx);
        double perpX = -Math.sin(angle);
        double perpY = Math.cos(angle);
        double dirX = Math.cos(angle);
        double dirY = Math.sin(angle);

        drawCenteredKinks(x1, y1, x2, y2, length, zigHeight, zigWidth, zigCount, dirX, dirY, perpX, perpY);
    }

    private void drawCircle(Circle circle) {
        LineStyle style = circle.getLineStyle();

        if (style != null && (style.getType() == LineType.WAVY || style.getType() == LineType.ZIGZAG)) {
            drawCircleStyled(circle, style);
        } else {
            Point center = toScreen(circle.getCenter());
            double screenRadius = circle.getRadius() * camera.getScale();

            gc.strokeOval(
                    center.getX() - screenRadius,
                    center.getY() - screenRadius,
                    screenRadius * 2,
                    screenRadius * 2);
        }
    }

    private void drawCircleStyled(Circle circle, LineStyle style) {
        Point center = circle.getCenter();
        double radius = circle.getRadius();
        Point screenCenter = toScreen(center);
        double screenRadius = radius * camera.getScale();
        double circumference = 2 * Math.PI * screenRadius;

        LineType type = style.getType();

        if (type == LineType.WAVY) {
            // Волнистая окружность - волна идёт вдоль окружности
            double amplitude = style.getWaveAmplitude() * camera.getScale();
            double waveLength = style.getWaveLength() * camera.getScale();

            int numWaves = Math.max(6, (int) Math.round(circumference / waveLength));
            int steps = numWaves * 24; // Много точек для плавности

            gc.beginPath();
            for (int i = 0; i <= steps; i++) {
                double t = (double) i / steps;
                double angle = t * 2 * Math.PI;

                double waveOffset = amplitude * Math.sin(numWaves * angle);
                double r = screenRadius + waveOffset;

                double x = screenCenter.getX() + r * Math.cos(angle);
                double y = screenCenter.getY() - r * Math.sin(angle);

                if (i == 0) {
                    gc.moveTo(x, y);
                } else {
                    gc.lineTo(x, y);
                }
            }
            gc.closePath();
            gc.stroke();

        } else if (type == LineType.ZIGZAG) {
            // Зигзаг на окружности - равномерное распределение по всей длине
            double zigHeight = 8.0 * camera.getScale();
            double zigWidth = 16.0 * camera.getScale();
            int numZigzags = 3;

            double[] params = style.getDashPattern();
            if (params != null && params.length >= 3) {
                zigHeight = params[0] * camera.getScale();
                zigWidth = params[1] * camera.getScale();
                numZigzags = Math.max(1, (int) params[2]);
            }

            double totalLength = 2 * Math.PI * screenRadius;

            // Позиции зигзагов РАВНОМЕРНО по окружности (замкнутый контур!)
            double spacing = totalLength / numZigzags;
            double[] zigzagPositions = new double[numZigzags];
            for (int z = 0; z < numZigzags; z++) {
                // Смещаем на половину интервала, чтобы зигзаги были по центру сегментов
                zigzagPositions[z] = (z + 0.5) * spacing;
            }

            // Генерируем точки окружности с зигзагами
            List<double[]> resultPoints = new java.util.ArrayList<>();
            double pathStep = Math.max(4, totalLength / 180);
            double s = 0;
            int zigzagIdx = 0;

            while (s < totalLength) {
                if (zigzagIdx < numZigzags) {
                    double nextZigzag = zigzagPositions[zigzagIdx];
                    if (s >= nextZigzag - zigWidth / 2) {
                        double centerDist = nextZigzag;

                        double beforeAngle = (centerDist - zigWidth / 2) / screenRadius;
                        resultPoints.add(new double[] {
                                screenCenter.getX() + screenRadius * Math.cos(beforeAngle),
                                screenCenter.getY() - screenRadius * Math.sin(beforeAngle)
                        });

                        double angle1 = (centerDist - zigWidth / 4) / screenRadius;
                        double r1 = screenRadius + zigHeight;
                        resultPoints.add(new double[] {
                                screenCenter.getX() + r1 * Math.cos(angle1),
                                screenCenter.getY() - r1 * Math.sin(angle1)
                        });

                        double angle2 = (centerDist + zigWidth / 4) / screenRadius;
                        double r2 = screenRadius - zigHeight;
                        resultPoints.add(new double[] {
                                screenCenter.getX() + r2 * Math.cos(angle2),
                                screenCenter.getY() - r2 * Math.sin(angle2)
                        });

                        double afterAngle = (centerDist + zigWidth / 2) / screenRadius;
                        resultPoints.add(new double[] {
                                screenCenter.getX() + screenRadius * Math.cos(afterAngle),
                                screenCenter.getY() - screenRadius * Math.sin(afterAngle)
                        });

                        s = centerDist + zigWidth / 2 + pathStep;
                        zigzagIdx++;
                        continue;
                    }
                }

                // Обычная точка на окружности
                double angle = s / screenRadius;
                resultPoints.add(new double[] {
                        screenCenter.getX() + screenRadius * Math.cos(angle),
                        screenCenter.getY() - screenRadius * Math.sin(angle)
                });
                s += pathStep;
            }

            resultPoints.add(new double[] {
                    screenCenter.getX() + screenRadius,
                    screenCenter.getY()
            });

            gc.beginPath();
            if (!resultPoints.isEmpty()) {
                gc.moveTo(resultPoints.get(0)[0], resultPoints.get(0)[1]);
                for (int i = 1; i < resultPoints.size(); i++) {
                    gc.lineTo(resultPoints.get(i)[0], resultPoints.get(i)[1]);
                }
            }
            gc.closePath();
            gc.stroke();
        }
    }

    private void drawArc(Arc arc) {
        LineStyle style = arc.getLineStyle();

        if (style != null && (style.getType() == LineType.WAVY || style.getType() == LineType.ZIGZAG)) {
            drawArcStyled(arc, style);
        } else {
            Point center = toScreen(arc.getCenter());
            double screenRadius = arc.getRadius() * camera.getScale();

            // В экранных координатах (Y вниз) 90° = вверх — совпадает с мировой системой.
            double startDeg = Math.toDegrees(arc.getStartAngle()) + camera.getAngle();
            double sweepDeg = Math.toDegrees(arc.getSweepAngle());

            gc.strokeArc(
                    center.getX() - screenRadius,
                    center.getY() - screenRadius,
                    screenRadius * 2,
                    screenRadius * 2,
                    startDeg,
                    sweepDeg,
                    javafx.scene.shape.ArcType.OPEN);
        }
    }

    private void drawArcStyled(Arc arc, LineStyle style) {
        Point center = arc.getCenter();
        double radius = arc.getRadius();
        double startAngle = arc.getStartAngle();
        double sweepAngle = arc.getSweepAngle();

        Point screenCenter = toScreen(center);
        double screenRadius = radius * camera.getScale();
        double arcLength = sweepAngle * screenRadius;

        double startDeg = Math.toDegrees(startAngle) + camera.getAngle();
        double sweepDeg = Math.toDegrees(sweepAngle);

        LineType type = style.getType();

        if (type == LineType.WAVY) {
            double amplitude = style.getWaveAmplitude() * camera.getScale();
            double waveLength = style.getWaveLength() * camera.getScale();

            int numWaves = Math.max(2, (int) Math.round(arcLength / waveLength));
            int steps = numWaves * 24;

            gc.beginPath();
            for (int i = 0; i <= steps; i++) {
                double t = (double) i / steps;

                double waveOffset = amplitude * Math.sin(numWaves * t * 2 * Math.PI);
                double r = screenRadius + waveOffset;

                // Угол точки на дуге — совпадает с gc.strokeArc(startDeg, sweepDeg)
                double angleDeg = startDeg + sweepDeg * t;
                double angleRad = Math.toRadians(angleDeg);

                // Координаты — Y инвертирован (экранные координаты)
                double x = screenCenter.getX() + r * Math.cos(angleRad);
                double y = screenCenter.getY() - r * Math.sin(angleRad);

                if (i == 0) {
                    gc.moveTo(x, y);
                } else {
                    gc.lineTo(x, y);
                }
            }
            gc.stroke();

        } else if (type == LineType.ZIGZAG) {
            // Зигзаг на дуге - пик наружу + пик внутрь (как у сплайна)
            double zigHeight = 8.0 * camera.getScale();
            double zigWidth = 16.0 * camera.getScale();
            int numZigzags = 3;

            double[] params = style.getDashPattern();
            if (params != null && params.length >= 3) {
                zigHeight = params[0] * camera.getScale();
                zigWidth = params[1] * camera.getScale();
                numZigzags = Math.max(1, (int) params[2]);
            }

            // Генерируем точки дуги как полилинию
            int numSamples = 120;
            List<Point> arcPoints = new java.util.ArrayList<>();
            for (int i = 0; i <= numSamples; i++) {
                double t = (double) i / numSamples;
                double angleDeg = startDeg + sweepDeg * t;
                double angleRad = Math.toRadians(angleDeg);
                double x = screenCenter.getX() + screenRadius * Math.cos(angleRad);
                double y = screenCenter.getY() - screenRadius * Math.sin(angleRad);
                arcPoints.add(new Point(x, y));
            }

            double[] cumLengths = new double[arcPoints.size()];
            cumLengths[0] = 0;
            for (int i = 1; i < arcPoints.size(); i++) {
                Point prev = arcPoints.get(i - 1);
                Point curr = arcPoints.get(i);
                double segLen = Math.sqrt(
                        Math.pow(curr.getX() - prev.getX(), 2) +
                                Math.pow(curr.getY() - prev.getY(), 2));
                cumLengths[i] = cumLengths[i - 1] + segLen;
            }
            double totalLength = cumLengths[arcPoints.size() - 1];

            // Позиции зигзагов равномерно по дуге
            double[] zigzagPositions = new double[numZigzags];
            for (int z = 0; z < numZigzags; z++) {
                double tCenter = (double) (z + 1) / (numZigzags + 1);
                zigzagPositions[z] = tCenter * totalLength;
            }

            List<double[]> resultPoints = new java.util.ArrayList<>();
            double pathStep = Math.max(4, totalLength / 80);
            double s = 0;
            int zigzagIdx = 0;

            while (s < totalLength) {
                if (zigzagIdx < numZigzags) {
                    double nextZigzag = zigzagPositions[zigzagIdx];
                    if (s >= nextZigzag - zigWidth / 2) {
                        double centerDist = nextZigzag;

                        double[] ptBefore = pointOnPolyline(arcPoints, cumLengths, centerDist - zigWidth / 2);
                        resultPoints.add(new double[] { ptBefore[0], ptBefore[1] });

                        double[] pt1 = pointOnPolyline(arcPoints, cumLengths, centerDist - zigWidth / 4);
                        resultPoints.add(new double[] { pt1[0] + pt1[2] * zigHeight, pt1[1] + pt1[3] * zigHeight });

                        double[] pt2 = pointOnPolyline(arcPoints, cumLengths, centerDist + zigWidth / 4);
                        resultPoints.add(new double[] { pt2[0] - pt2[2] * zigHeight, pt2[1] - pt2[3] * zigHeight });

                        double[] ptAfter = pointOnPolyline(arcPoints, cumLengths, centerDist + zigWidth / 2);
                        resultPoints.add(new double[] { ptAfter[0], ptAfter[1] });

                        s = centerDist + zigWidth / 2 + pathStep;
                        zigzagIdx++;
                        continue;
                    }
                }

                double[] pt = pointOnPolyline(arcPoints, cumLengths, s);
                resultPoints.add(new double[] { pt[0], pt[1] });
                s += pathStep;
            }

            Point lastPt = arcPoints.get(arcPoints.size() - 1);
            resultPoints.add(new double[] { lastPt.getX(), lastPt.getY() });

            gc.beginPath();
            if (!resultPoints.isEmpty()) {
                gc.moveTo(resultPoints.get(0)[0], resultPoints.get(0)[1]);
                for (int i = 1; i < resultPoints.size(); i++) {
                    gc.lineTo(resultPoints.get(i)[0], resultPoints.get(i)[1]);
                }
            }
            gc.stroke();
        }
    }

    private void drawRectangle(Rectangle rect) {
        Point[] corners = rect.getCorners();
        LineStyle style = rect.getLineStyle();

        if (rect.getCornerRadius() > 0) {
            drawRoundedRectangle(rect);
        } else if (style != null && style.getType() == LineType.WAVY) {
            drawRectangleWavyConnected(rect, style);
        } else if (style != null && style.getType() == LineType.ZIGZAG) {
            for (int i = 0; i < corners.length; i++) {
                Point start = corners[i];
                Point end = corners[(i + 1) % corners.length];
                drawStyledLine(start, end, style);
            }
        } else {
            gc.beginPath();
            Point first = toScreen(corners[0]);
            gc.moveTo(first.getX(), first.getY());

            for (int i = 1; i < corners.length; i++) {
                Point p = toScreen(corners[i]);
                gc.lineTo(p.getX(), p.getY());
            }
            gc.closePath();
            gc.stroke();
        }
    }

    private void drawRectangleWavyConnected(Rectangle rect, LineStyle style) {
        Point[] corners = rect.getCorners();
        double zoom = camera.getScale();
        double amplitude = style.getWaveAmplitude() * zoom * 0.5;
        double waveLength = style.getWaveLength() * zoom;

        gc.beginPath();
        boolean first = true;

        for (int i = 0; i < corners.length; i++) {
            Point start = corners[i];
            Point end = corners[(i + 1) % corners.length];

            Point screenStart = toScreen(start);
            Point screenEnd = toScreen(end);

            double dx = screenEnd.getX() - screenStart.getX();
            double dy = screenEnd.getY() - screenStart.getY();
            double sideLength = Math.sqrt(dx * dx + dy * dy);

            if (sideLength < 1)
                continue;

            double dirX = dx / sideLength;
            double dirY = dy / sideLength;
            // Перпендикуляр наружу
            double perpX = dirY;
            double perpY = -dirX;

            int numWaves = Math.max(1, (int) Math.round(sideLength / waveLength));
            int steps = Math.max(10, numWaves * 12);

            for (int j = 0; j <= steps; j++) {
                double t = (double) j / steps;
                double baseX = screenStart.getX() + t * dx;
                double baseY = screenStart.getY() + t * dy;

                double wavePhase = t * numWaves * 2 * Math.PI;
                double offset = amplitude * Math.sin(wavePhase);

                double x = baseX + offset * perpX;
                double y = baseY + offset * perpY;

                if (first) {
                    gc.moveTo(x, y);
                    first = false;
                } else {
                    gc.lineTo(x, y);
                }
            }
        }

        gc.closePath();
        gc.stroke();
    }

    private void drawRoundedRectangle(Rectangle rect) {
        Point center = rect.getCenter();
        double halfW = rect.getWidth() / 2;
        double halfH = rect.getHeight() / 2;
        double r = rect.getCornerRadius();

        gc.beginPath();

        Point p;

        p = toScreen(new Point(center.getX() - halfW + r, center.getY() + halfH));
        gc.moveTo(p.getX(), p.getY());
        p = toScreen(new Point(center.getX() + halfW - r, center.getY() + halfH));
        gc.lineTo(p.getX(), p.getY());

        if (rect.getCornerType() == Rectangle.CornerType.ROUNDED) {
            drawCornerArc(center.getX() + halfW - r, center.getY() + halfH - r, r, 90, -90);
        } else {
            p = toScreen(new Point(center.getX() + halfW, center.getY() + halfH - r));
            gc.lineTo(p.getX(), p.getY());
        }

        p = toScreen(new Point(center.getX() + halfW, center.getY() - halfH + r));
        gc.lineTo(p.getX(), p.getY());

        if (rect.getCornerType() == Rectangle.CornerType.ROUNDED) {
            drawCornerArc(center.getX() + halfW - r, center.getY() - halfH + r, r, 0, -90);
        } else {
            p = toScreen(new Point(center.getX() + halfW - r, center.getY() - halfH));
            gc.lineTo(p.getX(), p.getY());
        }

        p = toScreen(new Point(center.getX() - halfW + r, center.getY() - halfH));
        gc.lineTo(p.getX(), p.getY());

        if (rect.getCornerType() == Rectangle.CornerType.ROUNDED) {
            drawCornerArc(center.getX() - halfW + r, center.getY() - halfH + r, r, 270, -90);
        } else {
            p = toScreen(new Point(center.getX() - halfW, center.getY() - halfH + r));
            gc.lineTo(p.getX(), p.getY());
        }

        p = toScreen(new Point(center.getX() - halfW, center.getY() + halfH - r));
        gc.lineTo(p.getX(), p.getY());

        if (rect.getCornerType() == Rectangle.CornerType.ROUNDED) {
            drawCornerArc(center.getX() - halfW + r, center.getY() + halfH - r, r, 180, -90);
        } else {
            p = toScreen(new Point(center.getX() - halfW + r, center.getY() + halfH));
            gc.lineTo(p.getX(), p.getY());
        }

        gc.closePath();
        gc.stroke();
    }

    private void drawCornerArc(double cx, double cy, double r, double startAngle, double sweep) {
        // Аппроксимируем дугу линиями
        int segments = 8;
        double startRad = Math.toRadians(startAngle);
        double sweepRad = Math.toRadians(sweep);

        for (int i = 1; i <= segments; i++) {
            double angle = startRad + sweepRad * i / segments;
            Point p = toScreen(new Point(cx + r * Math.cos(angle), cy + r * Math.sin(angle)));
            gc.lineTo(p.getX(), p.getY());
        }
    }

    private void drawEllipse(Ellipse ellipse) {
        LineStyle style = ellipse.getLineStyle();

        if (style != null && (style.getType() == LineType.WAVY || style.getType() == LineType.ZIGZAG)) {
            drawEllipseStyled(ellipse, style);
        } else {
            // Аппроксимируем эллипс набором точек
            gc.beginPath();

            Point first = toScreen(ellipse.getPointAtAngle(0));
            gc.moveTo(first.getX(), first.getY());

            for (int i = 1; i <= CURVE_SEGMENTS; i++) {
                double t = 2 * Math.PI * i / CURVE_SEGMENTS;
                Point p = toScreen(ellipse.getPointAtAngle(t));
                gc.lineTo(p.getX(), p.getY());
            }

            gc.closePath();
            gc.stroke();
        }
    }

    private void drawEllipseStyled(Ellipse ellipse, LineStyle style) {
        double zoom = camera.getScale();
        LineType type = style.getType();

        if (type == LineType.WAVY) {
            // Волнистая линия на эллипсе - через полилинию для равномерности
            double amplitude = style.getWaveAmplitude() * zoom;
            double waveLength = style.getWaveLength() * zoom;

            // Генерируем точки эллипса как полилинию
            int numSamples = 180;
            List<Point> ellipsePoints = new java.util.ArrayList<>();
            for (int i = 0; i <= numSamples; i++) {
                double angle = (double) i / numSamples * 2 * Math.PI;
                Point worldPoint = ellipse.getPointAtAngle(angle);
                ellipsePoints.add(toScreen(worldPoint));
            }

            double[] cumLengths = new double[ellipsePoints.size()];
            cumLengths[0] = 0;
            for (int i = 1; i < ellipsePoints.size(); i++) {
                Point prev = ellipsePoints.get(i - 1);
                Point curr = ellipsePoints.get(i);
                double segLen = Math.sqrt(
                        Math.pow(curr.getX() - prev.getX(), 2) +
                                Math.pow(curr.getY() - prev.getY(), 2));
                cumLengths[i] = cumLengths[i - 1] + segLen;
            }
            double totalLength = cumLengths[ellipsePoints.size() - 1];

            int numWaves = Math.max(4, (int) Math.round(totalLength / waveLength));

            gc.beginPath();
            double pathStep = Math.max(2, totalLength / 360);
            boolean first = true;

            for (double s = 0; s <= totalLength; s += pathStep) {
                double[] pt = pointOnPolyline(ellipsePoints, cumLengths, s);

                double wavePhase = (s / totalLength) * numWaves * 2 * Math.PI;
                double offset = amplitude * Math.sin(wavePhase);

                double x = pt[0] + offset * pt[2];
                double y = pt[1] + offset * pt[3];

                if (first) {
                    gc.moveTo(x, y);
                    first = false;
                } else {
                    gc.lineTo(x, y);
                }
            }
            gc.closePath();
            gc.stroke();

        } else if (type == LineType.ZIGZAG) {
            // Зигзаг на эллипсе - зеркальное распределение верх/низ
            double zigHeight = 8.0 * zoom;
            double zigWidth = 16.0 * zoom;
            int numZigzags = 1;

            double[] params = style.getDashPattern();
            if (params != null && params.length >= 3) {
                zigHeight = params[0] * zoom;
                zigWidth = params[1] * zoom;
                numZigzags = Math.max(1, (int) params[2]);
            }

            // Генерируем точки эллипса как полилинию
            int numSamples = 180;
            List<Point> ellipsePoints = new java.util.ArrayList<>();
            for (int i = 0; i <= numSamples; i++) {
                double angle = (double) i / numSamples * 2 * Math.PI;
                Point worldPoint = ellipse.getPointAtAngle(angle);
                ellipsePoints.add(toScreen(worldPoint));
            }

            double[] cumLengths = new double[ellipsePoints.size()];
            cumLengths[0] = 0;
            for (int i = 1; i < ellipsePoints.size(); i++) {
                Point prev = ellipsePoints.get(i - 1);
                Point curr = ellipsePoints.get(i);
                double segLen = Math.sqrt(
                        Math.pow(curr.getX() - prev.getX(), 2) +
                                Math.pow(curr.getY() - prev.getY(), 2));
                cumLengths[i] = cumLengths[i - 1] + segLen;
            }
            double totalLength = cumLengths[ellipsePoints.size() - 1];

            int topCount = (numZigzags + 1) / 2; // ceil(n/2)
            int bottomCount = numZigzags / 2; // floor(n/2)

            List<Double> zigzagPositionsList = new java.util.ArrayList<>();

            for (int i = 0; i < topCount; i++) {
                double t = ((i + 0.5) / topCount) * 0.5; // от 0 до 0.5
                zigzagPositionsList.add(t * totalLength);
            }

            for (int i = 0; i < bottomCount; i++) {
                double t = 0.5 + ((i + 0.5) / bottomCount) * 0.5; // от 0.5 до 1.0
                zigzagPositionsList.add(t * totalLength);
            }

            java.util.Collections.sort(zigzagPositionsList);
            double[] zigzagPositions = new double[zigzagPositionsList.size()];
            for (int i = 0; i < zigzagPositionsList.size(); i++) {
                zigzagPositions[i] = zigzagPositionsList.get(i);
            }

            List<double[]> resultPoints = new java.util.ArrayList<>();
            double pathStep = Math.max(4, totalLength / 120);

            double[] pt0 = pointOnPolyline(ellipsePoints, cumLengths, 0);
            resultPoints.add(new double[] { pt0[0], pt0[1] });

            double s = pathStep; // Начинаем с path_step
            int zigzagIdx = 0;
            int totalZigzags = zigzagPositions.length;

            while (s < totalLength) {
                if (zigzagIdx < totalZigzags) {
                    double nextZigzag = zigzagPositions[zigzagIdx];
                    if (s >= nextZigzag - zigWidth / 2) {
                        double centerDist = nextZigzag;

                        double[] ptBefore = pointOnPolyline(ellipsePoints, cumLengths, centerDist - zigWidth / 2);
                        resultPoints.add(new double[] { ptBefore[0], ptBefore[1] });

                        double[] pt1 = pointOnPolyline(ellipsePoints, cumLengths, centerDist - zigWidth / 4);
                        resultPoints.add(new double[] { pt1[0] + pt1[2] * zigHeight, pt1[1] + pt1[3] * zigHeight });

                        double[] pt2 = pointOnPolyline(ellipsePoints, cumLengths, centerDist + zigWidth / 4);
                        resultPoints.add(new double[] { pt2[0] - pt2[2] * zigHeight, pt2[1] - pt2[3] * zigHeight });

                        double[] ptAfter = pointOnPolyline(ellipsePoints, cumLengths, centerDist + zigWidth / 2);
                        resultPoints.add(new double[] { ptAfter[0], ptAfter[1] });

                        s = centerDist + zigWidth / 2 + pathStep;
                        zigzagIdx++;
                        continue;
                    }
                }

                double[] pt = pointOnPolyline(ellipsePoints, cumLengths, s);
                resultPoints.add(new double[] { pt[0], pt[1] });
                s += pathStep;
            }

            if (!resultPoints.isEmpty()) {
                resultPoints.add(new double[] { resultPoints.get(0)[0], resultPoints.get(0)[1] });
            }

            gc.beginPath();
            if (!resultPoints.isEmpty()) {
                gc.moveTo(resultPoints.get(0)[0], resultPoints.get(0)[1]);
                for (int i = 1; i < resultPoints.size(); i++) {
                    gc.lineTo(resultPoints.get(i)[0], resultPoints.get(i)[1]);
                }
            }
            gc.closePath();
            gc.stroke();
        }
    }

    private void drawPolygon(Polygon polygon) {
        Point[] vertices = polygon.getVertices();
        LineStyle style = polygon.getLineStyle();

        if (style != null && (style.getType() == LineType.WAVY || style.getType() == LineType.ZIGZAG)) {
            drawPolygonStyled(polygon, style);
        } else {
            gc.beginPath();
            Point first = toScreen(vertices[0]);
            gc.moveTo(first.getX(), first.getY());

            for (int i = 1; i < vertices.length; i++) {
                Point p = toScreen(vertices[i]);
                gc.lineTo(p.getX(), p.getY());
            }

            gc.closePath();
            gc.stroke();
        }
    }

    private void drawPolygonWavyConnected(Polygon polygon, LineStyle style) {
        Point[] vertices = polygon.getVertices();
        int n = vertices.length;
        double zoom = camera.getScale();
        double amplitude = style.getWaveAmplitude() * zoom * 0.5;
        double waveLength = style.getWaveLength() * zoom;

        gc.beginPath();
        boolean first = true;

        for (int i = 0; i < n; i++) {
            Point start = vertices[i];
            Point end = vertices[(i + 1) % n];

            Point screenStart = toScreen(start);
            Point screenEnd = toScreen(end);

            double dx = screenEnd.getX() - screenStart.getX();
            double dy = screenEnd.getY() - screenStart.getY();
            double sideLength = Math.sqrt(dx * dx + dy * dy);

            if (sideLength < 1)
                continue;

            double dirX = dx / sideLength;
            double dirY = dy / sideLength;
            // Перпендикуляр наружу
            double perpX = dirY;
            double perpY = -dirX;

            int numWaves = Math.max(1, (int) Math.round(sideLength / waveLength));
            int steps = Math.max(10, numWaves * 12);

            for (int j = 0; j <= steps; j++) {
                double t = (double) j / steps;
                double baseX = screenStart.getX() + t * dx;
                double baseY = screenStart.getY() + t * dy;

                double wavePhase = t * numWaves * 2 * Math.PI;
                double offset = amplitude * Math.sin(wavePhase);

                double x = baseX + offset * perpX;
                double y = baseY + offset * perpY;

                if (first) {
                    gc.moveTo(x, y);
                    first = false;
                } else {
                    gc.lineTo(x, y);
                }
            }
        }

        gc.closePath();
        gc.stroke();
    }

    private void drawPolygonStyled(Polygon polygon, LineStyle style) {
        Point[] vertices = polygon.getVertices();
        int n = vertices.length;
        double zoom = camera.getScale();
        LineType type = style.getType();

        if (type == LineType.WAVY) {
            drawPolygonWavyConnected(polygon, style);

        } else if (type == LineType.ZIGZAG) {
            double zigHeight = 8.0 * zoom;
            double zigWidth = 16.0 * zoom;
            int zigzagsPerSide = 1; // Количество зигзагов на каждой стороне

            double[] params = style.getDashPattern();
            if (params != null && params.length >= 3) {
                zigHeight = params[0] * zoom;
                zigWidth = params[1] * zoom;
                zigzagsPerSide = Math.max(1, (int) params[2]);
            }

            gc.beginPath();
            boolean first = true;

            for (int edge = 0; edge < n; edge++) {
                Point start = toScreen(vertices[edge]);
                Point end = toScreen(vertices[(edge + 1) % n]);

                double dx = end.getX() - start.getX();
                double dy = end.getY() - start.getY();
                double edgeLen = Math.sqrt(dx * dx + dy * dy);

                double dirX = dx / edgeLen;
                double dirY = dy / edgeLen;
                // Перпендикуляр наружу
                double perpX = dirY;
                double perpY = -dirX;

                if (first) {
                    gc.moveTo(start.getX(), start.getY());
                    first = false;
                } else {
                    gc.lineTo(start.getX(), start.getY());
                }

                for (int z = 0; z < zigzagsPerSide; z++) {
                    double tCenter = (double) (z + 1) / (zigzagsPerSide + 1);
                    double centerDist = tCenter * edgeLen;

                    if (centerDist - zigWidth / 2 < 0 || centerDist + zigWidth / 2 > edgeLen) {
                        continue; // Зигзаг не помещается
                    }

                    double beforeX = start.getX() + (centerDist - zigWidth / 2) * dirX;
                    double beforeY = start.getY() + (centerDist - zigWidth / 2) * dirY;
                    gc.lineTo(beforeX, beforeY);

                    double peak1X = start.getX() + (centerDist - zigWidth / 4) * dirX + zigHeight * perpX;
                    double peak1Y = start.getY() + (centerDist - zigWidth / 4) * dirY + zigHeight * perpY;
                    gc.lineTo(peak1X, peak1Y);

                    double peak2X = start.getX() + (centerDist + zigWidth / 4) * dirX - zigHeight * perpX;
                    double peak2Y = start.getY() + (centerDist + zigWidth / 4) * dirY - zigHeight * perpY;
                    gc.lineTo(peak2X, peak2Y);

                    double afterX = start.getX() + (centerDist + zigWidth / 2) * dirX;
                    double afterY = start.getY() + (centerDist + zigWidth / 2) * dirY;
                    gc.lineTo(afterX, afterY);
                }

                gc.lineTo(end.getX(), end.getY());
            }

            gc.closePath();
            gc.stroke();
        }
    }

    private void drawSpline(Spline spline) {
        List<Point> splinePoints = spline.getSplinePoints(20);

        if (splinePoints.isEmpty())
            return;

        LineStyle style = spline.getLineStyle();

        if (style != null && style.getType() == LineType.WAVY) {
            drawSplineWavy(splinePoints, style);
        } else if (style != null && style.getType() == LineType.ZIGZAG) {
            drawSplineZigzag(splinePoints, style);
        } else {
            gc.beginPath();
            Point first = toScreen(splinePoints.get(0));
            gc.moveTo(first.getX(), first.getY());

            for (int i = 1; i < splinePoints.size(); i++) {
                Point p = toScreen(splinePoints.get(i));
                gc.lineTo(p.getX(), p.getY());
            }

            gc.stroke();
        }
    }

    private void drawSplineWavy(List<Point> splinePoints, LineStyle style) {
        if (splinePoints.size() < 2)
            return;

        double amplitude = style.getWaveAmplitude();
        double waveLength = style.getWaveLength();
        double zoom = camera.getScale();
        double scaledAmp = amplitude * zoom;
        double scaledWaveLen = waveLength * zoom;
        if (scaledWaveLen < 4)
            scaledWaveLen = 4;

        // Преобразуем в экранные координаты
        List<Point> screenPoints = new java.util.ArrayList<>();
        for (Point p : splinePoints) {
            screenPoints.add(toScreen(p));
        }

        double[] cumLengths = new double[screenPoints.size()];
        cumLengths[0] = 0;
        for (int i = 1; i < screenPoints.size(); i++) {
            Point prev = screenPoints.get(i - 1);
            Point curr = screenPoints.get(i);
            double segLen = Math.sqrt(
                    Math.pow(curr.getX() - prev.getX(), 2) +
                            Math.pow(curr.getY() - prev.getY(), 2));
            cumLengths[i] = cumLengths[i - 1] + segLen;
        }
        double totalLen = cumLengths[screenPoints.size() - 1];

        if (totalLen < 1)
            return;

        int numWaves = Math.max(4, (int) Math.round(totalLen / scaledWaveLen));

        gc.beginPath();
        boolean first = true;
        double step = Math.max(2, totalLen / 200);

        for (double s = 0; s <= totalLen; s += step) {
            double[] pt = pointOnPolyline(screenPoints, cumLengths, s);

            double wavePhase = (s / totalLen) * numWaves * 2 * Math.PI;
            double offset = scaledAmp * Math.sin(wavePhase);

            double finalX = pt[0] + pt[2] * offset;
            double finalY = pt[1] + pt[3] * offset;

            if (first) {
                gc.moveTo(finalX, finalY);
                first = false;
            } else {
                gc.lineTo(finalX, finalY);
            }
        }
        gc.stroke();
    }

    /**
     * Рисует сплайн с зигзагом.
     * Зигзаг состоит из пика вверх и пика вниз (как в ЕСКД).
     */
    private void drawSplineZigzag(List<Point> splinePoints, LineStyle style) {
        if (splinePoints.size() < 2)
            return;

        double zigHeight = 8.0;
        double zigWidth = 16.0;
        int numZigzags = 1;

        double[] params = style.getDashPattern();
        if (params != null && params.length >= 3) {
            zigHeight = params[0];
            zigWidth = params[1];
            numZigzags = Math.max(1, (int) params[2]);
        }

        double zoom = camera.getScale();
        double scaledHeight = zigHeight * zoom;
        double scaledWidth = zigWidth * zoom;

        // Преобразуем все точки в экранные координаты
        List<Point> screenPoints = new java.util.ArrayList<>();
        for (Point p : splinePoints) {
            screenPoints.add(toScreen(p));
        }

        double[] cumLengths = new double[screenPoints.size()];
        cumLengths[0] = 0;
        for (int i = 1; i < screenPoints.size(); i++) {
            Point prev = screenPoints.get(i - 1);
            Point curr = screenPoints.get(i);
            double segLen = Math.sqrt(
                    Math.pow(curr.getX() - prev.getX(), 2) +
                            Math.pow(curr.getY() - prev.getY(), 2));
            cumLengths[i] = cumLengths[i - 1] + segLen;
        }
        double totalLength = cumLengths[screenPoints.size() - 1];

        if (totalLength < scaledWidth) {
            gc.beginPath();
            gc.moveTo(screenPoints.get(0).getX(), screenPoints.get(0).getY());
            for (int i = 1; i < screenPoints.size(); i++) {
                gc.lineTo(screenPoints.get(i).getX(), screenPoints.get(i).getY());
            }
            gc.stroke();
            return;
        }

        double[] zigzagPositions = new double[numZigzags];
        for (int z = 0; z < numZigzags; z++) {
            double tCenter = (double) (z + 1) / (numZigzags + 1);
            zigzagPositions[z] = tCenter * totalLength;
        }

        // Собираем координаты результирующей ломаной
        List<double[]> resultPoints = new java.util.ArrayList<>();

        resultPoints.add(new double[] { screenPoints.get(0).getX(), screenPoints.get(0).getY() });

        double pathStep = Math.max(4, totalLength / 120);
        double s = pathStep;
        int zigzagIdx = 0;

        while (s < totalLength) {
            if (zigzagIdx < numZigzags) {
                double nextZigzag = zigzagPositions[zigzagIdx];
                if (s >= nextZigzag - scaledWidth / 2) {
                    double centerDist = nextZigzag;

                    double beforeDist = centerDist - scaledWidth / 2;
                    double[] ptBefore = pointOnPolyline(screenPoints, cumLengths, beforeDist);
                    resultPoints.add(new double[] { ptBefore[0], ptBefore[1] });

                    double d1 = centerDist - scaledWidth / 4;
                    double[] pt1 = pointOnPolyline(screenPoints, cumLengths, d1);
                    double nx1 = pt1[2], ny1 = pt1[3]; // нормаль
                    resultPoints.add(new double[] { pt1[0] + nx1 * scaledHeight, pt1[1] + ny1 * scaledHeight });

                    double d2 = centerDist + scaledWidth / 4;
                    double[] pt2 = pointOnPolyline(screenPoints, cumLengths, d2);
                    double nx2 = pt2[2], ny2 = pt2[3];
                    resultPoints.add(new double[] { pt2[0] - nx2 * scaledHeight, pt2[1] - ny2 * scaledHeight });

                    double afterDist = centerDist + scaledWidth / 2;
                    double[] ptAfter = pointOnPolyline(screenPoints, cumLengths, afterDist);
                    resultPoints.add(new double[] { ptAfter[0], ptAfter[1] });

                    s = afterDist + pathStep;
                    zigzagIdx++;
                    continue;
                }
            }

            double[] pt = pointOnPolyline(screenPoints, cumLengths, s);
            resultPoints.add(new double[] { pt[0], pt[1] });
            s += pathStep;
        }

        Point lastPt = screenPoints.get(screenPoints.size() - 1);
        resultPoints.add(new double[] { lastPt.getX(), lastPt.getY() });

        gc.beginPath();
        if (!resultPoints.isEmpty()) {
            gc.moveTo(resultPoints.get(0)[0], resultPoints.get(0)[1]);
            for (int i = 1; i < resultPoints.size(); i++) {
                gc.lineTo(resultPoints.get(i)[0], resultPoints.get(i)[1]);
            }
        }
        gc.stroke();
    }

    private void drawControlPoints(Primitive primitive) {
        List<ControlPoint> controlPoints = primitive.getControlPoints();

        for (ControlPoint cp : controlPoints) {
            Point screenPos = getControlPointScreenPosition(primitive, cp);
            double size = 6;

            switch (cp.getType()) {
                case CENTER -> gc.setFill(Color.BLUE);
                case ENDPOINT -> gc.setFill(Color.GREEN);
                case RADIUS, AXIS -> gc.setFill(Color.ORANGE);
                case ANGLE -> gc.setFill(Color.PURPLE);
                case CONTROL -> gc.setFill(Color.RED);
                case CHAMFER -> gc.setFill(Color.CYAN);
                default -> gc.setFill(Color.GRAY);
            }

            gc.fillRect(
                    screenPos.getX() - size / 2,
                    screenPos.getY() - size / 2,
                    size, size);
            gc.setStroke(Color.BLACK);
            gc.setLineWidth(1);
            gc.strokeRect(
                    screenPos.getX() - size / 2,
                    screenPos.getY() - size / 2,
                    size, size);
        }
    }

    private void drawSnapPoint(SnapPoint snap) {
        Point screenPos = toScreen(snap.getPosition());
        double size = 16;

        gc.setLineWidth(3.0);

        switch (snap.getType()) {
            case ENDPOINT -> {
                gc.setStroke(Color.MAGENTA);
                gc.setFill(Color.color(1, 0, 1, 0.3));
                gc.fillRect(
                        screenPos.getX() - size / 2,
                        screenPos.getY() - size / 2,
                        size, size);
                gc.strokeRect(
                        screenPos.getX() - size / 2,
                        screenPos.getY() - size / 2,
                        size, size);
            }
            case MIDPOINT -> {
                gc.setStroke(Color.CYAN);
                gc.setFill(Color.color(0, 1, 1, 0.3));
                double[] xPoints = {
                        screenPos.getX(),
                        screenPos.getX() - size / 2,
                        screenPos.getX() + size / 2
                };
                double[] yPoints = {
                        screenPos.getY() - size / 2,
                        screenPos.getY() + size / 2,
                        screenPos.getY() + size / 2
                };
                gc.fillPolygon(xPoints, yPoints, 3);
                gc.strokePolygon(xPoints, yPoints, 3);
            }
            case CENTER -> {
                gc.setStroke(Color.RED);
                gc.setFill(Color.color(1, 0, 0, 0.3));
                gc.fillOval(
                        screenPos.getX() - size / 2,
                        screenPos.getY() - size / 2,
                        size, size);
                gc.strokeOval(
                        screenPos.getX() - size / 2,
                        screenPos.getY() - size / 2,
                        size, size);
            }
            case INTERSECTION -> {
                gc.setStroke(Color.LIME);
                gc.setFill(Color.color(0, 1, 0, 0.3));
                gc.fillOval(
                        screenPos.getX() - size / 2,
                        screenPos.getY() - size / 2,
                        size, size);
                gc.setLineWidth(3);
                gc.strokeLine(
                        screenPos.getX() - size / 3,
                        screenPos.getY() - size / 3,
                        screenPos.getX() + size / 3,
                        screenPos.getY() + size / 3);
                gc.strokeLine(
                        screenPos.getX() - size / 3,
                        screenPos.getY() + size / 3,
                        screenPos.getX() + size / 3,
                        screenPos.getY() - size / 3);
            }
            case PERPENDICULAR -> {
                // Символ перпендикуляра (оранжевый)
                gc.setStroke(Color.ORANGE);
                gc.setFill(Color.color(1, 0.5, 0, 0.3));
                gc.fillRect(
                        screenPos.getX() - size / 2,
                        screenPos.getY() - size / 2,
                        size, size);
                gc.setLineWidth(3);
                gc.strokeLine(
                        screenPos.getX(),
                        screenPos.getY() - size / 2 + 2,
                        screenPos.getX(),
                        screenPos.getY() + size / 4);
                gc.strokeLine(
                        screenPos.getX() - size / 2 + 2,
                        screenPos.getY() + size / 4,
                        screenPos.getX() + size / 2 - 2,
                        screenPos.getY() + size / 4);
            }
            case TANGENT -> {
                // Круг с касательной (фиолетовый)
                gc.setStroke(Color.VIOLET);
                gc.setFill(Color.color(0.5, 0, 1, 0.3));
                gc.fillOval(
                        screenPos.getX() - size / 2,
                        screenPos.getY() - size / 2,
                        size, size);
                gc.strokeOval(
                        screenPos.getX() - size / 3,
                        screenPos.getY() - size / 3,
                        size * 2 / 3, size * 2 / 3);
                gc.setLineWidth(3);
                gc.strokeLine(
                        screenPos.getX() - size / 2,
                        screenPos.getY() + size / 3,
                        screenPos.getX() + size / 2,
                        screenPos.getY() + size / 3);
            }
        }
    }

    private void drawWavyLine(Segment segment) {
        Point p1 = toScreen(segment.getStartPoint());
        Point p2 = toScreen(segment.getEndPoint());
        double x1 = p1.getX(), y1 = p1.getY();
        double x2 = p2.getX(), y2 = p2.getY();
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.sqrt(dx * dx + dy * dy);

        if (length < 1)
            return;

        double amplitude = 2.5;
        double waveLength = 10.0;

        double[] params = segment.getLineStyle().getDashPattern();
        if (params != null && params.length >= 2) {
            amplitude = params[0];
            waveLength = params[1];
        }

        double zoom = camera.getScale();
        double scaledAmp = amplitude * zoom;
        double scaledWaveLen = waveLength * zoom;
        if (scaledWaveLen < 2)
            scaledWaveLen = 2;

        gc.beginPath();
        gc.moveTo(x1, y1);

        double angle = Math.atan2(dy, dx);

        int numWaves = Math.max(1, (int) (length / scaledWaveLen));
        int pointsTotal = numWaves * 16;

        for (int i = 1; i <= pointsTotal; i++) {
            double t = (double) i / pointsTotal;
            double baseX = x1 + dx * t;
            double baseY = y1 + dy * t;

            double waveOffset = scaledAmp * Math.sin(2 * Math.PI * numWaves * t);

            double offsetX = -waveOffset * Math.sin(angle);
            double offsetY = waveOffset * Math.cos(angle);

            gc.lineTo(baseX + offsetX, baseY + offsetY);
        }

        gc.lineTo(x2, y2);
        gc.stroke();
    }

    private void drawBrokenLine(Segment segment) {
        Point p1 = toScreen(segment.getStartPoint());
        Point p2 = toScreen(segment.getEndPoint());
        double x1 = p1.getX(), y1 = p1.getY();
        double x2 = p2.getX(), y2 = p2.getY();
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.sqrt(dx * dx + dy * dy);

        double breakHeight = 12.0;
        double breakWidth = 10.0;
        int breakCount = 1;

        double[] params = segment.getLineStyle().getDashPattern();
        if (params != null && params.length >= 3) {
            breakHeight = params[0];
            breakWidth = params[1];
            breakCount = (int) params[2];
        }

        if (breakCount < 1) {
            gc.strokeLine(x1, y1, x2, y2);
            return;
        }

        double angle = Math.atan2(dy, dx);
        double dirX = Math.cos(angle);
        double dirY = Math.sin(angle);
        double perpX = -Math.sin(angle);
        double perpY = Math.cos(angle);

        drawCenteredKinks(x1, y1, x2, y2, length, breakHeight, breakWidth, breakCount, dirX, dirY, perpX, perpY);
    }

    private void drawCenteredKinks(double x1, double y1, double x2, double y2, double length,
            double kinkHeight, double kinkWidth, int kinkCount,
            double dirX, double dirY, double perpX, double perpY) {
        double zoom = camera.getScale();
        double scaledHeight = kinkHeight * zoom;
        double scaledWidth = kinkWidth * zoom;
        int requestedKinkCount = Math.max(1, kinkCount);

        if (length < Math.max(4.0, scaledWidth)) {
            gc.strokeLine(x1, y1, x2, y2);
            return;
        }

        double minStraightMargin = Math.min(length * 0.22, Math.max(6.0, scaledWidth * 0.8));
        double usableLength = Math.max(0.0, length - minStraightMargin * 2.0);
        if (usableLength < 4.0) {
            gc.strokeLine(x1, y1, x2, y2);
            return;
        }

        double centerSpacing = requestedKinkCount == 1
                ? 0.0
                : usableLength / requestedKinkCount;

        double effectiveWidth = requestedKinkCount == 1
                ? Math.min(scaledWidth, usableLength * 0.22)
                : Math.min(scaledWidth, centerSpacing * 0.28);
        effectiveWidth = Math.max(1.5, effectiveWidth);

        double densityFactor = 1.0 / Math.sqrt(requestedKinkCount);
        double effectiveHeight = Math.min(scaledHeight * densityFactor, effectiveWidth * 0.9);
        effectiveHeight = Math.max(1.2, effectiveHeight);

        gc.beginPath();
        gc.moveTo(x1, y1);

        double firstCenterOffset = requestedKinkCount == 1
                ? length / 2.0
                : minStraightMargin + centerSpacing * 0.5;
        double firstStartOffset = Math.max(0.0, firstCenterOffset - effectiveWidth);
        gc.lineTo(x1 + firstStartOffset * dirX, y1 + firstStartOffset * dirY);

        for (int i = 0; i < requestedKinkCount; i++) {
            double centerOffset = requestedKinkCount == 1
                    ? length / 2.0
                    : minStraightMargin + centerSpacing * (i + 0.5);
            double cx = x1 + dirX * centerOffset;
            double cy = y1 + dirY * centerOffset;
            drawBreakGeometry(cx, cy, effectiveWidth, effectiveHeight, dirX, dirY, perpX, perpY);
        }

        gc.lineTo(x2, y2);
        gc.stroke();
    }

    private void drawBreakGeometry(double cx, double cy, double w, double h,
            double dirX, double dirY, double perpX, double perpY) {
        double p1x = cx - dirX * w;
        double p1y = cy - dirY * w;
        double p2x = cx - dirX * (w / 3.0) + perpX * h;
        double p2y = cy - dirY * (w / 3.0) + perpY * h;
        double p3x = cx + dirX * (w / 3.0) - perpX * h;
        double p3y = cy + dirY * (w / 3.0) - perpY * h;
        double p4x = cx + dirX * w;
        double p4y = cy + dirY * w;

        gc.lineTo(p1x, p1y);
        gc.lineTo(p2x, p2y);
        gc.lineTo(p3x, p3y);
        gc.lineTo(p4x, p4y);
    }

    private void drawOneBreak(double x1, double y1, double x2, double y2,
            double w, double h, double dirX, double dirY, double perpX, double perpY) {
        double midX = (x1 + x2) / 2.0;
        double midY = (y1 + y2) / 2.0;
        drawBreakGeometry(midX, midY, w, h, dirX, dirY, perpX, perpY);
        gc.lineTo(x2, y2);
    }

    private void drawGrid() {
        int step = settings.getGridStep();
        if (step <= 0)
            return;

        double w = gc.getCanvas().getWidth();
        double h = gc.getCanvas().getHeight();
        Point p1 = toWorld(0, 0);
        Point p2 = toWorld(w, 0);
        Point p3 = toWorld(w, h);
        Point p4 = toWorld(0, h);

        double minX = Math.min(Math.min(p1.getX(), p2.getX()), Math.min(p3.getX(), p4.getX()));
        double maxX = Math.max(Math.max(p1.getX(), p2.getX()), Math.max(p3.getX(), p4.getX()));
        double minY = Math.min(Math.min(p1.getY(), p2.getY()), Math.min(p3.getY(), p4.getY()));
        double maxY = Math.max(Math.max(p1.getY(), p2.getY()), Math.max(p3.getY(), p4.getY()));

        gc.setStroke(settings.getGridColor());
        gc.setLineWidth(0.5);
        gc.setLineDashes((double[]) null);

        double startX = Math.floor(minX / step) * step;
        double startY = Math.floor(minY / step) * step;

        for (double x = startX; x <= maxX; x += step) {
            Point top = toScreen(new Point(x, maxY));
            Point bottom = toScreen(new Point(x, minY));
            gc.strokeLine(top.getX(), top.getY(), bottom.getX(), bottom.getY());
        }
        for (double y = startY; y <= maxY; y += step) {
            Point left = toScreen(new Point(minX, y));
            Point right = toScreen(new Point(maxX, y));
            gc.strokeLine(left.getX(), left.getY(), right.getX(), right.getY());
        }
    }

    private void drawAxes() {
        double w = gc.getCanvas().getWidth();
        double h = gc.getCanvas().getHeight();
        Point worldZero = toScreen(new Point(0, 0));
        double angleRad = Math.toRadians(camera.getAngle());
        double dirXx = Math.cos(angleRad);
        double dirXy = -Math.sin(angleRad);
        double dirYx = -Math.sin(angleRad);
        double dirYy = -Math.cos(angleRad);
        double huge = 20000;

        gc.setLineWidth(1.5);
        gc.setStroke(Color.BLACK);
        gc.setFill(Color.BLACK);
        gc.setLineDashes((double[]) null);
        gc.setFont(new Font("Arial", 16));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);

        gc.strokeLine(worldZero.getX() - dirXx * huge, worldZero.getY() - dirXy * huge,
                worldZero.getX() + dirXx * huge, worldZero.getY() + dirXy * huge);
        gc.strokeLine(worldZero.getX() - dirYx * huge, worldZero.getY() - dirYy * huge,
                worldZero.getX() + dirYx * huge, worldZero.getY() + dirYy * huge);

        drawAxisLabel("X", worldZero, dirXx, dirXy, w, h, 20);
        drawAxisLabel("Y", worldZero, dirYx, dirYy, w, h, 20);
        gc.fillOval(worldZero.getX() - 3, worldZero.getY() - 3, 6, 6);
    }

    private void drawAxisLabel(String text, Point center, double dx, double dy, double w, double h, double sideOffset) {
        double t = Double.MAX_VALUE;
        double padding = 30;

        if (dx > 0.001) {
            double tr = (w - padding - center.getX()) / dx;
            if (tr > 0)
                t = Math.min(t, tr);
        }
        if (dx < -0.001) {
            double tl = (padding - center.getX()) / dx;
            if (tl > 0)
                t = Math.min(t, tl);
        }
        if (dy > 0.001) {
            double tb = (h - padding - center.getY()) / dy;
            if (tb > 0)
                t = Math.min(t, tb);
        }
        if (dy < -0.001) {
            double tt = (padding - center.getY()) / dy;
            if (tt > 0)
                t = Math.min(t, tt);
        }

        if (t < Double.MAX_VALUE) {
            double lx = center.getX() + t * dx + (-dy) * sideOffset;
            double ly = center.getY() + t * dy + (dx) * sideOffset;
            gc.fillText(text, lx, ly);
        }
    }
}
