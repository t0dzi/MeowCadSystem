package org.example.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Прямоугольник - геометрический примитив САПР.
 * Определяется центром, шириной и высотой.
 * Поддерживает фаски и скругления углов.
 * 
 * Способы создания:
 * - Две противоположные точки
 * - Одна точка (угол), ширина и высота
 * - Центр, ширина и высота
 * - С фасками/скруглениями при создании
 */
public class Rectangle extends Primitive {

    public enum CreationMode {
        TWO_POINTS,
        CORNER_SIZE,
        CENTER_SIZE
    }

    public enum CornerType {
        SHARP,
        ROUNDED,
        CHAMFERED
    }

    private Point center;
    private double width;
    private double height;
    private double cornerRadius;  // Радиус скругления или размер фаски
    private CornerType cornerType;
    private final CreationMode creationMode;


    public Rectangle(Point center, double width, double height, LineStyle lineStyle) {
        super(lineStyle);
        this.center = center;
        this.width = Math.abs(width);
        this.height = Math.abs(height);
        this.cornerRadius = 0;
        this.cornerType = CornerType.SHARP;
        this.creationMode = CreationMode.CENTER_SIZE;
    }

    public Rectangle(Point center, double width, double height, 
                     double cornerRadius, CornerType cornerType, LineStyle lineStyle) {
        super(lineStyle);
        this.center = center;
        this.width = Math.abs(width);
        this.height = Math.abs(height);
        this.cornerRadius = Math.abs(cornerRadius);
        this.cornerType = cornerType;
        this.creationMode = CreationMode.CENTER_SIZE;
    }

    public static Rectangle fromTwoPoints(Point p1, Point p2, LineStyle lineStyle) {
        double cx = (p1.getX() + p2.getX()) / 2;
        double cy = (p1.getY() + p2.getY()) / 2;
        double w = Math.abs(p2.getX() - p1.getX());
        double h = Math.abs(p2.getY() - p1.getY());
        return new Rectangle(new Point(cx, cy), w, h, lineStyle);
    }

    /**
     * Создаёт прямоугольник по угловой точке и размерам.
     * @param corner левый нижний угол
     */
    public static Rectangle fromCornerAndSize(Point corner, double width, double height, LineStyle lineStyle) {
        double cx = corner.getX() + width / 2;
        double cy = corner.getY() + height / 2;
        return new Rectangle(new Point(cx, cy), width, height, lineStyle);
    }


    @Override
    public Point getCenter() {
        return center;
    }

    public void setCenter(Point center) {
        this.center = center;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = Math.abs(width);
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = Math.abs(height);
    }

    public double getCornerRadius() {
        return cornerRadius;
    }

    public void setCornerRadius(double cornerRadius) {
        // Ограничиваем радиус скругления половиной меньшей стороны
        double maxRadius = Math.min(width, height) / 2;
        this.cornerRadius = Math.min(Math.abs(cornerRadius), maxRadius);
    }

    public CornerType getCornerType() {
        return cornerType;
    }

    public void setCornerType(CornerType cornerType) {
        this.cornerType = cornerType;
    }

    public CreationMode getCreationMode() {
        return creationMode;
    }

    public double getPerimeter() {
        if (cornerType == CornerType.ROUNDED && cornerRadius > 0) {
            return 2 * (width + height) - 8 * cornerRadius + 2 * Math.PI * cornerRadius;
        }
        return 2 * (width + height);
    }

    public double getArea() {
        if (cornerType == CornerType.ROUNDED && cornerRadius > 0) {
            return width * height - (4 - Math.PI) * cornerRadius * cornerRadius;
        } else if (cornerType == CornerType.CHAMFERED && cornerRadius > 0) {
            return width * height - 2 * cornerRadius * cornerRadius;
        }
        return width * height;
    }


    public Point[] getCorners() {
        double halfW = width / 2;
        double halfH = height / 2;
        return new Point[] {
            new Point(center.getX() - halfW, center.getY() - halfH), // Левый нижний
            new Point(center.getX() + halfW, center.getY() - halfH), // Правый нижний
            new Point(center.getX() + halfW, center.getY() + halfH), // Правый верхний
            new Point(center.getX() - halfW, center.getY() + halfH)  // Левый верхний
        };
    }


    @Override
    public PrimitiveType getType() {
        return PrimitiveType.RECTANGLE;
    }

