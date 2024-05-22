package com.example.demo;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;

public class HomepageScreen {
    private final Scene scene;
    private final Stage primaryStage;
    private FilteredListManager filteredListManager;
    private ServerSocket httpServerSocket;
    private ServerSocket httpsServerSocket;
    private volatile boolean isRunning = false;
    private Thread httpProxyThread;
    private Thread httpsProxyThread;
    private final TextArea logTextArea;
    private ConcurrentMap<String, CachedResources> cache;
    private final Customer currentCustomer;

    public HomepageScreen(Stage primaryStage, FilteredListManager filteredListManager, ConcurrentMap<String, CachedResources> cache, Customer currentCustomer) {
        this.primaryStage = primaryStage;
        this.filteredListManager = filteredListManager;
        this.cache = cache;
        this.currentCustomer = currentCustomer;
        this.logTextArea = new TextArea();

        configureTextArea();
        VBox homepageLayout = configureLayout();
        this.scene = new Scene(new BorderPane(homepageLayout, new MenuBar(createFileMenu(), createHelpMenu()), null, null, null), 400, 300);
        this.scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
    }

    private void configureTextArea() {
        logTextArea.setEditable(false);
        logTextArea.setWrapText(true);
        VBox.setVgrow(logTextArea, Priority.ALWAYS);  // Make TextArea grow vertically
    }

