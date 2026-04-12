package org.example.model;

public enum SnapType {
    ENDPOINT("Конец", "⬜"),
    
    /** Середина отрезка или дуги */
    MIDPOINT("Середина", "△"),
    
    /** Центр окружности, дуги, эллипса, многоугольника */
    CENTER("Центр", "○"),
    
    INTERSECTION("Пересечение", "✕"),
    
    /** Перпендикуляр к линии */
    PERPENDICULAR("Перпендикуляр", "⊥"),
    
    /** Касательная к окружности/дуге/эллипсу */
    TANGENT("Касательная", "◯");
    
    private final String displayName;
    private final String symbol;
    
    SnapType(String displayName, String symbol) {
        this.displayName = displayName;
        this.symbol = symbol;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getSymbol() {
        return symbol;
    }
}




