package com.example.demo;

public class ProxyManager {

    private boolean proxyRunning = false;

    public void start() {
        if (!proxyRunning) {
            // Start proxy
            proxyRunning = true;
        }
    }

    public void stop() {
        if (proxyRunning) {
            // Stop proxy
            proxyRunning = false;
        }
    }
}