    private VBox configureLayout() {
        Label statusLabel = new Label("Proxy Status: Stopped");
        Button startButton = new Button("Start Proxy");
        Button stopButton = new Button("Stop Proxy");
        startButton.setOnAction(e -> startProxy(statusLabel));
        stopButton.setOnAction(e -> stopProxy(statusLabel));

        VBox layout = new VBox(10, statusLabel, startButton, stopButton, logTextArea);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(10));
        return layout;
    }

    private void startProxy(Label statusLabel) {
        try {
            httpServerSocket = new ServerSocket(80);
            httpsServerSocket = new ServerSocket(443);
            isRunning = true;
            updateStatus(statusLabel, "Proxy Status: Starting...");

            // HTTP Proxy Thread
            httpProxyThread = new Thread(() -> {
                try {
                    while (isRunning && !Thread.currentThread().isInterrupted()) {
                        Socket incoming = httpServerSocket.accept();
                        new ServerHandler(incoming, filteredListManager, logTextArea, cache, currentCustomer).start();
                    }
                } catch (IOException e) {
                    if (isRunning) { // Only log unexpected errors.
                        logError("Error accepting connection on HTTP port: " + e.getMessage());
                    }
                } finally {
                    updateStatus(statusLabel, "Proxy Status: Stopped");
                    appendToLog("HTTP Proxy server stopped");
                }
            });

            // HTTPS Proxy Thread
            httpsProxyThread = new Thread(() -> {
                try {
                    while (isRunning && !Thread.currentThread().isInterrupted()) {
                        Socket incoming = httpsServerSocket.accept();
                        new ServerHandler(incoming, filteredListManager, logTextArea, cache, currentCustomer, true).start();
                    }
                } catch (IOException e) {
                    if (isRunning) { // Only log unexpected errors.
                        logError("Error accepting connection on HTTPS port: " + e.getMessage());
                    }
                } finally {
                    updateStatus(statusLabel, "Proxy Status: Stopped");
                    appendToLog("HTTPS Proxy server stopped");
                }
            });

            httpProxyThread.start();
            httpsProxyThread.start();
            updateStatus(statusLabel, "Proxy Status: Running on ports 80 (HTTP) and 443 (HTTPS)");
            appendToLog("Proxy server started on ports 80 (HTTP) and 443 (HTTPS)");
        } catch (IOException e) {
            logError("Error starting proxy server: " + e.getMessage());
            updateStatus(statusLabel, "Proxy Status: Failed to start");
        }
    }

    private void stopProxy(Label statusLabel) {
        if (!isRunning) {
            updateStatus(statusLabel, "Proxy Status: Already Stopped");
            return;
        }

        try {
            isRunning = false; // Signal the threads to stop
            if (httpServerSocket != null && !httpServerSocket.isClosed()) {
                httpServerSocket.close();
            }
            if (httpsServerSocket != null && !httpsServerSocket.isClosed()) {
                httpsServerSocket.close();
            }
            if (httpProxyThread != null && httpProxyThread.isAlive()) {
                httpProxyThread.interrupt();
                httpProxyThread.join(3000); // Wait for the thread to finish
            }
            if (httpsProxyThread != null && httpsProxyThread.isAlive()) {
                httpsProxyThread.interrupt();
                httpsProxyThread.join(3000); // Wait for the thread to finish
            }
            updateStatus(statusLabel, "Proxy Status: Stopped");
        } catch (IOException | InterruptedException e) {
            logError("Error occurred while closing the proxy: " + e.getMessage());
            updateStatus(statusLabel, "Proxy Status: Error stopping");
        }
    }

    private void updateStatus(Label statusLabel, String message) {
        Platform.runLater(() -> statusLabel.setText(message));
    }

    private void logError(String error) {
        System.out.println(error);  // Replace this with a more robust logging approach
    }

    private Menu createFileMenu() {
        Menu fileMenu = new Menu("File");

        MenuItem startItem = new MenuItem("Start Proxy");
        startItem.setOnAction(e -> startProxy((Label) ((VBox) ((BorderPane) scene.getRoot()).getCenter()).getChildren().get(0)));

        MenuItem stopItem = new MenuItem("Stop Proxy");
        stopItem.setOnAction(e -> stopProxy((Label) ((VBox) ((BorderPane) scene.getRoot()).getCenter()).getChildren().get(0)));

        MenuItem reportItem = new MenuItem("Report");
        reportItem.setOnAction(e -> displayReport(currentCustomer.getUsername()));

        MenuItem addHostItem = new MenuItem("Add Host to Filter");
        addHostItem.setOnAction(e -> addHostToFilter());

        MenuItem removeHostItem = new MenuItem("Remove Host from Filter");
        removeHostItem.setOnAction(e -> removeHostFromFilter());

        MenuItem displayFilterItem = new MenuItem("Display Current Filtered Hosts");
        displayFilterItem.setOnAction(e -> displayFilteredHosts());

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> primaryStage.close());

        fileMenu.getItems().addAll(startItem, stopItem, reportItem, addHostItem, displayFilterItem, removeHostItem , new SeparatorMenuItem(), exitItem);
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

    private void removeHostFromFilter() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Remove Host from Filter");
        dialog.setHeaderText("Enter host to remove from filter:");
        Optional<String> result = dialog.showAndWait();

        result.ifPresent(host -> {
            if (filteredListManager.isHostExist(host)) {
                boolean isRemoved = filteredListManager.removeHost(host);  // Attempt to remove the host
                if (isRemoved) {
                    // Show confirmation message that the host was successfully removed
                    Alert confirmation = new Alert(Alert.AlertType.INFORMATION);
                    confirmation.setTitle("Host Removed");
                    confirmation.setHeaderText(null);
                    confirmation.setContentText("Host '" + host + "' has been successfully removed from the filter list.");
                    confirmation.showAndWait();
                } else {
                    // Show an error message if the removal failed
                    Alert error = new Alert(Alert.AlertType.ERROR);
                    error.setTitle("Removal Failed");
                    error.setHeaderText(null);
                    error.setContentText("Failed to remove the host '" + host + "' from the filter list. Please try again.");
                    error.showAndWait();
                }
            } else {
                // Show error message if the host is not found in the list
                Alert error = new Alert(Alert.AlertType.ERROR);
                error.setTitle("Host Not Found");
                error.setHeaderText(null);
                error.setContentText("Host '" + host + "' was not found in the filter list.");
                error.showAndWait();
            }
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

    private void displayReport(String customerUsername) {
        // Logic to fetch log entries from the database
        try (Connection connection = DatabaseConnection.getConnection()) {
            String sql = "SELECT r.date, r.client_ip, r.domain, r.resource_path, r.method, r.status_code " +
                    "FROM request_logs r " +
                    "JOIN Customer c ON r.customer_id = c.id " +
                    "WHERE c.username = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, customerUsername);
                try (ResultSet resultSet = statement.executeQuery()) {
                    StringBuilder reportBuilder = new StringBuilder();
                    while (resultSet.next()) {
                        // Retrieve data from the result set
                        Date date = resultSet.getDate("date");
                        String clientIP = resultSet.getString("client_ip");
                        String domain = resultSet.getString("domain");
                        String resourcePath = resultSet.getString("resource_path");
                        String method = resultSet.getString("method");
                        int statusCode = resultSet.getInt("status_code");
                        // Append data to the report string
                        reportBuilder.append("Date: ").append(date).append("\n");
                        reportBuilder.append("Client IP: ").append(clientIP).append("\n");
                        reportBuilder.append("Domain: ").append(domain).append("\n");
                        reportBuilder.append("Resource Path: ").append(resourcePath).append("\n");
                        reportBuilder.append("Method: ").append(method).append("\n");
                        reportBuilder.append("Status Code: ").append(statusCode).append("\n");
                        reportBuilder.append("\n"); // Add a newline between entries
                    }
                    // Create a scrollable text area and set it to the content of the report
                    TextArea textArea = new TextArea(reportBuilder.toString());
                    textArea.setEditable(false);
                    textArea.setWrapText(true);
                    textArea.setMaxWidth(Double.MAX_VALUE);
                    textArea.setMaxHeight(Double.MAX_VALUE);
                    GridPane.setVgrow(textArea, Priority.ALWAYS);
                    GridPane.setHgrow(textArea, Priority.ALWAYS);

                    GridPane expContent = new GridPane();
                    expContent.setMaxWidth(Double.MAX_VALUE);
                    expContent.add(textArea, 0, 0);

                    // Show the report in a popup dialog with a scrollable area
                    Alert reportAlert = new Alert(Alert.AlertType.INFORMATION);
                    reportAlert.setTitle("Request Log Report");
                    reportAlert.setHeaderText("Log Report for " + customerUsername);
                    reportAlert.getDialogPane().setContent(expContent);
                    reportAlert.showAndWait();
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        } catch (SQLException e) {
            logError("Error fetching log entries from the database: " + e.getMessage());
            // Handle the exception (e.g., show an error message to the user)
        }
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

    private void appendToLog(String message) {
        Platform.runLater(() -> logTextArea.appendText(message + "\n"));
    }

    public void show() {
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}
