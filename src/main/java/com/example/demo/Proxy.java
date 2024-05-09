package com.example.demo;

import java.io.*;
import java.net.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;


public class Proxy {
    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_FILE_SIZE = 500 * 1024 * 1024; // 500 MB
    private static Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private static ServerSocket serverSocket;
    static boolean isRunning = false;
    private static FilteredListManager filteredListManager;
    private static List<Socket> activeClientConnections = new ArrayList<>();




    // check proxy is running
    public static boolean isRunning() {
        return isRunning;
    }

    private static void handleClientRequest(Socket clientSocket) throws IOException, SQLException {
        try (BufferedReader clientReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             OutputStream clientOut = clientSocket.getOutputStream()) {

            String requestLine = clientReader.readLine();
            if (requestLine == null) return; // Empty request

            String[] requestParts = requestLine.split("\\s+");
            String method = requestParts[0];
            String url = requestParts[1];
            String host = url.split("/")[2];

            if (!method.equals("GET") && !method.equals("HEAD") && !method.equals("OPTIONS") && !method.equals("POST")) {
                sendErrorResponse(clientOut, 405, "Method Not Allowed");
                return;
            }

            if (filteredListManager.isFilteredHost(host))    {
                sendErrorResponse(clientOut, 401, "Unauthorized");
                return;
            }

            if (method.equals("GET") || method.equals("HEAD")) {
                handleHTTPGet(clientSocket, clientOut, requestLine, host);
            } else if (method.equals("OPTIONS")) {
                // Handle OPTIONS request
            } else if (method.equals("POST")) {
                // Handle POST request
            }
        }
    }

    private static void handleHTTPGet(Socket clientSocket, OutputStream clientOut, String requestLine, String host) throws IOException, SQLException {
        String[] requestParts = requestLine.split("\\s+");
        String url = requestParts[1];

        CacheEntry cachedEntry = cache.get(url);
        if (cachedEntry != null && !isExpired(cachedEntry)) {
            sendCachedResponse(clientOut, cachedEntry);
        } else {
            System.out.println("Fetching from server: " + url);
            // Fetch from server and update cache
            try (Socket serverSocket = new Socket(host, 80)) {
                OutputStream serverOut = serverSocket.getOutputStream();
                serverOut.write(requestLine.getBytes());
                serverOut.flush();

                InputStream serverIn = serverSocket.getInputStream();
                ByteArrayOutputStream responseBuffer = new ByteArrayOutputStream();

                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = serverIn.read(buffer)) != -1) {
                    responseBuffer.write(buffer, 0, bytesRead);
                    if (responseBuffer.size() > MAX_FILE_SIZE) {
                        sendErrorResponse(clientOut, 413, "Request Entity Too Large");
                        return;
                    }
                }

                byte[] responseBytes = responseBuffer.toByteArray();
                clientOut.write(responseBytes);
                clientOut.flush();

                // Update cache
                if (isCacheable(host, responseBytes)) {
                    cache.put(url, new CacheEntry(responseBytes, new Date()));
                    // Store in the database
                    storeInDatabase(url, responseBytes);
                }
            }
        }
    }

    private static boolean isFilteredDomain(String host) {
        // Implement your domain filtering logic here
        return false;
    }

    private static boolean isCacheable(String host, byte[] response) {
        // Implement cacheability check based on response headers
        return true;
    }

    private static boolean isExpired(CacheEntry entry) {
        // Check if the cached entry is expired
        return false;
    }

    private static void sendCachedResponse(OutputStream clientOut, CacheEntry entry) throws IOException {
        // Send cached response to client
        clientOut.write(entry.getResponse());
        clientOut.flush();
    }

    private static void sendErrorResponse(OutputStream clientOut, int statusCode, String statusText) throws IOException {
        String response = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n\r\n";
        clientOut.write(response.getBytes());
        clientOut.flush();
    }

    private static void storeInDatabase(String url, byte[] responseBytes) throws SQLException {
        // Store the response in the database
        try (Connection connection = DatabaseConnection.getConnection()) {
            String sql = "INSERT INTO cached_responses (url, response) VALUES (?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, url);
                statement.setBytes(2, responseBytes);
                statement.executeUpdate();
            }
        }
    }

    private static class CacheEntry {
        private byte[] response;
        private Date lastModified;

        public CacheEntry(byte[] response, Date lastModified) {
            this.response = response;
            this.lastModified = lastModified;
        }

        public byte[] getResponse() {
            return response;
        }

        public Date getLastModified() {
            return lastModified;
        }
    }
}

