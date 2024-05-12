package com.example.demo;

import javafx.application.Application;

import javafx.stage.Stage;


public class TransparentProxy extends Application {

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

