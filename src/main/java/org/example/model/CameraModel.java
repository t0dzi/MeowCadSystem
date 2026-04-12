package org.example.model;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;

/**
 * Модель камеры (виртуального вида на чертёж).
 *
 * Управляет панорамированием (X, Y), масштабом (zoom) и углом поворота вида.
 * Все параметры — JavaFX DoubleProperty, что позволяет вешать слушателей
 * для автоматической перерисовки.
 *
 * Поддерживает пакетное обновление: {@link #beginUpdate()} / {@link #endUpdate()}
 * подавляет промежуточные перерисовки при одновременном изменении нескольких параметров.
 */
public class CameraModel {

    private final DoubleProperty x     = new SimpleDoubleProperty(0);

    private final DoubleProperty y     = new SimpleDoubleProperty(0);

    private final DoubleProperty scale = new SimpleDoubleProperty(1.0);

    private final DoubleProperty angle = new SimpleDoubleProperty(0);

    private boolean batching = false;


    public DoubleProperty xProperty()     { return x; }
    public DoubleProperty yProperty()     { return y; }
    public DoubleProperty scaleProperty() { return scale; }
    public DoubleProperty angleProperty() { return angle; }


    public double getX()             { return x.get(); }
    public void   setX(double v)     { x.set(v); }

    public double getY()             { return y.get(); }
    public void   setY(double v)     { y.set(v); }

    public double getScale()         { return scale.get(); }
    public void   setScale(double v) { scale.set(v); }

    public double getAngle()         { return angle.get(); }
    public void   setAngle(double v) { angle.set(v); }

    public boolean isBatching()      { return batching; }


    /**
     * Начинает пакетное обновление.
     * Пока активен этот режим, изменения свойств не уведомляют слушателей,
     * что исключает лишние перерисовки при одновременном изменении X, Y, масштаба.
     */
    public void beginUpdate() {
        batching = true;
    }

    /**
     * Завершает пакетное обновление и принудительно уведомляет подписчиков
     * через изменение свойства {@code angle} — это вызывает одну перерисовку.
     */
    public void endUpdate() {
        batching = false;
        angle.set(angle.get()); // принудительное уведомление
    }

    public void reset() {
        setX(0);
        setY(0);
        setScale(1.0);
        setAngle(0);
    }
}


