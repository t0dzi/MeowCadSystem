package org.example.model;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import java.util.ArrayList;
import java.util.Collections;
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
    private final List<Segment> segments = new ArrayList<>();
    private final ObjectProperty<Segment> selectedSegment = new SimpleObjectProperty<>(null);

    private final List<Primitive> primitives = new ArrayList<>();
    private final ObjectProperty<Primitive> selectedPrimitive = new SimpleObjectProperty<>(null);

    private final ObjectProperty<LineStyle> currentStyle = new SimpleObjectProperty<>();

    private final SnapManager snapManager;


    private final List<Layer> layers = new ArrayList<>();

    private final StringProperty activeLayerName = new SimpleStringProperty(Layer.DEFAULT_LAYER_NAME);

    public CadModel() {
        this.snapManager = new SnapManager(this);
        layers.add(Layer.createDefault());
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
            primitives.add(primitive);
        }
    }

    /**
     * Добавляет примитив, сохраняя слой, уже установленный на примитиве
     * (без перезаписи активным слоём). Используется при импорте DXF.
     */
    public void addImportedPrimitive(Primitive primitive) {
        if (primitive != null) {
            primitives.add(primitive);
        }
    }

    public void removePrimitive(Primitive primitive) {
        if (primitive != null) {
            primitives.remove(primitive);
            if (getSelectedPrimitive() == primitive) {
                setSelectedPrimitive(null);
            }
        }
    }

    public List<Primitive> getPrimitives() {
        return Collections.unmodifiableList(primitives);
    }

    public Primitive getSelectedPrimitive() {
        return selectedPrimitive.get();
    }

    public void setSelectedPrimitive(Primitive primitive) {
        selectedPrimitive.set(primitive);
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

    public void clearAll() {
        primitives.clear();
        segments.clear();
        setSelectedPrimitive(null);
        setSelectedSegment(null);
    }
}


