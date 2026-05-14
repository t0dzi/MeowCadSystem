package org.example.model;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Центральная модель данных чертежа.
 *
 * Хранит список всех геометрических примитивов,
 * список слоёв, активный слой, выделенный примитив,
 * текущий стиль линии и менеджер привязок.
 *
 * Архитектурная роль: Model в MVC.
 */
public class CadModel {
    private static final double JOIN_TOLERANCE_SQUARED = 1e-8;
    public static final String LAYER_1 = Layer.DEFAULT_LAYER_NAME;
    public static final String LAYER_2 = "Слой 2";
    public static final String LAYER_3 = "Слой 3";

    private final List<Segment> segments = new ArrayList<>();
    private final ObjectProperty<Segment> selectedSegment = new SimpleObjectProperty<>(null);

    private final List<Primitive> primitives = new ArrayList<>();
    private final ObjectProperty<Primitive> selectedPrimitive = new SimpleObjectProperty<>(null);
    private final ObservableList<Primitive> selectedPrimitives = FXCollections.observableArrayList();

    private final ObjectProperty<LineStyle> currentStyle = new SimpleObjectProperty<>();

    private final SnapManager snapManager;

    private final List<Layer> layers = new ArrayList<>();

    private final StringProperty activeLayerName = new SimpleStringProperty(Layer.DEFAULT_LAYER_NAME);
    private final StringProperty pendingLayerAssignmentName = new SimpleStringProperty(null);
    private final BooleanProperty dirty = new SimpleBooleanProperty(false);
    private final Deque<Runnable> undoStack = new ArrayDeque<>();

    public CadModel() {
        this.snapManager = new SnapManager(this);
        layers.add(Layer.createDefault());
        layers.add(new Layer(LAYER_2, 5, "CONTINUOUS"));
        layers.add(new Layer(LAYER_3, 4, "CONTINUOUS"));
    }

    public void addSegment(Segment segment) {
        segments.add(segment);
    }

    public void removeSegment(Segment segment) {
        if (segment != null) {
            segments.remove(segment);
        }
    }

    public void clearAllSegments() {
        segments.clear();
        setSelectedSegment(null);
    }

    public List<Segment> getSegments() {
        return Collections.unmodifiableList(segments);
    }

    public Segment getSelectedSegment() {
        return selectedSegment.get();
    }

    public void setSelectedSegment(Segment segment) {
        selectedSegment.set(segment);
    }

    public ObjectProperty<Segment> selectedSegmentProperty() {
        return selectedSegment;
    }

    public void addPrimitive(Primitive primitive) {
        if (primitive != null) {
            primitive.setLayerName(activeLayerName.get());
            addPrimitiveInternal(primitive);
            pushUndo(() -> removePrimitiveInternal(primitive));
        }
    }

    /**
     * Добавляет примитив, сохраняя слой, уже установленный на примитиве
     * (без перезаписи активным слоём). Используется при импорте DXF.
     */
    public void addImportedPrimitive(Primitive primitive) {
        if (primitive != null) {
            addPrimitiveInternal(primitive);
            pushUndo(() -> removePrimitiveInternal(primitive));
        }
    }

    public void removePrimitive(Primitive primitive) {
        if (primitive == null) {
            return;
        }

        List<Primitive> removed = collectPrimitiveAndDependentDimensions(primitive);
        if (removed.isEmpty()) {
            return;
        }

        removePrimitivesInternal(removed);
        pushUndo(() -> addPrimitivesInternal(removed));
    }

    public List<Primitive> getPrimitives() {
        return Collections.unmodifiableList(primitives);
    }

    public Primitive getSelectedPrimitive() {
        return selectedPrimitive.get();
    }

    public void setSelectedPrimitive(Primitive primitive) {
        if (primitive == null) {
            clearSelection();
            return;
        }
        selectedPrimitive.set(primitive);
        selectedPrimitives.setAll(primitive);
    }

    public void togglePrimitiveSelection(Primitive primitive) {
        if (primitive == null) {
            return;
        }

        if (selectedPrimitives.contains(primitive)) {
            selectedPrimitives.remove(primitive);
            if (selectedPrimitives.isEmpty()) {
                selectedPrimitive.set(null);
            } else if (selectedPrimitive.get() == primitive) {
                selectedPrimitive.set(selectedPrimitives.get(selectedPrimitives.size() - 1));
            }
            return;
        }

        selectedPrimitives.add(primitive);
        selectedPrimitive.set(primitive);
    }

