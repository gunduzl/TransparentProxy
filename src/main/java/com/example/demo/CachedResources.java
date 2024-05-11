package com.example.demo;

import java.net.URL;
public class CachedResources {
    private byte[] data;
    private long lastModifiedTimestamp;
    private long timestamp;

    public CachedResources(byte[] data, long lastModifiedTimestamp) {
        this.data = data;
        this.lastModifiedTimestamp = lastModifiedTimestamp;
        this.timestamp = System.currentTimeMillis();
    }

    public byte[] getData() {
        return data;
    }

    public long getLastModifiedTimestamp() {
        return lastModifiedTimestamp;
    }

    public boolean isExpired() {
        long duration = System.currentTimeMillis() - timestamp;
        return duration > 300000; // 5 minutes expiration
    }
}

