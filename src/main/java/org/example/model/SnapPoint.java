package org.example.model;

/**
 * Точка привязки - позиция с типом привязки.
 * Менеджер привязок. Помогает курсору примагничиваться к объектам.
 */
public class SnapPoint {
    private final Point position;
    private final SnapType type;
    private final Primitive sourcePrimitive;
    private final Primitive secondPrimitive; // Для пересечений

    public SnapPoint(Point position, SnapType type, Primitive sourcePrimitive) {
        this.position = position;
        this.type = type;
        this.sourcePrimitive = sourcePrimitive;
        this.secondPrimitive = null;
    }

    public SnapPoint(Point position, SnapType type, Primitive primitive1, Primitive primitive2) {
        this.position = position;
        this.type = type;
        this.sourcePrimitive = primitive1;
        this.secondPrimitive = primitive2;
    }

    public Point getPosition() {
        return position;
    }

    public SnapType getType() {
        return type;
    }

    public Primitive getSourcePrimitive() {
        return sourcePrimitive;
    }

    public Primitive getSecondPrimitive() {
        return secondPrimitive;
    }

    public double distanceTo(Point point) {
        double dx = position.getX() - point.getX();
        double dy = position.getY() - point.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    @Override
    public String toString() {
        return type.getDisplayName() + " (" +
                String.format("%.2f", position.getX()) + ", " +
                String.format("%.2f", position.getY()) + ")";
    }
}

