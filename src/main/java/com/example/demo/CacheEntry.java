package com.example.demo;

import java.util.Date;

public class CacheEntry {

    private final byte[] responseData;
    private final Date creationTime;

    public CacheEntry(byte[] responseData, Date creationTime) {
        this.responseData = responseData;
        this.creationTime = creationTime;
    }

    public byte[] getResponseData() {
        return responseData;
    }

    public Date getCreationTime() {
        return creationTime;
    }

    public static void storeInDatabase(CacheEntry entry, String url) {
        byte[] responseData = entry.getResponseData();
        Date creationTime = entry.getCreationTime();
        DatabaseConnection databaseConnection = new DatabaseConnection();
        databaseConnection.storeInDatabase(url, responseData, creationTime);
    }
}