    public void clearSelection() {
        selectedPrimitives.clear();
        selectedPrimitive.set(null);
    }

    public void selectAll() {
        if (!primitives.isEmpty()) {
            List<Primitive> selectable = primitives.stream()
                    .filter(this::isPrimitiveLayerVisible)
                    .toList();
            selectedPrimitives.setAll(selectable);
            selectedPrimitive.set(selectable.isEmpty() ? null : selectable.get(selectable.size() - 1));
        }
    }

    public List<Primitive> getSelectedPrimitives() {
        return Collections.unmodifiableList(selectedPrimitives);
    }

    public boolean isPrimitiveSelected(Primitive primitive) {
        return selectedPrimitives.contains(primitive);
    }

    public ObjectProperty<Primitive> selectedPrimitiveProperty() {
        return selectedPrimitive;
    }

    public ObjectProperty<LineStyle> currentStyleProperty() {
        return currentStyle;
    }

    public LineStyle getCurrentStyle() {
        return currentStyle.get();
    }

    public void setCurrentStyle(LineStyle style) {
        currentStyle.set(style);
    }

    public SnapManager getSnapManager() {
        return snapManager;
    }

    public List<Layer> getLayers() {
        return Collections.unmodifiableList(layers);
    }

    public Layer getLayer(String name) {
        for (Layer layer : layers) {
            if (layer.getName().equals(name)) {
                return layer;
            }
        }
        return null;
    }

    public void addLayer(Layer layer) {
        if (layer != null && getLayer(layer.getName()) == null) {
            layers.add(layer);
            dirty.set(true);
        }
    }

    public void removeLayer(String name) {
        if (Layer.DEFAULT_LAYER_NAME.equals(name))
            return;

        Layer toRemove = getLayer(name);
        if (toRemove != null) {
            layers.remove(toRemove);
            for (Primitive p : primitives) {
                if (name.equals(p.getLayerName())) {
                    p.setLayerName(Layer.DEFAULT_LAYER_NAME);
                }
            }
            if (name.equals(activeLayerName.get())) {
                activeLayerName.set(Layer.DEFAULT_LAYER_NAME);
            }
            dirty.set(true);
        }
    }

    public String getActiveLayerName() {
        return activeLayerName.get();
    }

    public void setActiveLayerName(String name) {
        if (getLayer(name) != null) {
            activeLayerName.set(name);
        }
    }

    public StringProperty activeLayerNameProperty() {
        return activeLayerName;
    }

    public String getPendingLayerAssignmentName() {
        return pendingLayerAssignmentName.get();
    }

    public void armLayerAssignment(String layerName) {
        pendingLayerAssignmentName.set(getLayer(layerName) != null ? layerName : null);
        setActiveLayerName(layerName);
    }

    public void clearPendingLayerAssignment() {
        pendingLayerAssignmentName.set(null);
    }

    public StringProperty pendingLayerAssignmentNameProperty() {
        return pendingLayerAssignmentName;
    }

    public boolean assignPrimitiveToLayer(Primitive primitive, String layerName) {
        if (primitive == null || getLayer(layerName) == null) {
            return false;
        }
        String oldLayer = primitive.getLayerName();
        if (layerName.equals(oldLayer)) {
            return false;
        }
        primitive.setLayerName(layerName);
        pushUndo(() -> primitive.setLayerName(oldLayer));
        return true;
    }

    public boolean movePrimitiveToDefaultLayer(Primitive primitive) {
        return assignPrimitiveToLayer(primitive, Layer.DEFAULT_LAYER_NAME);
    }

    public void setLayerVisible(String layerName, boolean visible) {
        Layer layer = getLayer(layerName);
        if (layer == null || layer.isVisible() == visible) {
            return;
        }
        layer.setVisible(visible);
        clearSelectionOfHiddenPrimitives();
        dirty.set(true);
    }

    public boolean isPrimitiveLayerVisible(Primitive primitive) {
        if (primitive == null) {
            return false;
        }
        Layer layer = getLayer(primitive.getLayerName());
        return layer == null || layer.isVisible();
    }

    public boolean isPrimitiveLayerLocked(Primitive primitive) {
        if (primitive == null) {
            return false;
        }
        Layer layer = getLayer(primitive.getLayerName());
        return layer != null && layer.isLocked();
    }

