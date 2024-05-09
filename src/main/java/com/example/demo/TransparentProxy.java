package com.example.demo;

import javafx.application.Application;

import javafx.stage.Stage;

import java.util.HashMap;
import java.util.Map;


public class TransparentProxy extends Application {

    private final ProxyManager proxyManager = new ProxyManager();
    private final FilteredListManager filteredListManager = new FilteredListManager();
    private Map<String, CachedResources> cache = new HashMap<>();


    @Override
    public void start(Stage primaryStage) {
        primaryStage.setScene(LoginScreen.create(primaryStage));
        primaryStage.setTitle("Transparent Proxy");
        primaryStage.show();
    }



    private void showHomepage(Stage primaryStage) {
        HomepageScreen homepageScreen = new HomepageScreen(primaryStage, proxyManager, filteredListManager, cache);
        primaryStage.setScene(homepageScreen.getScene());
        
    }
    public static void main(String[] args) {
        launch(args);
        System.setProperty("java.net.preferIPv4Stack", "true");
    }
}

