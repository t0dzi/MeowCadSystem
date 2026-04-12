package org.example.model;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.stage.Screen;

/**
 * Менеджер стилей линий по ЕСКД.
 */
public class StyleManager {

    private final ObservableList<LineStyle> styles = FXCollections.observableArrayList();
    private final double screenDpi;

    private final DoubleProperty baseThicknessMm = new SimpleDoubleProperty(0.5);

    public static final double MIN_THICK_MM = 0.5;
    public static final double MAX_THICK_MM = 1.4;
    public static final double DEFAULT_THICK_MM = 0.8;

    public StyleManager() {
        double dpi = 96.0;
        try {
            dpi = Screen.getPrimary().getDpi();
        } catch (Throwable ignored) {
        }
        this.screenDpi = dpi;

        initESKDStyles();

        baseThicknessMm.addListener((obs, oldV, newV) -> updateSystemStyles(newV.doubleValue()));
    }

    public int mmToPixels(double mm) {
        return (int) Math.max(1, Math.round((mm / 25.4) * screenDpi));
    }

    /**
     * Инициализирует стандартные стили по ЕСКД (ГОСТ 2.303-68).
     */
    private void initESKDStyles() {
        double s_mm = baseThicknessMm.get();
        double thin_mm = s_mm / 2.0;

        double s_px = mmToPixels(s_mm);
        double thin_px = mmToPixels(thin_mm);

        LineStyle solidThick = new LineStyle("Сплошная основная", s_px, null, true, LineType.SOLID);
        solidThick.setThicknessCategory(LineStyle.ThicknessCategory.THICK);
        solidThick.setThicknessMm(s_mm);
        styles.add(solidThick);

        LineStyle solidThin = new LineStyle("Сплошная тонкая", thin_px, null, true, LineType.SOLID);
        solidThin.setThicknessCategory(LineStyle.ThicknessCategory.THIN);
        solidThin.setThicknessMm(thin_mm);
        styles.add(solidThin);

        LineStyle wavy = new LineStyle("Сплошная волнистая", thin_px, new double[] { 2.5, 10.0 }, true, LineType.WAVY);
        wavy.setThicknessCategory(LineStyle.ThicknessCategory.THIN);
        wavy.setThicknessMm(thin_mm);
        wavy.setWaveAmplitude(5.0);
        wavy.setWaveLength(15.0);
        styles.add(wavy);

        // Паттерн: [штрих, пробел]
        LineStyle dashed = new LineStyle("Штриховая", thin_px, new double[] { 6.0, 3.0 }, true, LineType.DASHED);
        dashed.setThicknessCategory(LineStyle.ThicknessCategory.THIN);
        dashed.setThicknessMm(thin_mm);
        dashed.setDashLengthMm(6.0);
        dashed.setDashGapMm(3.0);
        styles.add(dashed);

        // Паттерн: [штрих, пробел, точка, пробел]
        LineStyle dashDotThin = new LineStyle("Штрихпунктирная тонкая", thin_px,
                new double[] { 12.0, 3.0, 1.0, 3.0 }, true, LineType.DASH_DOT);
        dashDotThin.setThicknessCategory(LineStyle.ThicknessCategory.THIN);
        dashDotThin.setThicknessMm(thin_mm);
        dashDotThin.setDashLengthMm(12.0);
        dashDotThin.setDashGapMm(3.0);
        styles.add(dashDotThin);

        LineStyle dashDotThick = new LineStyle("Штрихпунктирная утолщ.", s_px,
                new double[] { 8.0, 3.0, 1.0, 3.0 }, true, LineType.DASH_DOT);
        dashDotThick.setThicknessCategory(LineStyle.ThicknessCategory.THICK);
        dashDotThick.setThicknessMm(s_mm);
        dashDotThick.setDashLengthMm(8.0);
        dashDotThick.setDashGapMm(3.0);
        styles.add(dashDotThick);

        // Паттерн: [штрих, пробел, точка, пробел, точка, пробел]
        LineStyle dashDotDot = new LineStyle("Штрихпунктирная 2 точки", thin_px,
                new double[] { 12.0, 3.0, 1.0, 3.0, 1.0, 3.0 }, true, LineType.DASH_DOT_DOT);
        dashDotDot.setThicknessCategory(LineStyle.ThicknessCategory.THIN);
        dashDotDot.setThicknessMm(thin_mm);
        dashDotDot.setDashLengthMm(12.0);
        dashDotDot.setDashGapMm(3.0);
        styles.add(dashDotDot);

        LineStyle zigzag = new LineStyle("Сплошная с изломами", thin_px,
                new double[] { 12.0, 10.0, 1.0 }, true, LineType.ZIGZAG);
        zigzag.setThicknessCategory(LineStyle.ThicknessCategory.THIN);
        zigzag.setThicknessMm(thin_mm);
        styles.add(zigzag);
    }

