package org.example.model;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.paint.Color;

/**
 * Настройки отображения приложения.
 *
 * Хранит параметры сетки, цвета фона и примитивов, единицы измерения углов.
 * Все свойства реализованы через JavaFX-пропертис — изменение любого значения
 * автоматически уведомляет подписанных слушателей (UI, перерисовка холста).
 */
public class AppSettings {

    public enum AngleUnit {
        DEGREES,
        RADIANS
    }

    private final IntegerProperty gridStep        = new SimpleIntegerProperty(20);
    private final ObjectProperty<Color> backgroundColor = new SimpleObjectProperty<>(Color.WHITE);
    private final ObjectProperty<Color> gridColor       = new SimpleObjectProperty<>(Color.LIGHTGRAY);
    private final ObjectProperty<Color> segmentColor    = new SimpleObjectProperty<>(Color.ROYALBLUE);
    private final ObjectProperty<AngleUnit> angleUnit   = new SimpleObjectProperty<>(AngleUnit.DEGREES);


    public IntegerProperty gridStepProperty()     { return gridStep; }

    public ObjectProperty<Color> backgroundColorProperty() { return backgroundColor; }

    public ObjectProperty<Color> gridColorProperty()       { return gridColor; }

    public ObjectProperty<Color> segmentColorProperty()    { return segmentColor; }

    public ObjectProperty<AngleUnit> angleUnitProperty()   { return angleUnit; }


    public int     getGridStep()       { return gridStep.get(); }

    public void    setGridStep(int v)  { gridStep.set(v); }

    public Color   getBackgroundColor() { return backgroundColor.get(); }

    public Color   getSegmentColor()   { return segmentColor.get(); }

    public Color   getGridColor()      { return gridColor.get(); }

    public AngleUnit getAngleUnit()    { return angleUnit.get(); }

    public void setAngleUnit(AngleUnit unit) { angleUnit.set(unit); }
}


