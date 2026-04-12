package org.example.model;

/**
 * Контрольная точка примитива для редактирования.
 * Позволяет изменять геометрию примитива через перетаскивание.
 */
public class ControlPoint {

    public enum Type {
        ENDPOINT,
        CENTER,
        /** Точка на контуре для изменения радиуса/размера */
        RADIUS,
        ANGLE,
        AXIS,
        CONTROL,
        CHAMFER
    }

    private final Point position;
    private final Type type;
    private final int index;
    private final String label;

    public ControlPoint(Point position, Type type, int index, String label) {
        this.position = position;
        this.type = type;
        this.index = index;
        this.label = label;
    }

    public ControlPoint(Point position, Type type, int index) {
        this(position, type, index, null);
    }

    public Point getPosition() {
        return position;
    }

    public Type getType() {
        return type;
    }

    public int getIndex() {
        return index;
    }

    public String getLabel() {
        return label;
    }
}

