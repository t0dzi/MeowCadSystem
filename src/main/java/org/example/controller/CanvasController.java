package org.example.controller;

import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.example.model.*;
import org.example.model.DrawingState.CreationMethod;
import org.example.model.DrawingState.Tool;
import org.example.view.CanvasPainter;

import java.util.ArrayList;
import java.util.List;

/**
 * Контроллер холста — связывает ввод пользователя с моделью данных.
 *
 * Отвечает за:
 * - создание примитивов по кликам мыши;
 * - выделение и перетаскивание контрольных точек в режиме SELECT;
 * - панорамирование (средняя кнопка мыши) и масштабирование (колесо);
 * - поворот вида (клавиши Q / E);
 * - удаление примитивов (Delete/Backspace) и отмену рисования (Escape);
 * - завершение сплайна (Enter или двойной клик).
 */
public class CanvasController {
    private final CadModel model;
    private final CanvasPainter painter;
    private final CameraModel camera;
    private final Canvas canvas;
    private final DrawingState drawingState;
    private final Label infoLabel;

    private double lastMouseX;
    private double lastMouseY;
    
    private ControlPoint draggedControlPoint = null;
    private int draggedControlPointIndex = -1;
    
    private int selectedControlPointIndex = -1;

    /**
     * Создаёт контроллер и подключает обработчики событий к холсту.
     *
     * @param canvas       JavaFX-холст для рисования
     * @param model        модель данных чертежа
     * @param painter      отрисовщик холста (используется для перерисовки и конвертации координат)
     * @param infoLabel    статусная строка внизу окна
     * @param camera       модель камеры (зум, панорамирование, угол)
     * @param drawingState текущее состояние инструмента рисования
     */
    public CanvasController(Canvas canvas, CadModel model, CanvasPainter painter,
                            Label infoLabel, CameraModel camera, DrawingState drawingState) {
        this.canvas = canvas;
        this.model = model;
        this.painter = painter;
        this.camera = camera;
        this.drawingState = drawingState;
        this.infoLabel = infoLabel;

        addMouseHandlers(canvas, infoLabel);
        
        drawingState.currentMousePositionProperty().addListener((obs, o, n) -> painter.redrawAll());
        drawingState.getCollectedPoints().addListener((javafx.collections.ListChangeListener<Point>) c -> painter.redrawAll());
        
        drawingState.useManualInputProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                applyManualInput();
            }
        });

        updateInfoLabel();
    }

    public void onKeyPressed(KeyEvent event) {
        if (event.getTarget() instanceof javafx.scene.control.TextField ||
            event.getTarget() instanceof javafx.scene.control.Spinner) {
            return;
        }

        if (event.getCode() == KeyCode.F3) {
            SnapManager snapManager = model.getSnapManager();
            snapManager.setSnapEnabled(!snapManager.isSnapEnabled());
            painter.redrawAll();
        } else if (event.getCode() == KeyCode.M) {
            if (handleDimensionModeToggle()) {
                painter.redrawAll();
                updateInfoLabel();
                return;
            }
        } else if (event.getCode() == KeyCode.Z) {
            if (handleDimensionShelfToggle()) {
                painter.redrawAll();
                updateInfoLabel();
                return;
            }
        } else if (event.getCode() == KeyCode.Q) {
            double delta = event.isShiftDown() ? 15 : 5;
            rotateAroundCenter(delta);
        } else if (event.getCode() == KeyCode.E) {
            double delta = event.isShiftDown() ? -15 : -5;
            rotateAroundCenter(delta);
        } else if (event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) {
            Primitive selected = model.getSelectedPrimitive();
            if (selected != null) {
                if (selected instanceof Spline spline && selectedControlPointIndex >= 0) {
                    if (spline.removeControlPoint(selectedControlPointIndex)) {
                        selectedControlPointIndex = -1;
                        painter.redrawAll();
                        return;
                    }
                }
                model.removePrimitive(selected);
                selectedControlPointIndex = -1;
                painter.redrawAll();
            }
        } else if (event.getCode() == KeyCode.ESCAPE) {
            if (drawingState.isDrawing()) {
                drawingState.reset();
            } else {
                model.setSelectedPrimitive(null);
            }
            painter.redrawAll();
        } else if (event.getCode() == KeyCode.ENTER) {
            if (drawingState.getCurrentTool() == Tool.SPLINE && 
                drawingState.getCollectedPoints().size() >= 2) {
                finishSpline();
            }
        }
        
        updateInfoLabel();
    }

    private void rotateAroundCenter(double angleDelta) {
        double centerX = canvas.getWidth() / 2;
        double centerY = canvas.getHeight() / 2;

        Point worldCenter = painter.toWorld(centerX, centerY);
        double newAngle = camera.getAngle() + angleDelta;
        camera.setAngle(newAngle);

        double rad = Math.toRadians(newAngle);
        double s = camera.getScale();

        double rotX = worldCenter.getX() * Math.cos(rad) - worldCenter.getY() * Math.sin(rad);
        double rotY = worldCenter.getX() * Math.sin(rad) + worldCenter.getY() * Math.cos(rad);

        camera.setX(-rotX * s);
        camera.setY(-rotY * s);
    }

    private void addMouseHandlers(Canvas canvas, Label infoLabel) {
        canvas.setOnMousePressed(this::handleMousePressed);
        canvas.setOnMouseDragged(this::handleMouseDragged);
        canvas.setOnMouseReleased(this::handleMouseReleased);
        canvas.setOnMouseMoved(this::handleMouseMoved);
        canvas.setOnMouseClicked(this::handleMouseClicked);
        canvas.setOnScroll(event -> {
            double zoomFactor = 1.1;
            if (event.getDeltaY() < 0) zoomFactor = 1 / zoomFactor;

            double newScale = camera.getScale() * zoomFactor;
            if (newScale < 0.05) newScale = 0.05;
            if (newScale > 50.0) newScale = 50.0;

            Point mouseWorld = toWorld(event.getX(), event.getY());
            camera.setScale(newScale);

            double halfW = canvas.getWidth() / 2;
            double halfH = canvas.getHeight() / 2;

            double rad = Math.toRadians(camera.getAngle());
            double rotX = mouseWorld.getX() * Math.cos(rad) - mouseWorld.getY() * Math.sin(rad);
            double rotY = mouseWorld.getX() * Math.sin(rad) + mouseWorld.getY() * Math.cos(rad);

            camera.setX(event.getX() - halfW - (rotX * newScale));
            camera.setY((halfH - event.getY()) - (rotY * newScale));

            painter.redrawAll();
            event.consume();
        });
    }

    private void handleMousePressed(MouseEvent event) {
        if (event.getButton() == MouseButton.MIDDLE) {
            lastMouseX = event.getX();
            lastMouseY = event.getY();
            return;
        }

        Point clickPoint = toWorld(event.getX(), event.getY());
        
        SnapManager snapManager = model.getSnapManager();
        SnapPoint snap = snapManager.getCurrentSnap();
        Point effectivePoint = snap != null ? snap.getPosition() : clickPoint;
        
        Tool tool = drawingState.getCurrentTool();
        
        if (tool == Tool.SELECT) {
            handleSelectMode(event, clickPoint);
            return;
        }
        
        if (event.getButton() == MouseButton.PRIMARY) {
            if (tool == Tool.DIMENSION) {
                handleDimensionInput(clickPoint, effectivePoint, snap);
            } else {
                drawingState.addPoint(effectivePoint);
                if (drawingState.hasEnoughPoints()) {
                    createPrimitive();
                }
            }
        }
        
        updateInfoLabel();
    }

    private void handleSelectMode(MouseEvent event, Point clickPoint) {
        Primitive selected = model.getSelectedPrimitive();
        if (selected != null && event.getButton() == MouseButton.PRIMARY) {
            ControlPoint cp = findControlPointAt(selected, event.getX(), event.getY());
            if (cp != null) {
                draggedControlPoint = cp;
                draggedControlPointIndex = cp.getIndex();
                selectedControlPointIndex = cp.getIndex();
                return;
            }
        }
        
        selectedControlPointIndex = -1;
        
        Primitive clickedPrimitive = findPrimitiveAt(clickPoint);
        model.setSelectedPrimitive(clickedPrimitive);
        painter.redrawAll();
    }

    private void handleMouseDragged(MouseEvent event) {
        if (event.getButton() == MouseButton.MIDDLE) {
            double dx = event.getX() - lastMouseX;
            double dy = -(event.getY() - lastMouseY);
            camera.setX(camera.getX() + dx);
            camera.setY(camera.getY() + dy);
            lastMouseX = event.getX();
            lastMouseY = event.getY();
            painter.redrawAll();
            return;
        }
        
        if (draggedControlPoint != null && draggedControlPointIndex >= 0) {
            Primitive selected = model.getSelectedPrimitive();
            if (selected != null) {
                if (selected instanceof LinearDimension linearDimension && draggedControlPointIndex == 3) {
                    linearDimension.setTextPositionFactor(
                            painter.projectLinearDimensionTextFactor(linearDimension, event.getX(), event.getY()));
                    painter.redrawAll();
                    return;
                }
                Point newPos = toWorld(event.getX(), event.getY());
                selected.moveControlPoint(draggedControlPointIndex, newPos);
                painter.redrawAll();
            }
            return;
        }
        
        if (drawingState.isDrawing() || drawingState.getCurrentTool() != Tool.SELECT) {
            drawingState.setCurrentMousePosition(toWorld(event.getX(), event.getY()));
        }
    }

    private void handleMouseReleased(MouseEvent event) {
        if (event.getButton() == MouseButton.MIDDLE) return;
        
        if (draggedControlPoint != null) {
            draggedControlPoint = null;
            draggedControlPointIndex = -1;
            painter.redrawAll();
        }
    }

    private void handleMouseMoved(MouseEvent event) {
        Point worldPos = toWorld(event.getX(), event.getY());
        
        SnapManager snapManager = model.getSnapManager();
        
        // Устанавливаем базовую точку для перпендикуляра/касательной
        List<Point> collectedPoints = drawingState.getCollectedPoints();
        if (!collectedPoints.isEmpty()) {
            snapManager.setBasePoint(collectedPoints.get(0));
        } else {
            snapManager.setBasePoint(null);
        }
        
        SnapPoint snap = snapManager.findSnapPoint(worldPos, camera.getScale());
        painter.setCurrentSnapPoint(snap);
        
        if (drawingState.getCurrentTool() != Tool.SELECT) {
            Point effectivePos = snap != null ? snap.getPosition() : worldPos;
            if (drawingState.getCurrentTool() == Tool.DIMENSION
                    && drawingState.getCreationMethod() == CreationMethod.DIMENSION_ANGLE) {
                effectivePos = assistAngularDimensionPoint(worldPos, effectivePos, snap);
            } else if (drawingState.getCurrentTool() == Tool.DIMENSION) {
                effectivePos = assistLinearDimensionPoint(effectivePos);
            }
            drawingState.setCurrentMousePosition(effectivePos);
        }
        
        updateInfoLabel();
        painter.redrawAll();
    }

    private void handleMouseClicked(MouseEvent event) {
        if (event.getClickCount() == 2 && event.getButton() == MouseButton.PRIMARY) {
            if (drawingState.getCurrentTool() == Tool.SPLINE && 
                drawingState.getCollectedPoints().size() >= 2) {
                finishSpline();
            }
        }
    }

    private void applyManualInput() {
        Tool tool = drawingState.getCurrentTool();
        List<Point> points = drawingState.getCollectedPoints();
        
        LineStyle style = model.getCurrentStyle();
        Primitive primitive = null;
        
        // Получаем центр экрана в мировых координатах для создания без точки
        Point screenCenter = toWorld(canvas.getWidth() / 2, canvas.getHeight() / 2);
        
        switch (tool) {
            case CIRCLE -> {
                double radius = drawingState.getInputRadius();
                if (radius > 0) {
                    Point center = points.isEmpty() ? screenCenter : points.get(0);
                    primitive = new Circle(center, radius, style);
                }
            }
            case ELLIPSE -> {
                double semiMajor = drawingState.getInputSemiMajorAxis();
                double semiMinor = drawingState.getInputSemiMinorAxis();
                if (semiMajor > 0 && semiMinor > 0) {
                    Point center = points.isEmpty() ? screenCenter : points.get(0);
                    double rotation = 0;
                    if (points.size() > 1) {
                        rotation = Math.atan2(
                            points.get(1).getY() - center.getY(),
                            points.get(1).getX() - center.getX()
                        );
                    }
                    primitive = new Ellipse(center, semiMajor, semiMinor, rotation, style);
                }
            }
            case RECTANGLE -> {
                double width = drawingState.getInputWidth();
                double height = drawingState.getInputHeight();
                if (width > 0 && height > 0) {
                    Point basePoint = points.isEmpty() ? screenCenter : points.get(0);
                    Rectangle rect = new Rectangle(basePoint, width, height, style);
                    rect.setCornerType(drawingState.getRectangleCornerType());
                    rect.setCornerRadius(drawingState.getRectangleCornerRadius());
                    primitive = rect;
                }
            }
            case POLYGON -> {
                double radius = drawingState.getInputRadius();
                if (radius > 0) {
                    Point center = points.isEmpty() ? screenCenter : points.get(0);
                    double rotation = 0;
                    if (points.size() > 1) {
                        rotation = Math.atan2(
                            points.get(1).getY() - center.getY(),
                            points.get(1).getX() - center.getX()
                        );
                    }
                    primitive = new Polygon(center, radius, drawingState.getPolygonSides(),
                        drawingState.getPolygonType(), rotation, style);
                }
            }
            case SEGMENT -> {
                double length = drawingState.getInputLength();
                double angle = Math.toRadians(drawingState.getInputAngle());
                if (length > 0) {
                    Point start = points.isEmpty() ? screenCenter : points.get(0);
                    Point end = new Point(
                        start.getX() + length * Math.cos(angle),
                        start.getY() + length * Math.sin(angle)
                    );
                    primitive = new Segment(start, end, Segment.CreationMode.POLAR, style);
                }
            }
            default -> {}
        }
        
        if (primitive != null) {
            model.addPrimitive(primitive);
            drawingState.reset();
            painter.redrawAll();
        }
        
        drawingState.setUseManualInput(false);
    }

    private void createPrimitive() {
        CreationMethod method = drawingState.getCreationMethod();
        List<Point> points = new ArrayList<>(drawingState.getCollectedPoints());
        List<DimensionAnchor> dimensionAnchors = new ArrayList<>(drawingState.getCollectedDimensionAnchors());
        LineStyle style = model.getCurrentStyle();
        
        Primitive primitive = null;
        
        switch (method) {
            case SEGMENT_TWO_POINTS -> {
                if (points.size() >= 2) {
                    primitive = new Segment(points.get(0), points.get(1), 
                        Segment.CreationMode.CARTESIAN, style);
                }
            }
            case CIRCLE_CENTER_RADIUS, CIRCLE_CENTER_DIAMETER -> {
                if (points.size() >= 2) {
                    primitive = Circle.fromCenterAndPoint(points.get(0), points.get(1), style);
                }
            }
            case CIRCLE_TWO_POINTS -> {
                if (points.size() >= 2) {
                    primitive = Circle.fromTwoPoints(points.get(0), points.get(1), style);
                }
            }
            case CIRCLE_THREE_POINTS -> {
                if (points.size() >= 3) {
                    primitive = Circle.fromThreePoints(points.get(0), points.get(1), points.get(2), style);
                }
            }
            case ARC_THREE_POINTS -> {
                if (points.size() >= 3) {
                    primitive = Arc.fromThreePoints(points.get(0), points.get(1), points.get(2), style);
                }
            }
            case ARC_CENTER_ANGLES -> {
                if (points.size() >= 3) {
                    Point center = points.get(0);
                    double radius = distance(center, points.get(1));
                    double startAngle = Math.atan2(
                        points.get(1).getY() - center.getY(),
                        points.get(1).getX() - center.getX()
                    );
                    double endAngle = Math.atan2(
                        points.get(2).getY() - center.getY(),
                        points.get(2).getX() - center.getX()
                    );
                    primitive = new Arc(center, radius, startAngle, endAngle, style);
                }
            }
            case RECT_TWO_POINTS -> {
                if (points.size() >= 2) {
                    Rectangle rect = Rectangle.fromTwoPoints(points.get(0), points.get(1), style);
                    rect.setCornerType(drawingState.getRectangleCornerType());
                    rect.setCornerRadius(drawingState.getRectangleCornerRadius());
                    primitive = rect;
                }
            }
            case RECT_CORNER_SIZE, RECT_CENTER_SIZE -> {
                if (points.size() >= 2) {
                    double width = Math.abs(points.get(1).getX() - points.get(0).getX()) * 2;
                    double height = Math.abs(points.get(1).getY() - points.get(0).getY()) * 2;
                    Rectangle rect;
                    if (method == CreationMethod.RECT_CENTER_SIZE) {
                        rect = new Rectangle(points.get(0), width, height, style);
                    } else {
                        rect = Rectangle.fromCornerAndSize(points.get(0), width, height, style);
                    }
                    rect.setCornerType(drawingState.getRectangleCornerType());
                    rect.setCornerRadius(drawingState.getRectangleCornerRadius());
                    primitive = rect;
                }
            }
            case ELLIPSE_CENTER_AXES -> {
                // Эллипс: центр → большая ось → малая ось (3 точки)
                if (points.size() >= 3) {
                    Point center = points.get(0);
                    double semiMajor = distance(center, points.get(1));
                    double rotation = Math.atan2(
                        points.get(1).getY() - center.getY(),
                        points.get(1).getX() - center.getX()
                    );
                    double semiMinor = distance(center, points.get(2));
                    primitive = new Ellipse(center, semiMajor, semiMinor, rotation, style);
                }
            }
            case POLYGON_CENTER_RADIUS -> {
                if (points.size() >= 2) {
                    Point center = points.get(0);
                    double radius = distance(center, points.get(1));
                    double rotation = Math.atan2(
                        points.get(1).getY() - center.getY(),
                        points.get(1).getX() - center.getX()
                    );
                    primitive = new Polygon(center, radius, drawingState.getPolygonSides(),
                        drawingState.getPolygonType(), rotation, style);
                }
            }
            case DIMENSION_LINEAR_HORIZONTAL, DIMENSION_LINEAR_VERTICAL, DIMENSION_LINEAR_ALIGNED -> {
                if (points.size() >= 3 && dimensionAnchors.size() >= 2) {
                    LinearDimension.Orientation orientation = switch (method) {
                        case DIMENSION_LINEAR_HORIZONTAL -> LinearDimension.Orientation.HORIZONTAL;
                        case DIMENSION_LINEAR_VERTICAL -> LinearDimension.Orientation.VERTICAL;
                        default -> LinearDimension.Orientation.ALIGNED;
                    };
                    primitive = new LinearDimension(
                        dimensionAnchors.get(0),
                        dimensionAnchors.get(1),
                        points.get(2),
                        orientation,
                        style
                    );
                }
            }
            case DIMENSION_RADIUS, DIMENSION_DIAMETER -> {
                Primitive referenced = drawingState.getReferencePrimitive();
                if (points.size() >= 2 && (referenced instanceof Circle || referenced instanceof Arc)) {
                    RadialDimension.Kind kind = method == CreationMethod.DIMENSION_DIAMETER
                        ? RadialDimension.Kind.DIAMETER
                        : RadialDimension.Kind.RADIUS;
                    RadialDimension radialDimension = new RadialDimension(referenced, points.get(1), kind, style);
                    radialDimension.setShelfSide(drawingState.getRadialShelfSide());
                    primitive = radialDimension;
                }
            }
            case DIMENSION_ANGLE -> {
                if (points.size() >= 4 && dimensionAnchors.size() >= 3) {
                    primitive = new AngularDimension(
                        dimensionAnchors.get(0),
                        dimensionAnchors.get(1),
                        dimensionAnchors.get(2),
                        points.get(3),
                        style
                    );
                }
            }
            default -> {}
        }
        
        if (primitive != null) {
            model.addPrimitive(primitive);
        }
        
        drawingState.reset();
        painter.redrawAll();
    }

    private void handleDimensionInput(Point clickPoint, Point effectivePoint, SnapPoint snap) {
        CreationMethod method = drawingState.getCreationMethod();
        if (method == null) {
            return;
        }

        if ((method == CreationMethod.DIMENSION_RADIUS || method == CreationMethod.DIMENSION_DIAMETER)
                && drawingState.getCollectedPoints().isEmpty()) {
            Primitive referenced = findPrimitiveAt(clickPoint);
            if (referenced instanceof Circle || referenced instanceof Arc) {
                drawingState.setReferencePrimitive(referenced);
                drawingState.addPoint(clickPoint);
                if (drawingState.hasEnoughPoints()) {
                    createPrimitive();
                }
            }
            return;
        }

        if (method == CreationMethod.DIMENSION_ANGLE) {
            effectivePoint = assistAngularDimensionPoint(clickPoint, effectivePoint, snap);
        } else {
            effectivePoint = assistLinearDimensionPoint(effectivePoint);
        }

        drawingState.addDimensionPoint(effectivePoint, DimensionAnchor.fromSnapPoint(snap));
        if (drawingState.hasEnoughPoints()) {
            createPrimitive();
        }
    }

    private boolean handleDimensionModeToggle() {
        if (drawingState.getCurrentTool() == Tool.DIMENSION && drawingState.cycleDimensionCreationMethod()) {
            return true;
        }

        Primitive selected = model.getSelectedPrimitive();
        if (selected instanceof LinearDimension linearDimension) {
            LinearDimension.Orientation nextOrientation = switch (linearDimension.getOrientation()) {
                case HORIZONTAL -> LinearDimension.Orientation.VERTICAL;
                case VERTICAL -> LinearDimension.Orientation.ALIGNED;
                case ALIGNED -> LinearDimension.Orientation.HORIZONTAL;
            };
            linearDimension.setOrientation(nextOrientation);
            return true;
        }

        if (selected instanceof RadialDimension radialDimension) {
            RadialDimension.Kind nextKind = radialDimension.getKind() == RadialDimension.Kind.RADIUS
                    ? RadialDimension.Kind.DIAMETER
                    : RadialDimension.Kind.RADIUS;
            radialDimension.setKind(nextKind);
            return true;
        }

        return false;
    }

    private boolean handleDimensionShelfToggle() {
        if (drawingState.getCurrentTool() == Tool.DIMENSION && drawingState.toggleRadialShelfSide()) {
            return true;
        }

        Primitive selected = model.getSelectedPrimitive();
        if (selected instanceof RadialDimension radialDimension) {
            radialDimension.toggleShelfSide();
            return true;
        }

        return false;
    }

    private void finishSpline() {
        List<Point> points = new ArrayList<>(drawingState.getCollectedPoints());
        if (points.size() >= 2) {
            Spline spline = new Spline(points, model.getCurrentStyle());
            model.addPrimitive(spline);
        }
        drawingState.reset();
        painter.redrawAll();
    }


    private Point toWorld(double screenX, double screenY) {
        return painter.toWorld(screenX, screenY);
    }

    private Point assistAngularDimensionPoint(Point rawPoint, Point effectivePoint, SnapPoint snap) {
        if (snap != null) {
            return effectivePoint;
        }

        int collected = drawingState.getCollectedPoints().size();
        if (collected != 1 && collected != 2) {
            return effectivePoint;
        }

        Primitive primitive = findPrimitiveAt(rawPoint);
        if (primitive == null) {
            return effectivePoint;
        }

        Point projected = projectPointToPrimitive(rawPoint, primitive);
        return projected != null ? projected : effectivePoint;
    }

    private Point assistLinearDimensionPoint(Point effectivePoint) {
        CreationMethod method = drawingState.getCreationMethod();
        if (method != CreationMethod.DIMENSION_LINEAR_HORIZONTAL
                && method != CreationMethod.DIMENSION_LINEAR_VERTICAL
                && method != CreationMethod.DIMENSION_LINEAR_ALIGNED) {
            return effectivePoint;
        }

        List<Point> collectedPoints = drawingState.getCollectedPoints();
        if (collectedPoints.size() != 2) {
            return effectivePoint;
        }

        Point currentDirection = getLinearDimensionDirection(collectedPoints.get(0), collectedPoints.get(1), method);
        Point mouseScreen = painter.toScreen(effectivePoint);
        double bestDistance = 12.0;
        Point bestPoint = null;

        for (Primitive primitive : model.getPrimitives()) {
            if (!(primitive instanceof LinearDimension existingDimension)) {
                continue;
            }

            Point existingDirection = normalize(existingDimension.getDirection());
            double alignment = Math.abs(dot(currentDirection, existingDirection));
            if (alignment < 0.995) {
                continue;
            }

            Point lineStart = existingDimension.getDimensionStart();
            Point lineEnd = existingDimension.getDimensionEnd();
            Point projectedScreen = projectPointToInfiniteLine(
                    mouseScreen,
                    painter.toScreen(lineStart),
                    painter.toScreen(lineEnd));

            double screenDistance = distance(mouseScreen, projectedScreen);
            if (screenDistance > bestDistance) {
                continue;
            }

            bestDistance = screenDistance;
            bestPoint = projectPointToInfiniteLine(effectivePoint, lineStart, lineEnd);
        }

        return bestPoint != null ? bestPoint : effectivePoint;
    }

    private Point getLinearDimensionDirection(Point firstPoint, Point secondPoint, CreationMethod method) {
        return switch (method) {
            case DIMENSION_LINEAR_HORIZONTAL -> new Point(1, 0);
            case DIMENSION_LINEAR_VERTICAL -> new Point(0, 1);
            case DIMENSION_LINEAR_ALIGNED -> normalize(new Point(
                    secondPoint.getX() - firstPoint.getX(),
                    secondPoint.getY() - firstPoint.getY()));
            default -> new Point(1, 0);
        };
    }

    private Point projectPointToPrimitive(Point point, Primitive primitive) {
        if (primitive instanceof Segment segment) {
            return projectPointToSegment(point, segment.getStartPoint(), segment.getEndPoint());
        }
        if (primitive instanceof Circle circle) {
            return projectPointToCircle(point, circle.getCenter(), circle.getRadius());
        }
        if (primitive instanceof Arc arc) {
            return projectPointToArc(point, arc);
        }
        if (primitive instanceof Rectangle rectangle) {
            return projectPointToPolylineEdges(point, rectangle.getCorners(), true);
        }
        if (primitive instanceof Polygon polygon) {
            return projectPointToPolylineEdges(point, polygon.getVertices(), true);
        }
        return null;
    }

    private Point projectPointToPolylineEdges(Point point, Point[] vertices, boolean closed) {
        Point bestPoint = null;
        double bestDistance = Double.MAX_VALUE;
        int edgeCount = closed ? vertices.length : vertices.length - 1;

        for (int i = 0; i < edgeCount; i++) {
            Point projected = projectPointToSegment(point, vertices[i], vertices[(i + 1) % vertices.length]);
            double distance = distance(point, projected);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestPoint = projected;
            }
        }

        return bestPoint;
    }

    private Point projectPointToInfiniteLine(Point point, Point lineStart, Point lineEnd) {
        double dx = lineEnd.getX() - lineStart.getX();
        double dy = lineEnd.getY() - lineStart.getY();
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared < 1e-9) {
            return lineStart;
        }

        double t = ((point.getX() - lineStart.getX()) * dx + (point.getY() - lineStart.getY()) * dy) / lengthSquared;
        return new Point(lineStart.getX() + dx * t, lineStart.getY() + dy * t);
    }

    private Point projectPointToSegment(Point point, Point start, Point end) {
        double dx = end.getX() - start.getX();
        double dy = end.getY() - start.getY();
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared < 1e-9) {
            return start;
        }

        double t = ((point.getX() - start.getX()) * dx + (point.getY() - start.getY()) * dy) / lengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));
        return new Point(start.getX() + dx * t, start.getY() + dy * t);
    }

    private Point projectPointToCircle(Point point, Point center, double radius) {
        double dx = point.getX() - center.getX();
        double dy = point.getY() - center.getY();
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < 1e-9) {
            return new Point(center.getX() + radius, center.getY());
        }
        return new Point(center.getX() + dx / length * radius, center.getY() + dy / length * radius);
    }

    private Point projectPointToArc(Point point, Arc arc) {
        Point center = arc.getCenter();
        double angle = Math.atan2(point.getY() - center.getY(), point.getX() - center.getX());
        double clamped = clampAngleToArc(angle, arc.getStartAngle(), arc.getSweepAngle());
        return new Point(
                center.getX() + arc.getRadius() * Math.cos(clamped),
                center.getY() + arc.getRadius() * Math.sin(clamped));
    }

    private double clampAngleToArc(double angle, double startAngle, double sweepAngle) {
        double normalizedAngle = normalizeAngle(angle);
        double normalizedStart = normalizeAngle(startAngle);
        double normalizedEnd = normalizeAngle(startAngle + sweepAngle);

        if (sweepAngle >= 0) {
            if (isAngleBetween(normalizedAngle, normalizedStart, normalizedEnd, true)) {
                return angle;
            }
        } else if (isAngleBetween(normalizedAngle, normalizedEnd, normalizedStart, true)) {
            return angle;
        }

        double startDelta = angularDistance(normalizedAngle, normalizedStart);
        double endDelta = angularDistance(normalizedAngle, normalizedEnd);
        return startDelta <= endDelta ? startAngle : startAngle + sweepAngle;
    }

    private boolean isAngleBetween(double angle, double start, double end, boolean counterClockwise) {
        if (counterClockwise) {
            if (start <= end) {
                return angle >= start && angle <= end;
            }
            return angle >= start || angle <= end;
        }
        if (end <= start) {
            return angle <= start && angle >= end;
        }
        return angle <= start || angle >= end;
    }

    private double angularDistance(double first, double second) {
        double diff = Math.abs(first - second) % (Math.PI * 2.0);
        return Math.min(diff, Math.PI * 2.0 - diff);
    }

    private double normalizeAngle(double angle) {
        double normalized = angle % (Math.PI * 2.0);
        if (normalized < 0) {
            normalized += Math.PI * 2.0;
        }
        return normalized;
    }

    private Primitive findPrimitiveAt(Point worldPoint) {
        // Минимум 5 мировых единиц для выбора маленьких примитивов при большом зуме
        double tolerancePixels = 10.0;
        double toleranceWorld = tolerancePixels / camera.getScale();
        double tolerance = Math.max(toleranceWorld, 5.0);
        
        var primitives = model.getPrimitives();
        for (int i = primitives.size() - 1; i >= 0; i--) {
            Primitive p = primitives.get(i);
            if (p.containsPoint(worldPoint, tolerance)) {
                return p;
            }
        }
        return null;
    }
    
    private ControlPoint findControlPointAt(Primitive primitive, double screenX, double screenY) {
        double tolerance = 8;
        
        for (ControlPoint cp : primitive.getControlPoints()) {
            Point screenPos = painter.getControlPointScreenPosition(primitive, cp);
            double dx = screenX - screenPos.getX();
            double dy = screenY - screenPos.getY();
            if (Math.sqrt(dx * dx + dy * dy) < tolerance) {
                return cp;
            }
        }
        return null;
    }

    /** Евклидово расстояние между двумя точками в мировых координатах. */
    private double distance(Point p1, Point p2) {
        double dx = p2.getX() - p1.getX();
        double dy = p2.getY() - p1.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private double dot(Point p1, Point p2) {
        return p1.getX() * p2.getX() + p1.getY() * p2.getY();
    }

    private Point normalize(Point point) {
        double length = Math.sqrt(point.getX() * point.getX() + point.getY() * point.getY());
        if (length < 1e-9) {
            return new Point(1, 0);
        }
        return new Point(point.getX() / length, point.getY() / length);
    }

    /**
     * Обновляет статусную строку: координаты курсора, текущий масштаб и подсказку по инструменту.
     */
    private void updateInfoLabel() {
        Point mousePos = drawingState.getCurrentMousePosition();
        if (mousePos == null) {
            mousePos = new Point(0, 0);
        }
        
        String hint = drawingState.getHint();
        if (hint == null || hint.isBlank()) {
            hint = "Готово";
        }

        infoLabel.setText(String.format("Курсор: (%.1f, %.1f)    Масштаб: %.2f    Подсказка: %s",
            mousePos.getX(), mousePos.getY(), camera.getScale(), hint));
    }

    public DrawingState getDrawingState() {
        return drawingState;
    }
}
