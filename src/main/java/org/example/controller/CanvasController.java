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
            drawingState.addPoint(effectivePoint);
            
            if (drawingState.hasEnoughPoints()) {
                createPrimitive();
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
            Point newPos = toWorld(event.getX(), event.getY());
            Primitive selected = model.getSelectedPrimitive();
            if (selected != null) {
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
            default -> {}
        }
        
        if (primitive != null) {
            model.addPrimitive(primitive);
        }
        
        drawingState.reset();
        painter.redrawAll();
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
            Point screenPos = painter.toScreen(cp.getPosition());
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

    /**
     * Обновляет статусную строку: координаты курсора, текущий масштаб и подсказку по инструменту.
     */
    private void updateInfoLabel() {
        Point mousePos = drawingState.getCurrentMousePosition();
        if (mousePos == null) {
            mousePos = new Point(0, 0);
        }
        
        String hint = drawingState.getHint();
        
        infoLabel.setText(String.format("XY: (%.1f, %.1f) | Zoom: %.2f | %s",
            mousePos.getX(), mousePos.getY(), camera.getScale(), hint));
    }

    public DrawingState getDrawingState() {
        return drawingState;
    }
}

