package org.example.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.example.model.LineStyle;
import org.example.model.LineType;
import org.example.model.StyleManager;


public class StyleEditorDialog {

    private final StyleManager styleManager;
    private final Stage stage;
    private Runnable onUpdate;

    public StyleEditorDialog(StyleManager styleManager) {
        this.styleManager = styleManager;
        this.stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Редактор стилей линий");
    }

    public void setOnUpdate(Runnable onUpdate) {
        this.onUpdate = onUpdate;
    }

    public void show() {
        TableView<LineStyle> table = new TableView<>(styleManager.getStyles());

        TableColumn<LineStyle, String> nameCol = new TableColumn<>("Название");
        nameCol.setCellValueFactory(cell -> cell.getValue().nameProperty());
        nameCol.setPrefWidth(160);

        TableColumn<LineStyle, String> typeCol = new TableColumn<>("Тип");
        typeCol.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().getType().getDisplayName()));
        typeCol.setPrefWidth(100);

        TableColumn<LineStyle, String> thickCol = new TableColumn<>("Толщина");
        thickCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                String.format("%.1f мм", cell.getValue().getThicknessMm())));
        thickCol.setPrefWidth(80);

        TableColumn<LineStyle, String> catCol = new TableColumn<>("Категория");
        catCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                cell.getValue().getThicknessCategory().getDisplayName()));
        catCol.setPrefWidth(100);

        table.getColumns().addAll(nameCol, typeCol, thickCol, catCol);

        TextField nameField = new TextField();
        nameField.setPromptText("Название стиля");

        ComboBox<LineType> typeBox = new ComboBox<>();
        typeBox.getItems().addAll(LineType.values());
        typeBox.setValue(LineType.SOLID);
        typeBox.setPrefWidth(150);

        ComboBox<LineStyle.ThicknessCategory> thickCatBox = new ComboBox<>();
        thickCatBox.getItems().addAll(LineStyle.ThicknessCategory.values());
        thickCatBox.setValue(LineStyle.ThicknessCategory.THIN);
        thickCatBox.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(LineStyle.ThicknessCategory item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getDisplayName());
            }
        });
        thickCatBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(LineStyle.ThicknessCategory item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getDisplayName());
            }
        });

        Spinner<Double> dashLengthSpinner = new Spinner<>(1.0, 50.0, 6.0, 1.0);
        dashLengthSpinner.setEditable(true);
        dashLengthSpinner.setPrefWidth(80);

        Spinner<Double> dashGapSpinner = new Spinner<>(1.0, 20.0, 3.0, 0.5);
        dashGapSpinner.setEditable(true);
        dashGapSpinner.setPrefWidth(80);

        Label baseThickLabel = new Label(String.format("Базовая толщина s: %.2f мм",
                styleManager.getBaseThicknessMm()));
        Slider baseThickSlider = new Slider(StyleManager.MIN_THICK_MM, StyleManager.MAX_THICK_MM,
                styleManager.getBaseThicknessMm());
        baseThickSlider.setShowTickLabels(true);
        baseThickSlider.setShowTickMarks(true);
        baseThickSlider.setMajorTickUnit(0.3);
        baseThickSlider.valueProperty().addListener((obs, oldV, newV) -> {
            styleManager.setBaseThicknessMm(newV.doubleValue());
            baseThickLabel.setText(String.format("Базовая толщина s: %.2f мм", newV.doubleValue()));
            table.refresh();
            if (onUpdate != null)
                onUpdate.run();
        });

        Button addButton = new Button("Создать");
        Button editButton = new Button("Сохранить");
        Button deleteButton = new Button("Удалить");

        editButton.setDisable(true);
        deleteButton.setDisable(true);

        table.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                nameField.setText(newVal.getName());
                typeBox.setValue(newVal.getType());
                thickCatBox.setValue(newVal.getThicknessCategory());
                dashLengthSpinner.getValueFactory().setValue(newVal.getDashLengthMm());
                dashGapSpinner.getValueFactory().setValue(newVal.getDashGapMm());

                boolean isSystem = newVal.isSystem();
                nameField.setDisable(isSystem);
                typeBox.setDisable(isSystem);
                deleteButton.setDisable(isSystem);
                editButton.setDisable(false);
            } else {
                editButton.setDisable(true);
                deleteButton.setDisable(true);
            }
        });

        addButton.setOnAction(e -> {
            try {
                styleManager.createCustomStyle(
                        nameField.getText(),
                        typeBox.getValue(),
                        thickCatBox.getValue(),
                        dashLengthSpinner.getValue(),
                        dashGapSpinner.getValue());
                nameField.clear();
                if (onUpdate != null)
                    onUpdate.run();
            } catch (Exception ex) {
                showAlert("Ошибка создания стиля");
            }
        });

        editButton.setOnAction(e -> {
            LineStyle sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) {
                try {
                    if (!sel.isSystem()) {
                        sel.setName(nameField.getText());
                    }
                    styleManager.updateStyleParameters(sel,
                            dashLengthSpinner.getValue(),
                            dashGapSpinner.getValue());

                    table.refresh();
                    if (onUpdate != null)
                        onUpdate.run();
                } catch (Exception ex) {
                    showAlert("Ошибка редактирования");
                }
            }
        });

        deleteButton.setOnAction(e -> {
            LineStyle sel = table.getSelectionModel().getSelectedItem();
            if (sel != null && !sel.isSystem()) {
                styleManager.removeStyle(sel);
                if (onUpdate != null)
                    onUpdate.run();
            }
        });

        GridPane paramsGrid = new GridPane();
        paramsGrid.setHgap(10);
        paramsGrid.setVgap(8);
        paramsGrid.add(new Label("Название:"), 0, 0);
        paramsGrid.add(nameField, 1, 0);
        paramsGrid.add(new Label("Тип:"), 0, 1);
        paramsGrid.add(typeBox, 1, 1);
        paramsGrid.add(new Label("Толщина:"), 0, 2);
        paramsGrid.add(thickCatBox, 1, 2);
        paramsGrid.add(new Label("Длина штриха (мм):"), 0, 3);
        paramsGrid.add(dashLengthSpinner, 1, 3);
        paramsGrid.add(new Label("Пробел (мм):"), 0, 4);
        paramsGrid.add(dashGapSpinner, 1, 4);

        HBox buttonsBox = new HBox(10, addButton, editButton, deleteButton);
        buttonsBox.setAlignment(Pos.CENTER_LEFT);

        VBox baseThickBox = new VBox(5, baseThickLabel, baseThickSlider);
        baseThickBox.setStyle("-fx-border-color: #ccc; -fx-border-width: 1; -fx-padding: 10;");

        VBox inputs = new VBox(10,
                new Label("Параметры стиля:"),
                paramsGrid,
                buttonsBox,
                new Separator(),
                new Label("Глобальные настройки:"),
                baseThickBox);
        inputs.setPadding(new Insets(10));

        VBox root = new VBox(10, table, inputs);
        root.setPadding(new Insets(10));
        stage.setScene(new Scene(root, 500, 600));
        stage.show();
    }

    private void showAlert(String message) {
        new Alert(Alert.AlertType.ERROR, message).showAndWait();
    }
}

