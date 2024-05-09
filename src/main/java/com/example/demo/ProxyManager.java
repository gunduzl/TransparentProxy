package com.example.demo;


import java.util.HashMap;
import java.util.Map;

public class ProxyManager {

    private final Map<String, CacheEntry> cache;

    public ProxyManager() {
        this.cache = new HashMap<>();
    }

    public synchronized CacheEntry getCacheEntry(String url) {
        return cache.get(url);
    }

    public synchronized void putCacheEntry(String url, CacheEntry cacheEntry) {
        cache.put(url, cacheEntry);
    }

    public synchronized void clearCache() {
        cache.clear();
    }

}