    @Override
    public List<ControlPoint> getControlPoints() {
        List<ControlPoint> points = new ArrayList<>();
        Point[] corners = getCorners();
        
        points.add(new ControlPoint(center, ControlPoint.Type.CENTER, 0, "Центр"));
        
        points.add(new ControlPoint(corners[0], ControlPoint.Type.ENDPOINT, 1, "Левый нижний"));
        points.add(new ControlPoint(corners[1], ControlPoint.Type.ENDPOINT, 2, "Правый нижний"));
        points.add(new ControlPoint(corners[2], ControlPoint.Type.ENDPOINT, 3, "Правый верхний"));
        points.add(new ControlPoint(corners[3], ControlPoint.Type.ENDPOINT, 4, "Левый верхний"));
        
        double halfW = width / 2;
        double halfH = height / 2;
        points.add(new ControlPoint(
            new Point(center.getX() + halfW, center.getY()), 
            ControlPoint.Type.AXIS, 5, "Ширина"
        ));
        points.add(new ControlPoint(
            new Point(center.getX(), center.getY() + halfH), 
            ControlPoint.Type.AXIS, 6, "Высота"
        ));
        
        if (cornerRadius > 0) {
            double cr = cornerRadius;
            points.add(new ControlPoint(
                new Point(center.getX() - halfW + cr, center.getY() - halfH + cr),
                ControlPoint.Type.CHAMFER, 7, "Скругление"
            ));
        }
        
        return points;
    }

    @Override
    public void moveControlPoint(int pointIndex, Point newPosition) {
        double halfW = width / 2;
        double halfH = height / 2;
        
        switch (pointIndex) {
            case 0 -> {
                center = newPosition;
            }
            case 1 -> {
                double newWidth = (center.getX() + halfW) - newPosition.getX();
                double newHeight = (center.getY() + halfH) - newPosition.getY();
                center = new Point(
                    newPosition.getX() + newWidth / 2,
                    newPosition.getY() + newHeight / 2
                );
                width = Math.abs(newWidth);
                height = Math.abs(newHeight);
            }
            case 2 -> {
                double newWidth = newPosition.getX() - (center.getX() - halfW);
                double newHeight = (center.getY() + halfH) - newPosition.getY();
                center = new Point(
                    center.getX() - halfW + newWidth / 2,
                    newPosition.getY() + newHeight / 2
                );
                width = Math.abs(newWidth);
                height = Math.abs(newHeight);
            }
            case 3 -> {
                double newWidth = newPosition.getX() - (center.getX() - halfW);
                double newHeight = newPosition.getY() - (center.getY() - halfH);
                center = new Point(
                    center.getX() - halfW + newWidth / 2,
                    center.getY() - halfH + newHeight / 2
                );
                width = Math.abs(newWidth);
                height = Math.abs(newHeight);
            }
            case 4 -> {
                double newWidth = (center.getX() + halfW) - newPosition.getX();
                double newHeight = newPosition.getY() - (center.getY() - halfH);
                center = new Point(
                    newPosition.getX() + newWidth / 2,
                    center.getY() - halfH + newHeight / 2
                );
                width = Math.abs(newWidth);
                height = Math.abs(newHeight);
            }
            case 5 -> {
                width = Math.abs(newPosition.getX() - center.getX()) * 2;
            }
            case 6 -> {
                height = Math.abs(newPosition.getY() - center.getY()) * 2;
            }
            case 7 -> {
                // Изменение радиуса скругления/фаски
                double dx = Math.abs(newPosition.getX() - (center.getX() - halfW));
                double dy = Math.abs(newPosition.getY() - (center.getY() - halfH));
                setCornerRadius(Math.min(dx, dy));
            }
        }
    }

    @Override
    public void translate(double dx, double dy) {
        center = new Point(center.getX() + dx, center.getY() + dy);
    }

    @Override
    public boolean containsPoint(Point point, double tolerance) {
        Point[] corners = getCorners();
        
        for (int i = 0; i < 4; i++) {
            Point p1 = corners[i];
            Point p2 = corners[(i + 1) % 4];
            if (distanceToSegment(point, p1, p2) < tolerance) {
                return true;
            }
        }
        return false;
    }

    @Override
    public double[] getBoundingBox() {
        double halfW = width / 2;
        double halfH = height / 2;
        return new double[]{
            center.getX() - halfW,
            center.getY() - halfH,
            center.getX() + halfW,
            center.getY() + halfH
        };
    }

    @Override
    public Map<String, Object> getProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put("Центр X", center.getX());
        props.put("Центр Y", center.getY());
        props.put("Ширина", width);
        props.put("Высота", height);
        props.put("Тип углов", cornerType.name());
        props.put("Радиус скругления", cornerRadius);
        props.put("Периметр", getPerimeter());
        props.put("Площадь", getArea());
        return props;
    }

    @Override
    public boolean setProperty(String propertyName, Object value) {
        try {
            switch (propertyName) {
                case "Центр X" -> {
                    double val = parseDouble(value);
                    center = new Point(val, center.getY());
                }
                case "Центр Y" -> {
                    double val = parseDouble(value);
                    center = new Point(center.getX(), val);
                }
                case "Ширина" -> setWidth(parseDouble(value));
                case "Высота" -> setHeight(parseDouble(value));
                case "Радиус скругления" -> setCornerRadius(parseDouble(value));
                case "Тип углов" -> {
                    if (value instanceof CornerType) {
                        cornerType = (CornerType) value;
                    } else {
                        cornerType = CornerType.valueOf(value.toString());
                    }
                }
                default -> { return false; }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private double parseDouble(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(value.toString());
    }
}








