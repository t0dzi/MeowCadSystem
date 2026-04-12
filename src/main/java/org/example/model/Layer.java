package org.example.model;

/**
 * Описание слоя (имя, цвет, тип линии)
 *
 * Каждый слой имеет:
 * - Уникальное имя
 * - Цвет (ACI — AutoCAD Color Index, 1–255)
 * - Тип линии (имя DXF: "CONTINUOUS", "DASHED" и т.д.)
 * - Флаг видимости
 * - Флаг блокировки (запрет редактирования)
 */
public class Layer {

    public static final String DEFAULT_LAYER_NAME = "0";

    private String name;
    private int colorIndex; // ACI: 1–255 (7 = белый/чёрный)
    private String linetypeName; // DXF linetype name
    private boolean visible;
    private boolean locked;

    public Layer(String name, int colorIndex, String linetypeName) {
        this.name = name;
        this.colorIndex = colorIndex;
        this.linetypeName = linetypeName != null ? linetypeName : "CONTINUOUS";
        this.visible = true;
        this.locked = false;
    }

    public Layer(String name) {
        this(name, 7, "CONTINUOUS");
    }

    public static Layer createDefault() {
        return new Layer(DEFAULT_LAYER_NAME, 7, "CONTINUOUS");
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getColorIndex() {
        return colorIndex;
    }

    public void setColorIndex(int colorIndex) {
        this.colorIndex = Math.max(1, Math.min(255, colorIndex));
    }

    public String getLinetypeName() {
        return linetypeName;
    }

    public void setLinetypeName(String linetypeName) {
        this.linetypeName = linetypeName != null ? linetypeName : "CONTINUOUS";
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        Layer layer = (Layer) obj;
        return name.equals(layer.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}


