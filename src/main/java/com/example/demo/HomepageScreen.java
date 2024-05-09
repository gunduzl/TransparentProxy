package com.example.demo;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Optional;

public class HomepageScreen {

    private final Scene scene;
    private final Stage primaryStage;
    private ProxyManager proxyManager;
    private FilteredListManager filteredListManager;
    private ServerSocket serverSocket;
    private volatile boolean isRunning = false;
    private Thread proxyThread;

    public HomepageScreen(Stage primaryStage, ProxyManager proxyManager, FilteredListManager filteredListManager) {
        this.primaryStage = primaryStage;
        this.proxyManager = proxyManager;
        this.filteredListManager = filteredListManager;

        Label statusLabel = new Label("Proxy Status: Stopped");
        Button startButton = new Button("Start Proxy");
        Button stopButton = new Button("Stop Proxy");

        VBox homepageLayout = new VBox(10);
        homepageLayout.setAlignment(Pos.CENTER);
        homepageLayout.getChildren().addAll(statusLabel, startButton, stopButton);

        BorderPane mainLayout = new BorderPane();
        Menu fileMenu = createFileMenu();
        Menu helpMenu = createHelpMenu();
        mainLayout.setTop(new MenuBar(fileMenu, helpMenu));
        mainLayout.setCenter(homepageLayout);

        this.scene = new Scene(mainLayout, 400, 300);
        this.scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());

        startButton.setOnAction(e -> startProxy());
        stopButton.setOnAction(e -> stopProxy());
    }

    public void show() {
        primaryStage.setScene(scene);
    }

    private void startProxy() {
        int port = 8080; // Default port number
        try {
            serverSocket = new ServerSocket(port);
            updateStatus("Proxy Status: Starting...");

            proxyThread = new Thread(() -> {
                try {
                    isRunning = true;
                    while (isRunning && !Thread.currentThread().isInterrupted()) {
                        try {
                            Socket incoming = serverSocket.accept();
                            new ServerHandler(incoming,filteredListManager).start();
                        } catch (IOException e) {
                            if (isRunning) { // Only log unexpected errors.
                                logError("Error accepting connection: " + e.getMessage());
                            }
                        }
                    }
                } finally {
                    updateStatus("Proxy Status: Stopped");
                }
            });
            proxyThread.start();
            updateStatus("Proxy Status: Running on port " + port);
        } catch (IOException e) {
            logError("Error starting proxy server: " + e.getMessage());
            updateStatus("Proxy Status: Failed to start");
        }
    }

    private void updateStatus(String message) {
        Platform.runLater(() -> {
            Label statusLabel = (Label)((VBox)((BorderPane)scene.getRoot()).getCenter()).getChildren().get(0);
            statusLabel.setText(message);
        });
    }

    private void logError(String error) {
        System.out.println(error);  // Replace this with a more robust logging approach
    }


    private void stopProxy() {
        if (!isRunning) {
            updateStatus("Proxy Status: Already Stopped");
            return;
        }

        try {
            isRunning = false; // Signal the thread to stop accepting new connections
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close(); // This will cause serverSocket.accept() to throw a SocketException
            }
            if (proxyThread != null && proxyThread.isAlive()) {
                proxyThread.interrupt(); // Ensure any blocking operations are interrupted
                proxyThread.join(3000); // Wait for the thread to finish with a timeout
            }
            updateStatus("Proxy Status: Stopped");
        } catch (IOException | InterruptedException e) {
            logError("Error occurred while closing the proxy: " + e.getMessage());
            updateStatus("Proxy Status: Error stopping");
        }
    }



    private Menu createFileMenu() {
        Menu fileMenu = new Menu("File");

        MenuItem startItem = new MenuItem("Start Proxy");
        startItem.setOnAction(e -> startProxy());

        MenuItem stopItem = new MenuItem("Stop Proxy");
        stopItem.setOnAction(e -> stopProxy());

        MenuItem reportItem = new MenuItem("Report");
        reportItem.setOnAction(e -> displayReport());

        MenuItem addHostItem = new MenuItem("Add Host to Filter");
        addHostItem.setOnAction(e -> addHostToFilter());

        MenuItem displayFilterItem = new MenuItem("Display Current Filtered Hosts");
        displayFilterItem.setOnAction(e -> displayFilteredHosts());

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> primaryStage.close());

        fileMenu.getItems().addAll(startItem, stopItem, reportItem, addHostItem, displayFilterItem, new SeparatorMenuItem(), exitItem);
        return fileMenu;
    }

    private void addHostToFilter() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Host to Filter");
        dialog.setHeaderText("Enter host to filter:");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(host -> {
            filteredListManager.addHost(host);
            // Show confirmation message
            Alert confirmation = new Alert(Alert.AlertType.INFORMATION);
            confirmation.setTitle("Host Added");
            confirmation.setHeaderText(null);
            confirmation.setContentText("Host '" + host + "' has been added to the filter list.");
            confirmation.showAndWait();
        });
    }

    private void displayFilteredHosts() {
        List<String> filteredHosts = filteredListManager.getFilteredHosts();
        StringBuilder message = new StringBuilder("Filtered Hosts:\n");
        for (String host : filteredHosts) {
            message.append(host).append("\n");
        }
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Filtered Hosts");
        alert.setHeaderText(null);
        alert.setContentText(message.toString());
        alert.showAndWait();
    }

    private Menu createHelpMenu() {
        Menu helpMenu = new Menu("Help");
        MenuItem aboutItem = new MenuItem("About");
        aboutItem.setOnAction(e -> showAbout());
        helpMenu.getItems().add(aboutItem);
        return helpMenu;
    }

    private void showAbout() {
        // Logic to display about information
        // This could involve showing a popup or navigating to a new scene
    }

    private void displayReport() {
        /*
         Logic to display report
         This could involve showing a popup or navigating to a new scene
        */
    }

    public Scene getScene() {
        return scene;
    }
}
