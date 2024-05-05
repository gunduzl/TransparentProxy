package com.example.demo;

public class ProxyManager {

    private Proxy proxy;

    public ProxyManager() {
        this.proxy = new Proxy();
    }

    public void start(int port) {
        if (!proxy.isRunning()) {
            proxy.startProxy();
        } else {
            System.out.println("Proxy is already running.");
        }
    }

    public void stop() {
        if (proxy.isRunning()) {
            proxy.stopProxy();
            System.out.println("Proxy server stopped.");
        } else {
            System.out.println("Proxy server is not running.");
        }
    }

    public boolean isProxyRunning() {
        return proxy.isRunning();
    }
}
