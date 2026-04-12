package org.example.model;

// Хранение 2D координат, X и Y
public class Point {
    private double x;
    private double y;

    public Point(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public static Point fromPolar(double radius, double angleInRadius) {
        double x = radius * Math.cos(angleInRadius);
        double y = radius * Math.sin(angleInRadius);
        return new Point(x, y);
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getRadius() {
        return Math.sqrt(x * x + y * y);
    }

    public double getAngle() {
        return Math.atan2(y, x);
    }
}

