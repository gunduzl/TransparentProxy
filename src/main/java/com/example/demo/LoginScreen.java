package com.example.demo;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

public class LoginScreen {
    private final Consumer<Stage> homepageCallback;
    private final Scene scene;

    private LoginScreen(Consumer<Stage> homepageCallback, Scene scene) {
        this.homepageCallback = homepageCallback;
        this.scene = scene;
    }

    public static Scene create(Stage primaryStage) {
        TextField nameField = new TextField();
        PasswordField passwordField = new PasswordField();
        Button loginButton = new Button("Login");
        loginButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");

        Label nameLabel = new Label("Username:");
        Label passwordLabel = new Label("Password:");

        VBox loginLayout = new VBox(10);
        GridPane gridPane = new GridPane();
        gridPane.setAlignment(Pos.CENTER);
        gridPane.setHgap(10);
        gridPane.setVgap(10);
        gridPane.setPadding(new Insets(20));

        gridPane.add(nameLabel, 0, 0);
        gridPane.add(nameField, 1, 0);
        gridPane.add(passwordLabel, 0, 1);
        gridPane.add(passwordField, 1, 1);
        gridPane.add(loginButton, 1, 2);

        loginButton.setOnAction(e -> handleLogin(primaryStage, nameField, passwordField));

        loginLayout.getChildren().add(gridPane);

        Scene scene = new Scene(loginLayout, 400, 300);
        scene.getStylesheets().add(LoginScreen.class.getResource("styles.css").toExternalForm());

        return new LoginScreen(stage -> {}, scene).scene;
    }

    private static void handleLogin(Stage primaryStage, TextField nameField, PasswordField passwordField) {
        String username = nameField.getText().trim();
        String password = passwordField.getText();

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT id, username, password FROM customer WHERE username = ? AND password = ?")) {
            statement.setString(1, username);
            statement.setString(2, password);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                int customerId = resultSet.getInt("id");
                // Successful login
                ConcurrentMap<String, CachedResources> cache = new ConcurrentHashMap<>();// Create or get your cache here

                // Assuming `isLoginBefore` is true and no `RequestLogEntry` initially.
                // Now using fetched 'id' to construct the Customer.
                Customer customer = new Customer(customerId, username, password, true, null);

                HomepageScreen homepageScreen = new HomepageScreen(primaryStage, new FilteredListManager(), cache, customer);
                homepageScreen.show(); // Show the HomepageScreen
            } else {
                // Invalid credentials
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText(null);
                alert.setContentText("Invalid username or password.");
                alert.showAndWait();
            }
        } catch (SQLException ex) {
            Alert errorAlert = new Alert(Alert.AlertType.ERROR);
            errorAlert.setTitle("Database Error");
            errorAlert.setHeaderText(null);
            errorAlert.setContentText("A database error occurred.");
            errorAlert.showAndWait();
            ex.printStackTrace(); // Consider logging this error as well
        }
    }
}
