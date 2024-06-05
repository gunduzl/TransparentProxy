package com.example.demo;

import javafx.application.Platform;
import javafx.scene.control.TextArea;

import java.io.*;
import java.net.*;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ServerHandler extends Thread {
    private final Socket connection; // Client connection socket
    private final FilteredListManager filteredListManager; // Manager for filtered lists
    private BufferedReader clientInput; // Reader for client input stream
    private DataOutputStream clientOutput; // Output stream for client
    private static final int BUFFER_SIZE = 8192; // 8 KB buffer size
    private final TextArea logTextArea; // TextArea for logging
    private final ConcurrentMap<String, CachedResources> cache; // Thread-safe cache implementation
    private final Customer customer; // Customer object
    private final boolean isHttps; // Flag to check if connection is HTTPS
    private static final int MAX_FILE_SIZE = 500 * 1024 * 1024; // 500 MB maximum file size

    private static final String LOGIN_PAGE = "<html><body><h2>Login Page</h2><form method='post'>Token: <input type='text' name='token'><input type='submit' value='Submit'></form></body></html>";
    private static final Map<String, Boolean> clientTokens = new ConcurrentHashMap<>(); // Token map to manage client tokens


    public ServerHandler(Socket connection, FilteredListManager filteredListManager, TextArea logTextArea, ConcurrentMap<String, CachedResources> cache, Customer customer, boolean isHttps) {
        this.connection = connection;
        this.filteredListManager = filteredListManager;
        this.logTextArea = logTextArea;
        this.cache = cache;
        this.customer = customer;
        this.isHttps = isHttps;
        initStreams(); // Initialize input and output streams
    }

    private void initStreams() {
        try {
            clientInput = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            clientOutput = new DataOutputStream(connection.getOutputStream());
            appendToLog("ServerHandler initialized for: " + connection);
        } catch (IOException e) {
            appendToLog("Failed to initialize ServerHandler: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            appendToLog("Handling connection from " + connection.getInetAddress().getHostAddress());
            if (isHttps) {
                handleHttps(); // Handle HTTPS connections
            } else {
                handleClientRequest(); // Handle HTTP requests
            }
        } catch (Exception e) {
            appendToLog("Error processing the request: " + e.getMessage());
        } finally {
            closeResources(); // Close resources after handling request
        }
    }

    private void handleHttps() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            DataOutputStream dataOutputStream = new DataOutputStream(connection.getOutputStream());

            // Read the first line of the request to check if it is a CONNECT request
            String firstLine = reader.readLine();
            if (firstLine != null && firstLine.startsWith("CONNECT")) {
                appendToLog("Received CONNECT request: " + firstLine);

                // Acknowledge the CONNECT request
                dataOutputStream.writeBytes("HTTP/1.1 200 Connection Established\r\n\r\n");
                dataOutputStream.flush();

                // Relay data transparently between client and target server
                Socket targetSocket = null;
                try {
                    // Extract the target hostname and port from the CONNECT request
                    String[] parts = firstLine.split(" ");
                    String[] hostPort = parts[1].split(":");
                    String host = hostPort[0];
                    int port = Integer.parseInt(hostPort[1]);

                    String clientIP = connection.getInetAddress().getHostAddress();
                    if (!clientTokens.containsKey(clientIP)) {
                        handleInitialRequest(); // Handle initial request if client token is not present
                        return;
                    }

                    Boolean isFilteringEnabled = clientTokens.get(clientIP);
                    if (isFilteringEnabled != null && isFilteringEnabled && filteredListManager.isFilteredHost(host)) {
                        sendUnauthorizedResponseMinimal(); // Send unauthorized response if host is filtered
                        return;
                    }

                    // Connect to the target server
                    targetSocket = new Socket(host, port);
                    appendToLog("Connected to target server: " + host + ":" + port);

                    // Relay data between the client and the target server
                    relayData(connection.getInputStream(), targetSocket.getOutputStream(), targetSocket.getInputStream(), connection.getOutputStream());

                } catch (Exception e) {
                    appendToLog("Error handling CONNECT request: " + e.getMessage());
                    if (targetSocket != null && !targetSocket.isClosed()) {
                        targetSocket.close();
                    }
                }
            } else {
                appendToLog("Expected CONNECT request but received: " + firstLine);
                sendErrorResponse(400, "Bad Request");
            }
        } catch (IOException e) {
            appendToLog("Failed to handle HTTPS: " + e.getMessage());
        }
    }

    private void relayData(InputStream clientInputStream, OutputStream serverOutputStream, InputStream serverInputStream, OutputStream clientOutputStream) {
        Thread clientToServer = new Thread(() -> {
            try {
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = clientInputStream.read(buffer)) != -1) { // Read data from client and send to server
                    serverOutputStream.write(buffer, 0, read);
                    serverOutputStream.flush();
                }
            } catch (IOException e) {
                appendToLog("Error relaying data from client to server: " + e.getMessage());
            }
        });

        Thread serverToClient = new Thread(() -> {
            try {
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = serverInputStream.read(buffer)) != -1) { // Read data from server and send to client
                    clientOutputStream.write(buffer, 0, read);
                    clientOutputStream.flush();
                }
            } catch (IOException e) {
                appendToLog("Error relaying data from server to client: " + e.getMessage());
            }
        });

        clientToServer.start();
        serverToClient.start();

        try {
            clientToServer.join();
            serverToClient.join();
        } catch (InterruptedException e) {
            appendToLog("Data relay threads interrupted: " + e.getMessage());
        }
    }

    private void handleClientRequest() throws IOException {
        String clientIP = connection.getInetAddress().getHostAddress();

        if (!clientTokens.containsKey(clientIP)) {
            handleInitialRequest(); // Handle initial request if client token is not present
            return;
        }

        String firstLine = clientInput.readLine();
        if (firstLine == null || firstLine.isEmpty()) {
            appendToLog("Invalid request: empty first line.");
            return;
        }

        String method = extractMethod(firstLine); // Extract HTTP method from request
        if ("CONNECT".equalsIgnoreCase(method)) {
            handleConnectRequest(firstLine); // Handle CONNECT request
        } else {
            handleHttpRequest(firstLine, method); // Handle other HTTP requests
        }
    }

    private void handleInitialRequest() throws IOException {
        String firstLine = clientInput.readLine();
        if (firstLine == null || firstLine.isEmpty()) {
            appendToLog("Invalid request: empty first line.");
            return;
        }

        String method = extractMethod(firstLine); // Extract HTTP method from request
        if ("POST".equalsIgnoreCase(method)) {
            handleTokenSubmission(); // Handle token submission via POST request
        } else {
            serveLoginPage(); // Serve login page for other requests
        }
    }

    private void handleHttpRequest(String firstLine, String method) throws IOException {
        String header = readRestOfHeader(); // Read the rest of the HTTP header
        String path = extractPath(firstLine); // Extract the path from the request line
        String host = extractHost(header); // Extract the host from the header
        if (host == null) {
            appendToLog("Host header is missing.");
            return;
        }

        String fullUrl = constructUrl(host, path); // Construct the full URL from host and path
        if (fullUrl.isEmpty()) {
            appendToLog("Failed to construct URL.");
            return;
        }

        URL url;
        try {
            url = new URL(fullUrl); // Validate the constructed URL
        } catch (MalformedURLException e) {
            appendToLog("Malformed URL: " + fullUrl);
            return;
        }

        String domain = url.getHost(); // Extract domain from URL
        String urlPath = url.getPath(); // Extract path from URL

        Boolean isFilteringEnabled = clientTokens.get(connection.getInetAddress().getHostAddress());
        if (isFilteringEnabled != null && isFilteringEnabled && filteredListManager.isFilteredHost(domain)) {
            sendUnauthorizedResponse(domain); // Send unauthorized response if host is filtered
            return;
        }

        logRequest(domain, urlPath, method, 200); // Log the request

        switch (method.toUpperCase()) {
            case "GET":
            case "HEAD":
                handleGetHeadRequest(method, url, header); // Handle GET and HEAD requests
                break;
            case "POST":
                handlePostRequest(url, header); // Handle POST request
                break;
            case "OPTIONS":
                handleOptionsRequest(domain, urlPath, header); // Handle OPTIONS request
                break;
            default:
                sendMethodNotAllowed(); // Send response for unsupported methods
        }
    }

    private void handleConnectRequest(String firstLine) {
        appendToLog("Handling CONNECT request for: " + firstLine);
        String[] parts = firstLine.split(" ");
        if (parts.length != 3) {
            appendToLog("Invalid CONNECT request line: " + firstLine);
            sendErrorResponse(400, "Bad Request");
            return;
        }
        String domainAndPort = parts[1];
        try {
            String[] hostParts = domainAndPort.split(":");
            if (hostParts.length != 2) {
                sendErrorResponse(400, "Bad Request: Invalid host and port format.");
                return;
            }
            String host = hostParts[0];
            int port = Integer.parseInt(hostParts[1]);

            Boolean isFilteringEnabled = clientTokens.get(connection.getInetAddress().getHostAddress());
            if (isFilteringEnabled != null && isFilteringEnabled && filteredListManager.isFilteredHost(host)) {
                sendUnauthorizedResponseMinimal(); // Send unauthorized response if host is filtered
                return;
            }

            appendToLog("Parsed host: " + host + ", port: " + port);

            try (Socket serverSocket = new Socket(host, port);
                 InputStream clientInput = connection.getInputStream();
                 OutputStream clientOutput = connection.getOutputStream();
                 InputStream serverInput = serverSocket.getInputStream();
                 OutputStream serverOutput = serverSocket.getOutputStream()) {

                clientOutput.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes());
                clientOutput.flush();

                Thread clientToServer = new Thread(() -> relayData(clientInput, serverOutput));
                Thread serverToClient = new Thread(() -> relayData(serverInput, clientOutput));

                clientToServer.start();
                serverToClient.start();

                clientToServer.join();
                serverToClient.join();
            }
        } catch (NumberFormatException e) {
            appendToLog("Number format exception: " + e.getMessage());
            sendErrorResponse(400, "Bad Request: Invalid port number.");
        } catch (Exception e) {
            appendToLog("Failed to handle CONNECT request: " + e.getMessage());
            sendErrorResponse(500, "Internal Server Error");
        }
    }

    private void relayData(InputStream in, OutputStream out) {
        try {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) { // Relay data between streams
                out.write(buffer, 0, read);
                out.flush();
            }
        } catch (IOException e) {
            appendToLog("Error relaying data: " + e.getMessage());
        }
    }

    private void handleGetHeadRequest(String method, URL url, String header) throws IOException {
        String urlString = url.toString();

        CachedResources cachedResource = cache.get(urlString);
        if (cachedResource != null && !cachedResource.isExpired()) {
            appendToLog("Serving cached data for URL: " + urlString);
            clientOutput.write(cachedResource.getData());
            clientOutput.flush();
        } else {
            GET_HEAD_FROM_SERVER(url, method, urlString, header); // Fetch and cache the GET/HEAD request
        }
    }

    private void GET_HEAD_FROM_SERVER(URL url, String method, String urlString, String headers) throws IOException {
        ByteArrayOutputStream bufferStream = new ByteArrayOutputStream();
        try (Socket socket = new Socket(url.getHost(), url.getPort() == -1 ? 80 : url.getPort()); // Connect to the server using the URL host and port number
             InputStream serverInput = socket.getInputStream();
             OutputStream serverOutput = socket.getOutputStream();
             PrintWriter writer = new PrintWriter(serverOutput, true)) {

            // Process and set headers using the utility function
            String processedHeaders = HeaderUtils.processHeaders(headers, url, method);
            // processedHeaders will contain the processed headers for the request = method + path + HTTP/1.1 + other headers
            writer.print(processedHeaders);
            writer.println(); // Ensure an empty line to end the header section
            writer.flush();

            appendToLog("Request sent to " + url.getHost() + ":\n" + processedHeaders);

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = serverInput.read(buffer)) != -1) { // Read response from server
                bufferStream.write(buffer, 0, bytesRead);
            }

            byte[] data = bufferStream.toByteArray();
            clientOutput.write(data);
            clientOutput.flush();

            cache.put(urlString, new CachedResources(url, data, System.currentTimeMillis() + 1000 * 60 * 10)); // 10 minutes expiry
            appendToLog("New data fetched and cached for URL: " + urlString);
        }
    }

    private void handlePostRequest(URL url, String headers) throws IOException {
        final int BUFFER_SIZE = 4096;
        try (Socket socket = new Socket(url.getHost(), url.getPort() == -1 ? 80 : url.getPort());
             InputStream serverInputStream = socket.getInputStream();
             OutputStream serverOutputStream = socket.getOutputStream();
             ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
             PrintWriter writer = new PrintWriter(serverOutputStream, true)) {

            // Process and set headers using the utility function
            String processedHeaders = HeaderUtils.processHeaders(headers, url, "POST");
            writer.print(processedHeaders);
            writer.println(); // Ensure an empty line to end the header section
            writer.flush();

            // Read the body from the client and forward it to the server
            StringBuilder requestBody = new StringBuilder();
            while (clientInput.ready()) { // Read request body from client
                requestBody.append((char) clientInput.read());
            }
            String body = requestBody.toString();
            writer.print(body);
            writer.flush();

            // Read the response from the server and forward it to the client
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = serverInputStream.read(buffer)) != -1) { // Read response from server
                responseStream.write(buffer, 0, bytesRead);
            }
            appendToLog("POST request handled for domain: " + url.getHost());
            clientOutput.write(responseStream.toByteArray());
        }
    }

    private void handleOptionsRequest(String domain, String path, String headers) {
        try {
            URL url = new URL("http://" + domain + path); // Create the URL for the OPTIONS request

            try (Socket socket = new Socket(url.getHost(), url.getPort() == -1 ? 80 : url.getPort());
                 InputStream serverInputStream = socket.getInputStream();
                 OutputStream serverOutputStream = socket.getOutputStream();
                 ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
                 PrintWriter writer = new PrintWriter(serverOutputStream, true)) {

                // Process the headers using the HeaderUtils class
                String processedHeaders = HeaderUtils.processHeaders(headers, url, "OPTIONS");
                writer.print(processedHeaders);
                writer.println(); // Ensure an empty line to end the header section
                writer.flush();

                appendToLog("OPTIONS request sent to " + domain + path + ":\n" + processedHeaders);

                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = serverInputStream.read(buffer)) != -1) { // Read response from server
                    responseStream.write(buffer, 0, bytesRead);
                }

                clientOutput.write(responseStream.toByteArray()); // Send the response back to the client
                clientOutput.flush();

                appendToLog("OPTIONS request handled for domain: " + domain);
            }
        } catch (IOException e) {
            appendToLog("OPTIONS request failed for domain: " + domain + "; Error: " + e.getMessage());
        }
    }

    private void sendMethodNotAllowed() {
        String response = "HTTP/1.1 405 Method Not Allowed\r\n"
                + "Date: " + new Date() + "\r\n"
                + "Server: Custom Proxy Server\r\n"
                + "Content-Length: 0\r\n\r\n";
        try {
            clientOutput.writeBytes(response); // Send method not allowed response
        } catch (IOException e) {
            appendToLog("Failed to send Method Not Allowed response: " + e.getMessage());
        }
        appendToLog("Method not allowed");
    }

    private void serveLoginPage() throws IOException {
        PrintWriter out = new PrintWriter(clientOutput, true);
        out.print("HTTP/1.1 200 OK\r\n");
        out.print("Content-Type: text/html\r\n");
        out.print("Content-Length: " + LOGIN_PAGE.length() + "\r\n");
        out.print("\r\n");
        out.print(LOGIN_PAGE); // Send login page HTML
        out.flush();
    }

    private void handleTokenSubmission() throws IOException {
        String clientIP = connection.getInetAddress().getHostAddress();
        StringBuilder requestBody = new StringBuilder();

        while (!clientInput.readLine().isEmpty()) { // Read headers until empty line is found
            // Read headers
        }
        while (clientInput.ready()) { // Read request body from client input stream until no more data
            requestBody.append((char) clientInput.read());
        }

        String token = extractTokenFromRequestBody(requestBody.toString());
        if (validateToken(token)) {
            boolean isFilteringEnabled = "51e2cba401".equals(token);
            clientTokens.put(clientIP, isFilteringEnabled);
            appendToLog("Token validated for IP: " + clientIP);
            serveSuccessPage(); // Serve success page if token is valid
        } else {
            appendToLog("Invalid token for IP: " + clientIP);
            serveLoginPage(); // Serve login page if token is invalid
        }
    }

    private String extractTokenFromRequestBody(String requestBody) {
        for (String param : requestBody.split("&")) { // Split request body by "&" to extract parameters and values separately
            String[] pair = param.split("=");
            if (pair.length == 2 && "token".equals(pair[0])) { // Check if parameter is "token" and extract value
                return pair[1]; // Extract token from request body
            }
        }
        return null;
    }

    private boolean validateToken(String token) {
        return "8a21bce200".equals(token) || "51e2cba401".equals(token); // Validate token
    }

    private void serveSuccessPage() throws IOException {
        String successPage = "<html><body><h2>Token accepted. You can now access the internet.</h2></body></html>";
        PrintWriter out = new PrintWriter(clientOutput, true);
        out.print("HTTP/1.1 200 OK\r\n");
        out.print("Content-Type: text/html\r\n");
        out.print("Content-Length: " + successPage.length() + "\r\n");
        out.print("\r\n");
        out.print(successPage); // Send success page HTML
        out.flush();
    }

    private String extractMethod(String firstLine) {
        int firstSpace = firstLine.indexOf(' ');
        if (firstSpace == -1) {
            appendToLog("Invalid request header, no method found: " + firstLine);
            return "INVALID";
        }
        return firstLine.substring(0, firstSpace); // Extract method from request line
    }

    private void sendUnauthorizedResponse(String domain) {
        String html = "<html><body><h1>Access to " + domain + " is not allowed!</h1></body></html>";
        String response = "HTTP/1.1 401 Not Authorized\r\n"
                + "Date: " + new Date() + "\r\n"
                + "Server: Custom Proxy Server\r\n"
                + "Content-Length: " + html.length() + "\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n\r\n" + html;
        try {
            clientOutput.writeBytes(response); // Send unauthorized response with HTML
        } catch (IOException e) {
            appendToLog("Failed to send unauthorized response: " + e.getMessage());
        }
        appendToLog("Unauthorized access attempt to domain: " + domain);
    }

    private void sendUnauthorizedResponseMinimal() {
        String response = "HTTP/1.1 401 Not Authorized\r\n"
                + "Date: " + new Date() + "\r\n"
                + "Server: Custom Proxy Server\r\n"
                + "Content-Length: 0\r\n\r\n";
        try {
            clientOutput.writeBytes(response); // Send minimal unauthorized response
            clientOutput.flush();
        } catch (IOException e) {
            appendToLog("Failed to send unauthorized response: " + e.getMessage());
        }
    }

    private void sendErrorResponse(int statusCode, String message) {
        String response = "HTTP/1.1 " + statusCode + " " + message + "\r\n"
                + "Date: " + new Date() + "\r\n"
                + "Server: Custom Proxy Server\r\n"
                + "Content-Length: 0\r\n\r\n";
        try {
            clientOutput.writeBytes(response); // Send error response
        } catch (IOException e) {
            appendToLog("Failed to send error response: " + e.getMessage());
        }
    }

    private String extractPath(String firstLine) {
        int firstSpace = firstLine.indexOf(' ');
        int secondSpace = firstLine.indexOf(' ', firstSpace + 1);
        if (firstSpace == -1 || secondSpace == -1) {
            appendToLog("Invalid request header, cannot extract path: " + firstLine);
            return "";
        }
        String url = firstLine.substring(firstSpace + 1, secondSpace);
        try {
            URL parsedUrl = new URL(url);
            return parsedUrl.getFile(); // Return the path part of the URL
        } catch (MalformedURLException e) {
            appendToLog("Malformed URL in request line: " + url);
            return "";
        }
    }

    private String constructUrl(String host, String path) {
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return "http://" + host + path; // Construct the URL from host and path
    }

    private String readRestOfHeader() throws IOException {
        StringBuilder header = new StringBuilder();
        String line;
        while ((line = clientInput.readLine()) != null && !line.isEmpty()) { // Read the rest of the HTTP header
            header.append(line).append("\r\n");
        }
        return header.toString();
    }

    private String extractHost(String headers) {
        String[] lines = headers.split("\r\n");
        for (String line : lines) {
            if (line.startsWith("Host:")) {
                return line.substring(5).trim(); // Extract host from headers
            }
        }
        return null;
    }

    private void appendToLog(String message) {
        Platform.runLater(() -> logTextArea.appendText(message + "\n")); // Append messages to log
    }

    private void logRequest(String domain, String resourcePath, String method, int statusCode) {
        RequestLogEntry logEntry = new RequestLogEntry(new Date(), connection.getInetAddress().getHostAddress(), domain, resourcePath, method, statusCode, customer);
        try {
            logEntry.saveToDatabase(); // Save log entry to database
        } catch (SQLException e) {
            System.out.println("Error saving log entry: " + e.getMessage());
        }
    }

    private void closeResources() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close(); // Close the client connection
            }
        } catch (IOException e) {
            appendToLog("Failed to close connection: " + e.getMessage());
        }
    }
}
