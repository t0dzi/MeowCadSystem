package org.example.model;

public enum LineType {
    SOLID("Сплошная"),

    WAVY("Волнистая"),

    DASHED("Штриховая"),

    DASH_DOT("Штрихпунктирная"),

    DASH_DOT_DOT("Штрихпунктирная 2 точки"),

    ZIGZAG("С изломами");

    private final String displayName;

    LineType(String displayName) {
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

