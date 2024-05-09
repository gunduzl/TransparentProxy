package com.example.demo;

import javafx.application.Platform;
import javafx.scene.control.TextArea;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.*;

public class ServerHandler extends Thread {
    private final Socket conSock;
    private final FilteredListManager filteredListManager;
    private DataInputStream ServerStream;
    private DataOutputStream ClientStream;
    private static final int BUFFER_SIZE = 8192; //
    private static final int MAX_FILE_SIZE = 500 * 1024 * 1024; // 500 MB
    private static final int MAX_CACHE_SIZE = 100; // Maximum number of cached resources
    private final TextArea logTextArea;
    private Map<String, CachedResources> cache;

    // Caching mechanism
    private static final String CACHE_DIR = "./cache";
    private Map<String, File> cacheMap = new HashMap<>();

    public ServerHandler(Socket c, FilteredListManager filteredListManager, TextArea logTextArea, Map<String, CachedResources> cache) {
        conSock = c;
        this.filteredListManager = filteredListManager;
        this.logTextArea = logTextArea;
        this.cache = cache;
        System.setProperty("java.net.preferIPv4Stack", "true");
        try {
            ServerStream = new DataInputStream(conSock.getInputStream());
            ClientStream = new DataOutputStream(conSock.getOutputStream());
            System.out.println("ServerHandler initialized for: " + conSock);
            appendToLog("ServerHandler initialized for: " + conSock);
        } catch (IOException e) {
            System.err.println("Error initializing ServerHandler: " + e.getMessage());
            try {
                conSock.close();
            } catch (IOException ex) {
                System.err.println("Error closing socket: " + ex.getMessage());
            }
        }
    }

    @Override
    public void run() {
        try {
            System.out.println("Handling connection from " + conSock.getInetAddress().getHostAddress());
            appendToLog("Handling connection from " + conSock.getInetAddress().getHostAddress());
            String header = readHeader(ServerStream);
            System.out.println("Received header: " + header);
            appendToLog("Received header: " + header);

            String method = extractMethod(header);
            String path = extractPath(header);
            String restHeader = extractRestHeader(header);
            String host = extractHost(restHeader);

            if (host == null) {
                host = "example.com";
            }

            String fullUrl = "http://" + host + path;
            URL url = new URL(fullUrl);

            String domain = url.getHost();
            String urlPath = url.getPath();

            if (filteredListManager.isFilteredHost(domain)) {
                sendUnauthorizedResponse(domain);
                return;
            }

            System.out.println("Request method: " + method + ", Domain: " + domain + ", Path: " + urlPath);
            appendToLog("Request method: " + method + ", Domain: " + domain + ", Path: " + urlPath);

            switch (method.toUpperCase()) {
                case "GET":
                case "HEAD":
                    handleGetHeadRequest(method, domain, urlPath, restHeader);
                    break;
                case "POST":
                    handlePostRequest(domain, urlPath, restHeader);
                    break;
                case "OPTIONS":
                    handleOptionsRequest(domain, urlPath, restHeader);
                    break;
                default:
                    sendMethodNotAllowed();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendToClient(URL url, byte[] cachedData) throws IOException {
        try (Socket socket = new Socket(url.getHost(), url.getPort() == -1 ? 80 : url.getPort());
             InputStream serverInput = cachedData == null ? socket.getInputStream() : new ByteArrayInputStream(cachedData);
             OutputStream serverOutput = socket.getOutputStream();
             PrintWriter writer = new PrintWriter(serverOutput, true)) {

            if (cachedData == null) {
                writer.println("GET " + url.getFile() + " HTTP/1.1");
                writer.println("Host: " + url.getHost());
                writer.println("Connection: close");
                writer.println();
            }

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = serverInput.read(buffer)) != -1) {
                ClientStream.write(buffer, 0, bytesRead);
            }
            ClientStream.flush();
        } finally {
            conSock.close();
        }
    }

    private String extractMethod(String header) {
        int firstSpace = header.indexOf(' ');
        return header.substring(0, firstSpace);
    }



    private String extractRestHeader(String header) {
        int secondLine = header.indexOf('\r') + 2;
        return header.substring(secondLine);
    }

    private String extractHost(String headers) {
        String[] lines = headers.split("\r\n");
        for (String line : lines) {
            if (line.startsWith("Host:")) {
                return line.substring(5).trim(); // Assuming there's always a space after "Host:"
            }
        }
        return null;
    }

    private void sendUnauthorizedResponse(String domain) throws IOException {
        String html = "<html><body><h1>Access to " + domain + " is not allowed!</h1></body></html>";
        String response = "HTTP/1.1 401 Not Authorized\r\n"
                + "Date: " + new Date() + "\r\n"
                + "Server: Custom Proxy Server\r\n"
                + "Content-Length: " + html.length() + "\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n\r\n" + html;
        ClientStream.writeBytes(response);
        conSock.close();
        appendToLog("Unauthorized access attempt to domain: " + domain);
    }

    private void sendMethodNotAllowed() throws IOException {
        String response = "HTTP/1.1 405 Method Not Allowed\r\n"
                + "Date: " + new Date() + "\r\n"
                + "Server: Custom Proxy Server\r\n"
                + "Content-Length: 0\r\n\r\n";
        ClientStream.writeBytes(response);
        conSock.close();
        appendToLog("Method not allowed");
    }

    private String readHeader(DataInputStream dis) throws IOException {
        ByteArrayOutputStream headerOut = new ByteArrayOutputStream();
        int c;
        while ((c = dis.read()) != -1) {
            headerOut.write(c);
            // Check if the last 4 characters are \r\n\r\n
            if (headerOut.size() >= 4) {
                byte[] lastFour = headerOut.toByteArray();
                int len = lastFour.length;
                if (lastFour[len - 4] == '\r' && lastFour[len - 3] == '\n' &&
                        lastFour[len - 2] == '\r' && lastFour[len - 1] == '\n') {
                    break;
                }
            }
        }
        return headerOut.toString(StandardCharsets.UTF_8);
    }

    private void handlePostRequest(String domain, String path, String restHeader) throws IOException {
        URL url = new URL("http://" + domain + path);
        String contentType = getHeaderValue("Content-Type", restHeader);
        int contentLength = Integer.parseInt(getHeaderValue("Content-Length", restHeader));

        byte[] requestBody = new byte[contentLength];
        ServerStream.readFully(requestBody);  // Read the full POST body

        byte[] response = fetchFromServer(url, "POST", restHeader, requestBody);
        cacheResource(path, response);  // Cache the POST response (if appropriate based on caching rules)

        sendToClient(url, response);
        appendToLog("POST request handled for domain: " + domain);
    }


    private void handleOptionsRequest(String domain, String path, String restHeader) throws IOException {
        URL url = new URL("http://" + domain + path);
        byte[] response = fetchFromServer(url, "OPTIONS", restHeader, new byte[0]);
        sendToClient(url, response);
        appendToLog("OPTIONS request handled for domain: " + domain);
    }

    private String getHeaderValue(String headerName, String headers) {
        // Simple parse to extract a header value from raw headers string
        int index = headers.indexOf(headerName);
        if (index == -1) return null;
        int start = headers.indexOf(": ", index) + 2;
        int end = headers.indexOf("\r\n", start);
        return headers.substring(start, end);
    }

    private void cacheResource(String path, byte[] data) throws IOException {
        File file = new File(CACHE_DIR, path.replace("/", "_"));
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
        }
        cacheMap.put(path, file);
        appendToLog("Resource cached for path: " + path);
    }