    public void clearNonDefaultLayerPrimitives() {
        List<Primitive> removed = new ArrayList<>();
        for (Primitive primitive : new ArrayList<>(primitives)) {
            if (!Layer.DEFAULT_LAYER_NAME.equals(primitive.getLayerName())) {
                for (Primitive dependent : collectPrimitiveAndDependentDimensions(primitive)) {
                    if (!removed.contains(dependent)) {
                        removed.add(dependent);
                    }
                }
            }
        }
        if (removed.isEmpty()) {
            return;
        }
        removePrimitivesInternal(removed);
        pushUndo(() -> addPrimitivesInternal(removed));
    }

    public boolean undo() {
        Runnable undo = undoStack.pollLast();
        if (undo == null) {
            return false;
        }
        undo.run();
        dirty.set(true);
        return true;
    }

    public void pushUndo(Runnable undo) {
        if (undo != null) {
            undoStack.addLast(undo);
            dirty.set(true);
        }
    }

    public boolean isDirty() {
        return dirty.get();
    }

    public void markChanged() {
        dirty.set(true);
    }

    public void markSaved() {
        dirty.set(false);
    }

    public BooleanProperty dirtyProperty() {
        return dirty;
    }

    public boolean glueSelectedPrimitives() {
        if (selectedPrimitives.size() < 2) {
            return false;
        }

        List<List<Point>> chains = new ArrayList<>();
        for (Primitive primitive : selectedPrimitives) {
            if (primitive instanceof Segment segment) {
                chains.add(new ArrayList<>(List.of(segment.getStartPoint(), segment.getEndPoint())));
            } else if (primitive instanceof Polyline polyline && !polyline.isClosed()) {
                chains.add(new ArrayList<>(polyline.getVertices()));
            } else {
                return false;
            }
        }

        List<Point> merged = new ArrayList<>(chains.remove(0));
        while (!chains.isEmpty()) {
            boolean attached = false;
            for (int i = 0; i < chains.size(); i++) {
                List<Point> candidate = new ArrayList<>(chains.get(i));
                if (tryAttachChain(merged, candidate)) {
                    chains.remove(i);
                    attached = true;
                    break;
                }
            }
            if (!attached) {
                return false;
            }
        }

        boolean closed = merged.size() > 2 && pointsEqual(merged.get(0), merged.get(merged.size() - 1));
        if (closed) {
            merged.remove(merged.size() - 1);
        }

        Primitive template = selectedPrimitive.get() != null
                ? selectedPrimitive.get()
                : selectedPrimitives.get(0);

        Polyline polyline = new Polyline(merged, closed, template.getLineStyle());
        polyline.setLayerName(template.getLayerName());
        polyline.setColorAci(template.getColorAci());

        List<Primitive> originals = new ArrayList<>(selectedPrimitives);
        primitives.removeAll(originals);
        primitives.add(polyline);
        selectedPrimitives.setAll(polyline);
        selectedPrimitive.set(polyline);
        pushUndo(() -> {
            primitives.remove(polyline);
            primitives.addAll(originals);
            selectedPrimitives.setAll(originals);
            selectedPrimitive.set(originals.isEmpty() ? null : originals.get(originals.size() - 1));
        });
        return true;
    }

    public boolean unglueSelectedPrimitives() {
        if (selectedPrimitives.isEmpty()) {
            return false;
        }

        List<Primitive> originals = new ArrayList<>(selectedPrimitives);
        List<Primitive> replacements = new ArrayList<>();

        for (Primitive primitive : originals) {
            if (!(primitive instanceof Polyline polyline)) {
                return false;
            }

            List<Segment> exploded = polyline.explodeToSegments();
            if (exploded.isEmpty()) {
                return false;
            }

            for (Segment segment : exploded) {
                segment.setLayerName(polyline.getLayerName());
                segment.setColorAci(polyline.getColorAci());
                replacements.add(segment);
            }
        }

        primitives.removeAll(originals);
        primitives.addAll(replacements);
        selectedPrimitives.setAll(replacements);
        selectedPrimitive.set(replacements.isEmpty() ? null : replacements.get(replacements.size() - 1));
        pushUndo(() -> {
            primitives.removeAll(replacements);
            primitives.addAll(originals);
            selectedPrimitives.setAll(originals);
            selectedPrimitive.set(originals.isEmpty() ? null : originals.get(originals.size() - 1));
        });
        return true;
    }

    public void clearAll() {
        List<Primitive> oldPrimitives = new ArrayList<>(primitives);
        List<Segment> oldSegments = new ArrayList<>(segments);
        primitives.clear();
        segments.clear();
        clearSelection();
        setSelectedSegment(null);
        if (!oldPrimitives.isEmpty() || !oldSegments.isEmpty()) {
            pushUndo(() -> {
                primitives.clear();
                primitives.addAll(oldPrimitives);
                segments.clear();
                segments.addAll(oldSegments);
            });
        }
    }

