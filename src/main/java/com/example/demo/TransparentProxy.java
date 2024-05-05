package com.example.demo;

import javafx.application.Application;

import javafx.stage.Stage;


public class TransparentProxy extends Application {

    private final ProxyManager proxyManager = new ProxyManager();
    private final FilteredListManager filteredListManager = new FilteredListManager();

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setScene(LoginScreen.create(primaryStage));
        primaryStage.setTitle("Transparent Proxy");
        primaryStage.show();
    }

    private void showHomepage(Stage primaryStage) {
        HomepageScreen homepageScreen = new HomepageScreen(primaryStage, proxyManager, filteredListManager);
        primaryStage.setScene(homepageScreen.getScene());
    }

    public static void main(String[] args) {
        launch(args);
    }
}