    private void updateSystemStyles(double newS_mm) {
        double s_px = mmToPixels(newS_mm);
        double thin_mm = newS_mm / 2.0;
        double thin_px = mmToPixels(thin_mm);

        for (LineStyle style : styles) {
            if (style.isSystem()) {
                if (style.getThicknessCategory() == LineStyle.ThicknessCategory.THICK) {
                    style.setThickness(s_px);
                    style.setThicknessMm(newS_mm);
                } else {
                    style.setThickness(thin_px);
                    style.setThicknessMm(thin_mm);
                }
            }
        }
    }


    public ObservableList<LineStyle> getStyles() {
        return styles;
    }

    public LineStyle getDefaultStyle() {
        return styles.isEmpty() ? null : styles.get(0);
    }

    public LineStyle getStyleByName(String name) {
        return styles.stream()
                .filter(s -> s.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    public DoubleProperty baseThicknessMmProperty() {
        return baseThicknessMm;
    }

    public double getBaseThicknessMm() {
        return baseThicknessMm.get();
    }

    public void setBaseThicknessMm(double value) {
        baseThicknessMm.set(Math.max(MIN_THICK_MM, Math.min(MAX_THICK_MM, value)));
    }


    public LineStyle createCustomStyle(String name, LineType type,
            LineStyle.ThicknessCategory thickCat,
            double dashLengthMm, double dashGapMm) {
        double thickMm = thickCat == LineStyle.ThicknessCategory.THICK ? baseThicknessMm.get()
                : baseThicknessMm.get() / 2.0;
        double thickPx = mmToPixels(thickMm);

        double[] pattern = null;
        if (type == LineType.DASHED) {
            pattern = new double[] { dashLengthMm, dashGapMm };
        } else if (type == LineType.DASH_DOT) {
            pattern = new double[] { dashLengthMm, dashGapMm, 1.0, dashGapMm };
        } else if (type == LineType.DASH_DOT_DOT) {
            pattern = new double[] { dashLengthMm, dashGapMm, 1.0, dashGapMm, 1.0, dashGapMm };
        }

        LineStyle style = new LineStyle(name, thickPx, pattern, false, type);
        style.setThicknessCategory(thickCat);
        style.setThicknessMm(thickMm);
        style.setDashLengthMm(dashLengthMm);
        style.setDashGapMm(dashGapMm);

        styles.add(style);
        return style;
    }

    public boolean removeStyle(LineStyle style) {
        if (!style.isSystem()) {
            return styles.remove(style);
        }
        return false;
    }

    public void updateStyleParameters(LineStyle style, double dashLengthMm, double dashGapMm) {
        style.setDashLengthMm(dashLengthMm);
        style.setDashGapMm(dashGapMm);

        // Пересчитываем паттерн
        LineType type = style.getType();
        double[] pattern = null;
        if (type == LineType.DASHED) {
            pattern = new double[] { dashLengthMm, dashGapMm };
        } else if (type == LineType.DASH_DOT) {
            pattern = new double[] { dashLengthMm, dashGapMm, 1.0, dashGapMm };
        } else if (type == LineType.DASH_DOT_DOT) {
            pattern = new double[] { dashLengthMm, dashGapMm, 1.0, dashGapMm, 1.0, dashGapMm };
        }
        style.setDashPattern(pattern);
    }

    public double getScreenDpi() {
        return screenDpi;
    }
}


