package org.example.model;

import javafx.beans.property.*;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Описание стилей линий
 * 
 * Параметры:
 * - Тип линии (сплошная, штриховая, волнистая и т.д.)
 * - Толщина (основная s=0.5-1.4мм, тонкая s/2=0.25-0.7мм)
 * - Длина штриха (для штриховых линий)
 * - Расстояние между штрихами (для штриховых линий)
 */
public class LineStyle {

    private final StringProperty name = new SimpleStringProperty();
    private final ObjectProperty<LineType> type = new SimpleObjectProperty<>(LineType.SOLID);

    private final DoubleProperty thickness = new SimpleDoubleProperty(1.0);

    private final DoubleProperty thicknessMm = new SimpleDoubleProperty(0.5);

    // Паттерн штриховки [штрих, пробел, ...] - для штриховых линий
    private final ObjectProperty<double[]> dashPattern = new SimpleObjectProperty<>();

    private final DoubleProperty dashLengthMm = new SimpleDoubleProperty(6.0);

    private final DoubleProperty dashGapMm = new SimpleDoubleProperty(2.0);

    private final DoubleProperty waveAmplitude = new SimpleDoubleProperty(5.0);

    private final DoubleProperty waveLength = new SimpleDoubleProperty(15.0);

    private final BooleanProperty isSystem = new SimpleBooleanProperty();

    public enum ThicknessCategory {
        THICK("Основная (s)"),
        THIN("Тонкая (s/2)");

        private final String displayName;

        ThicknessCategory(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private final ObjectProperty<ThicknessCategory> thicknessCategory = new SimpleObjectProperty<>(
            ThicknessCategory.THIN);


    public LineStyle(String name, double thicknessPx, double[] dashPattern, boolean isSystem, LineType type) {
        setName(name);
        setThickness(thicknessPx);
        setDashPattern(dashPattern);
        setIsSystem(isSystem);
        setType(type);
    }

    public LineStyle(String name, double thicknessPx, double[] dashPattern, boolean isSystem) {
        this(name, thicknessPx, dashPattern, isSystem, LineType.SOLID);
    }

    /**
     * Создаёт стиль линии с полными параметрами ЕСКД.
     */
    public static LineStyle createESKD(String name, LineType type, ThicknessCategory thickCat,
            double dashMm, double gapMm, boolean isSystem) {
        LineStyle style = new LineStyle(name, 1.0, null, isSystem, type);
        style.setThicknessCategory(thickCat);
        style.setDashLengthMm(dashMm);
        style.setDashGapMm(gapMm);
        return style;
    }


    public String getName() {
        return name.get();
    }

    public void setName(String value) {
        name.set(value);
    }

    public StringProperty nameProperty() {
        return name;
    }

    public double getThickness() {
        return thickness.get();
    }

    public void setThickness(double value) {
        thickness.set(value);
    }

    public DoubleProperty thicknessProperty() {
        return thickness;
    }

    public double getThicknessMm() {
        return thicknessMm.get();
    }

    public void setThicknessMm(double value) {
        thicknessMm.set(value);
    }

    public DoubleProperty thicknessMmProperty() {
        return thicknessMm;
    }

    public double[] getDashPattern() {
        return dashPattern.get();
    }

    public void setDashPattern(double[] value) {
        dashPattern.set(value);
    }

    public ObjectProperty<double[]> dashPatternProperty() {
        return dashPattern;
    }

    public boolean isSystem() {
        return isSystem.get();
    }

    public void setIsSystem(boolean value) {
        isSystem.set(value);
    }

    public BooleanProperty isSystemProperty() {
        return isSystem;
    }

    public LineType getType() {
        return type.get();
    }

    public void setType(LineType value) {
        type.set(value);
    }

    public ObjectProperty<LineType> typeProperty() {
        return type;
    }

    public ThicknessCategory getThicknessCategory() {
        return thicknessCategory.get();
    }

    public void setThicknessCategory(ThicknessCategory value) {
        thicknessCategory.set(value);
    }

    public ObjectProperty<ThicknessCategory> thicknessCategoryProperty() {
        return thicknessCategory;
    }

    public double getDashLengthMm() {
        return dashLengthMm.get();
    }

    public void setDashLengthMm(double value) {
        dashLengthMm.set(value);
    }

    public DoubleProperty dashLengthMmProperty() {
        return dashLengthMm;
    }

    public double getDashGapMm() {
        return dashGapMm.get();
    }

    public void setDashGapMm(double value) {
        dashGapMm.set(value);
    }

    public DoubleProperty dashGapMmProperty() {
        return dashGapMm;
    }

    public double getWaveAmplitude() {
        return waveAmplitude.get();
    }

    public void setWaveAmplitude(double value) {
        waveAmplitude.set(value);
    }

    public DoubleProperty waveAmplitudeProperty() {
        return waveAmplitude;
    }

    public double getWaveLength() {
        return waveLength.get();
    }

    public void setWaveLength(double value) {
        waveLength.set(value);
    }

    public DoubleProperty waveLengthProperty() {
        return waveLength;
    }


    public String getDashPatternString() {
        if (getDashPattern() == null || getDashPattern().length == 0)
            return "";
        return Arrays.stream(getDashPattern()).mapToObj(String::valueOf).collect(Collectors.joining(" "));
    }

    public static double[] parseDashPattern(String str) {
        if (str == null || str.trim().isEmpty())
            return null;
        try {
            return Arrays.stream(str.trim().split("\\s+")).mapToDouble(Double::parseDouble).toArray();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return getName();
    }
}


