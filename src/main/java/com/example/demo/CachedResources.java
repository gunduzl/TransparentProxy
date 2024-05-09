package com.example.demo;

import java.net.URL;
public class CachedResources {
    private byte[] data; // The data of the cached resource
    private long timestamp; // Timestamp when the data was cached
    private static final long EXPIRATION_TIME = 300000; // 5 minutes in milliseconds

    public CachedResources(byte[] data, long timestamp) {
        this.data = data;
        this.timestamp = timestamp;
    }

    public byte[] getData() {
        return data;
    }

    public boolean isExpired() {
        long duration = System.currentTimeMillis() - timestamp;
        return duration > 300000; // 5 minutes expiration
    }
}