    private void handleGetHeadRequest(String method, String domain, String path, String restHeader) throws IOException, ParseException {
        URL url = new URL("http://" + domain + path);
        String urlString = url.toString();

        // Check cache first
        if (cache.containsKey(urlString)) {
            CachedResources cachedResource = cache.get(urlString);
            if (!cachedResource.isExpired()) {
                byte[] data = cachedResource.getData();
                sendToClient(url, data);  // Use the cached data if not expired
                logCachedDataSent(urlString, conSock.getInetAddress().getHostAddress());  // Log the event
                return;  // Return after handling with cached data
            }
        }

        // Fetch new data if not cached or cache is stale
        byte[] data = fetchFromServer(url, method, restHeader, new byte[0]);
        cache.put(urlString, new CachedResources(data, System.currentTimeMillis()));  // Cache the new data
        sendToClient(url, data);  // Send the newly fetched data to the client
        appendToLog("GET/HEAD request handled for domain: " + domain);
    }

    private byte[] fetchFromServer(URL url, String method, String headers, byte[] body) throws IOException {
        try (Socket socket = new Socket(url.getHost(), url.getPort() == -1 ? 80 : url.getPort());
             InputStream serverInputStream = socket.getInputStream();
             OutputStream serverOutputStream = socket.getOutputStream();
             ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
             PrintWriter writer = new PrintWriter(serverOutputStream, true)) {

            // Set up the request headers and body
            Set<String> headerSet = new HashSet<>();
            StringBuilder requestBuilder = new StringBuilder();
            requestBuilder.append(method).append(" ").append(url.getFile()).append(" HTTP/1.1\r\n");

            String[] lines = headers.split("\r\n");
            for (String line : lines) {
                int colonIndex = line.indexOf(':');
                if (colonIndex != -1) {
                    String headerName = line.substring(0, colonIndex).trim();
                    if (!headerSet.contains(headerName)) {
                        requestBuilder.append(line).append("\r\n");
                        headerSet.add(headerName);
                    }
                }
            }

            // Add Host header if missing
            if (!headerSet.contains("Host")) {
                requestBuilder.append("Host: ").append(url.getHost()).append("\r\n");
            }

            requestBuilder.append("Connection: close\r\n\r\n");
            writer.print(requestBuilder.toString());
            writer.flush();

            // Send the body if it exists (important for POST requests)
            if (body.length > 0) {
                serverOutputStream.write(body);
                serverOutputStream.flush();
            }

            // Read the response
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = serverInputStream.read(buffer)) != -1) {
                responseStream.write(buffer, 0, bytesRead);
            }

            return responseStream.toByteArray();
        }
    }

    private String extractPath(String header) {
        int firstSpace = header.indexOf(' ');
        int secondSpace = header.indexOf(' ', firstSpace + 1);
        String path = header.substring(firstSpace + 1, secondSpace);
        if (path.startsWith("http://") || path.startsWith("https://")) {
            try {
                URL url = new URL(path);
                return url.getFile(); // This gets the path part after the domain
            } catch (MalformedURLException e) {
                System.err.println("Malformed URL: " + path);
            }
        }
        return path;
    }


    private void logCachedDataSent(String url, String clientIP) {
        appendToLog("Cached data for URL: " + url + " sent to client IP: " + clientIP + "\n");
    }

    private void appendToLog(String message) {
        Platform.runLater(() -> logTextArea.appendText(message + "\n"));
    }
}