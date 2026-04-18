package org.example.model;

import javafx.scene.paint.Color;

import java.util.Locale;

public abstract class DimensionPrimitive extends Primitive {

    public enum TextPlacement {
        ABOVE_LINE("Над линией"),
        ON_LINE("На линии"),
        BELOW_LINE("Под линией");

        private final String displayName;

        TextPlacement(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum FontVariant {
        FONT_A("Шрифт A", 0.78),
        FONT_B("Шрифт B", 0.92);

        private final String displayName;
        private final double widthFactor;

        FontVariant(String displayName, double widthFactor) {
            this.displayName = displayName;
            this.widthFactor = widthFactor;
        }

        public String getDisplayName() {
            return displayName;
        }

        public double getWidthFactor() {
            return widthFactor;
        }
    }

    public enum ArrowType {
        CLOSED("Закрытая"),
        OPEN("Открытая"),
        SLASH("Засечка"),
        DOT("Точка");

        private final String displayName;

        ArrowType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private String textOverride = "";
    private Point textPosition;
    private boolean textPositionManuallyMoved;

    private LineStyle extensionLineStyle;
    private Color dimensionLineColor;
    private Color extensionLineColor;

    private double extensionLineOffset = 0.0;
    private double extensionLineOvershoot = 8.0;
    private double dimensionLineExtension = 0.0;
    private double arrowSize = 6.0;
    private boolean filledArrows = true;
    private ArrowType arrowType = ArrowType.CLOSED;
    private String textFont = "Arial";
    private FontVariant fontVariant = FontVariant.FONT_B;
    private TextPlacement textPlacement = TextPlacement.ABOVE_LINE;
    private double textHeight = 12.0;
    private double textGap = 8.0;

    protected DimensionPrimitive(LineStyle lineStyle) {
        super(lineStyle);
    }

    public abstract double getMeasuredValue();

    protected abstract Point getDefaultTextPosition();

    protected abstract String getMeasurementSuffix();

    public String getTextOverride() {
        return textOverride;
    }

    public void setTextOverride(String textOverride) {
        this.textOverride = textOverride != null ? textOverride : "";
    }

    public boolean hasTextOverride() {
        return textOverride != null && !textOverride.isBlank();
    }

    public String getDisplayText() {
        if (hasTextOverride()) {
            return textOverride;
        }
        return formatMeasurement(getMeasuredValue(), getMeasurementSuffix());
    }

    public Point getTextPosition() {
        if (textPositionManuallyMoved && textPosition != null) {
            return textPosition;
        }
        return getDefaultTextPosition();
    }

    public void setTextPosition(Point textPosition) {
        this.textPosition = textPosition;
        this.textPositionManuallyMoved = textPosition != null;
    }

    public boolean isTextPositionManuallyMoved() {
        return textPositionManuallyMoved;
    }

    public void resetTextPosition() {
        this.textPosition = null;
        this.textPositionManuallyMoved = false;
    }

    public LineStyle getExtensionLineStyle() {
        return extensionLineStyle != null ? extensionLineStyle : getLineStyle();
    }

    public void setExtensionLineStyle(LineStyle extensionLineStyle) {
        this.extensionLineStyle = extensionLineStyle;
    }

    public Color getDimensionLineColor() {
        return dimensionLineColor;
    }

    public void setDimensionLineColor(Color dimensionLineColor) {
        this.dimensionLineColor = dimensionLineColor;
    }

    public Color getExtensionLineColor() {
        return extensionLineColor;
    }

    public void setExtensionLineColor(Color extensionLineColor) {
        this.extensionLineColor = extensionLineColor;
    }

    public double getExtensionLineOffset() {
        return extensionLineOffset;
    }

    public void setExtensionLineOffset(double extensionLineOffset) {
        this.extensionLineOffset = Math.max(0.0, extensionLineOffset);
    }

    public double getExtensionLineOvershoot() {
        return extensionLineOvershoot;
    }

    public void setExtensionLineOvershoot(double extensionLineOvershoot) {
        this.extensionLineOvershoot = Math.max(0.0, extensionLineOvershoot);
    }

    public double getDimensionLineExtension() {
        return dimensionLineExtension;
    }

    public void setDimensionLineExtension(double dimensionLineExtension) {
        this.dimensionLineExtension = Math.max(0.0, dimensionLineExtension);
    }

    public double getArrowSize() {
        return arrowSize;
    }

    public void setArrowSize(double arrowSize) {
        this.arrowSize = Math.max(1.0, arrowSize);
    }

    public boolean isFilledArrows() {
        return filledArrows;
    }

    public void setFilledArrows(boolean filledArrows) {
        this.filledArrows = filledArrows;
    }

    public ArrowType getArrowType() {
        return arrowType;
    }

    public void setArrowType(ArrowType arrowType) {
        this.arrowType = arrowType != null ? arrowType : ArrowType.CLOSED;
    }

    public String getTextFont() {
        return textFont;
    }

    public void setTextFont(String textFont) {
        this.textFont = textFont != null && !textFont.isBlank() ? textFont : "Arial";
    }

    public FontVariant getFontVariant() {
        return fontVariant;
    }

    public void setFontVariant(FontVariant fontVariant) {
        this.fontVariant = fontVariant != null ? fontVariant : FontVariant.FONT_B;
    }

    public TextPlacement getTextPlacement() {
        return textPlacement;
    }

    public void setTextPlacement(TextPlacement textPlacement) {
        this.textPlacement = textPlacement != null ? textPlacement : TextPlacement.ABOVE_LINE;
    }

    public double getTextHeight() {
        return textHeight;
    }

    public void setTextHeight(double textHeight) {
        this.textHeight = Math.max(6.0, textHeight);
    }

    public double getTextGap() {
        return textGap;
    }

    public void setTextGap(double textGap) {
        this.textGap = Math.max(0.0, textGap);
    }

    protected void translateText(double dx, double dy) {
        if (textPositionManuallyMoved && textPosition != null) {
            textPosition = new Point(textPosition.getX() + dx, textPosition.getY() + dy);
        }
    }

    protected String formatMeasurement(double value, String suffix) {
        String formatted;
        if (Math.abs(value - Math.rint(value)) < 1e-6) {
            formatted = String.format(Locale.US, "%.0f", value);
        } else {
            formatted = String.format(Locale.US, "%.2f", value)
                    .replaceAll("0+$", "")
                    .replaceAll("\\.$", "");
        }
        return suffix == null ? formatted : formatted + suffix;
    }

    protected static Point midpoint(Point p1, Point p2) {
        return new Point(
                (p1.getX() + p2.getX()) / 2.0,
                (p1.getY() + p2.getY()) / 2.0);
    }

    protected static double distance(Point p1, Point p2) {
        double dx = p2.getX() - p1.getX();
        double dy = p2.getY() - p1.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    protected static double dot(Point p1, Point p2) {
        return p1.getX() * p2.getX() + p1.getY() * p2.getY();
    }

    protected static Point subtract(Point p1, Point p2) {
        return new Point(p1.getX() - p2.getX(), p1.getY() - p2.getY());
    }

    protected static Point add(Point p1, Point p2) {
        return new Point(p1.getX() + p2.getX(), p1.getY() + p2.getY());
    }

    protected static Point scale(Point p, double scalar) {
        return new Point(p.getX() * scalar, p.getY() * scalar);
    }

    protected static Point normalize(Point p) {
        double length = Math.sqrt(p.getX() * p.getX() + p.getY() * p.getY());
        if (length < 1e-9) {
            return new Point(1, 0);
        }
        return new Point(p.getX() / length, p.getY() / length);
    }

    protected static Point perpendicularLeft(Point p) {
        return new Point(-p.getY(), p.getX());
    }
}
