package com.example.demo;

import javafx.application.Application;

import javafx.stage.Stage;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


public class TransparentProxy extends Application {

    private final FilteredListManager filteredListManager = new FilteredListManager();
    private ConcurrentMap<String, CachedResources> cache = new ConcurrentHashMap<>();
    private Customer currentCustomer;


    @Override
    public void start(Stage primaryStage) {
        primaryStage.setScene(LoginScreen.create(primaryStage));
        primaryStage.setTitle("Transparent Proxy");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
        System.setProperty("java.net.preferIPv4Stack", "true");
    }
}

