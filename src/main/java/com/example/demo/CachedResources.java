package com.example.demo;

import java.net.URL;
public class CachedResources {
    private URL url;
    private byte[] data;
    private long expiryTime;  // Milliseconds since the epoch when the data should expire


    public CachedResources(URL url,byte[] data, long expiryTime) {
        this.url = url;
        this.data = data;
        this.expiryTime = expiryTime;
    }

    public byte[] getData() {
        return data;
    }

    public URL getUrl() {
        return url;
    }


    public boolean isExpired() {
        long currentTime = System.currentTimeMillis();
        return currentTime > expiryTime; // If the current time is greater than the expiry time, the data is expired
    }
}

