package com.papeleriarosita;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class App extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
            App.class.getResource("/com/papeleriarosita/fxml/main-view.fxml")
        );

        Scene scene = new Scene(loader.load(), 1040, 700);

        scene.getStylesheets().add(
            App.class.getResource("/com/papeleriarosita/css/styles.css").toExternalForm()
        );

        stage.setTitle("Papelería Rosita - Inicio de Sesión");
        stage.setMinWidth(920);
        stage.setMinHeight(620);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
