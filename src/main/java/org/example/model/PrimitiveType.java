package org.example.model;

public enum PrimitiveType {
    POINT("Точка"),
    SEGMENT("Отрезок"),
    CIRCLE("Окружность"),
    ARC("Дуга"),
    RECTANGLE("Прямоугольник"),
    ELLIPSE("Эллипс"),
    POLYGON("Многоугольник"),
    SPLINE("Сплайн");
    
    private final String displayName;
    
    PrimitiveType(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}







