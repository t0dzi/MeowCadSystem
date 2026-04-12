package org.example.view;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import org.example.model.LineStyle;
import org.example.model.LineType;


public class StyleListCell extends ListCell<LineStyle> {

    private static final double CANVAS_WIDTH = 80;
    private static final double CANVAS_HEIGHT = 20;

    @Override
    protected void updateItem(LineStyle item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setGraphic(null);
            setText(null);
        } else {
            HBox hbox = new HBox(10);
            Canvas canvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT);
            GraphicsContext gc = canvas.getGraphicsContext2D();

            double centerY = CANVAS_HEIGHT / 2;
            gc.setStroke(Color.BLACK);
            gc.setLineWidth(Math.min(item.getThickness(), 3.0));

            LineType type = item.getType();

            switch (type) {
                case WAVY -> {
                    gc.setLineDashes((double[]) null);
                    gc.beginPath();
                    gc.moveTo(0, centerY);
                    for (double x = 0; x <= CANVAS_WIDTH; x += 2) {
                        gc.lineTo(x, centerY + 4 * Math.sin(x * 0.3));
                    }
                    gc.stroke();
                }
                case ZIGZAG -> {
                    gc.setLineDashes((double[]) null);
                    gc.beginPath();
                    gc.moveTo(0, centerY);
                    double center = CANVAS_WIDTH / 2;
                    double size = 6;
                    gc.lineTo(center - 10, centerY);
                    gc.lineTo(center - 3, centerY - size);
                    gc.lineTo(center + 3, centerY + size);
                    gc.lineTo(center + 10, centerY);
                    gc.lineTo(CANVAS_WIDTH, centerY);
                    gc.stroke();
                }
                default -> {
                    double[] pattern = item.getDashPattern();
                    if (pattern != null) {
                        gc.setLineDashes(pattern);
                    } else {
                        gc.setLineDashes((double[]) null);
                    }
                    gc.strokeLine(0, centerY, CANVAS_WIDTH, centerY);
                }
            }

            String displayName = item.getName();
            Label label = new Label(displayName);

            hbox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            hbox.getChildren().addAll(canvas, label);

            setGraphic(hbox);
            setText(null);
        }
    }
}