    private void addPrimitiveInternal(Primitive primitive) {
        primitives.add(primitive);
        dirty.set(true);
    }

    private void addPrimitivesInternal(List<Primitive> primitivesToAdd) {
        primitives.addAll(primitivesToAdd);
        selectedPrimitives.setAll(primitivesToAdd);
        selectedPrimitive.set(primitivesToAdd.isEmpty() ? null : primitivesToAdd.get(primitivesToAdd.size() - 1));
        dirty.set(true);
    }

    private void removePrimitiveInternal(Primitive primitive) {
        removePrimitivesInternal(List.of(primitive));
    }

    private void removePrimitivesInternal(List<Primitive> primitivesToRemove) {
        primitives.removeAll(primitivesToRemove);
        selectedPrimitives.removeAll(primitivesToRemove);
        if (selectedPrimitive.get() != null && primitivesToRemove.contains(selectedPrimitive.get())) {
            selectedPrimitive.set(selectedPrimitives.isEmpty()
                    ? null
                    : selectedPrimitives.get(selectedPrimitives.size() - 1));
        }
        dirty.set(true);
    }

    private List<Primitive> collectPrimitiveAndDependentDimensions(Primitive primitive) {
        List<Primitive> result = new ArrayList<>();
        if (!primitives.contains(primitive)) {
            return result;
        }
        result.add(primitive);
        for (Primitive candidate : primitives) {
            if (candidate != primitive && isDimensionDependentOn(candidate, primitive)) {
                result.add(candidate);
            }
        }
        return result;
    }

    private boolean isDimensionDependentOn(Primitive candidate, Primitive primitive) {
        if (candidate instanceof LinearDimension dimension) {
            return isAnchorDependentOn(dimension.getFirstAnchor(), primitive)
                    || isAnchorDependentOn(dimension.getSecondAnchor(), primitive);
        }
        if (candidate instanceof AngularDimension dimension) {
            return isAnchorDependentOn(dimension.getVertexAnchor(), primitive)
                    || isAnchorDependentOn(dimension.getFirstRayAnchor(), primitive)
                    || isAnchorDependentOn(dimension.getSecondRayAnchor(), primitive);
        }
        if (candidate instanceof RadialDimension dimension) {
            return dimension.getReferencedPrimitive() == primitive;
        }
        return false;
    }

    private boolean isAnchorDependentOn(DimensionAnchor anchor, Primitive primitive) {
        return anchor != null && anchor.getPrimitive() == primitive;
    }

    private void clearSelectionOfHiddenPrimitives() {
        selectedPrimitives.removeIf(p -> !isPrimitiveLayerVisible(p));
        if (selectedPrimitive.get() != null && !isPrimitiveLayerVisible(selectedPrimitive.get())) {
            selectedPrimitive.set(selectedPrimitives.isEmpty()
                    ? null
                    : selectedPrimitives.get(selectedPrimitives.size() - 1));
        }
    }

    private boolean tryAttachChain(List<Point> merged, List<Point> candidate) {
        if (pointsEqual(merged.get(merged.size() - 1), candidate.get(0))) {
            appendWithoutDuplicate(merged, candidate);
            return true;
        }

        if (pointsEqual(merged.get(merged.size() - 1), candidate.get(candidate.size() - 1))) {
            Collections.reverse(candidate);
            appendWithoutDuplicate(merged, candidate);
            return true;
        }

        if (pointsEqual(merged.get(0), candidate.get(candidate.size() - 1))) {
            appendWithoutDuplicate(candidate, merged);
            merged.clear();
            merged.addAll(candidate);
            return true;
        }

        if (pointsEqual(merged.get(0), candidate.get(0))) {
            Collections.reverse(candidate);
            appendWithoutDuplicate(candidate, merged);
            merged.clear();
            merged.addAll(candidate);
            return true;
        }

        return false;
    }

    private void appendWithoutDuplicate(List<Point> target, List<Point> extension) {
        for (int i = 1; i < extension.size(); i++) {
            target.add(extension.get(i));
        }
    }

    private boolean pointsEqual(Point first, Point second) {
        if (first == null || second == null) {
            return false;
        }
        double dx = first.getX() - second.getX();
        double dy = first.getY() - second.getY();
        return dx * dx + dy * dy <= JOIN_TOLERANCE_SQUARED;
    }
}
