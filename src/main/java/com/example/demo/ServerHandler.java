package com.example.demo;

import javafx.application.Platform;
import javafx.scene.control.TextArea;

import java.io.*;
import java.lang.reflect.Field;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import javax.net.ssl.*;

public class ServerHandler extends Thread {
    private final Socket connection;
    private final FilteredListManager filteredListManager;
    private DataInputStream ServerStream;
    private DataOutputStream ClientStream;
    private static final int BUFFER_SIZE = 8192; // 8 KB
    private static final int MAX_FILE_SIZE = 500 * 1024 * 1024; //// 500 MB
    private static final int MAX_POST_SIZE = 500 * 1024 * 1024; // 500 MB
    private final TextArea logTextArea;
    private ConcurrentMap<String, CachedResources> cache; // Thread-safe cache implementation
    private final Customer customer;


    public ServerHandler(Socket c, FilteredListManager filteredListManager, TextArea logTextArea,ConcurrentMap<String, CachedResources> cache, Customer customer) {
        connection = c;
        this.filteredListManager = filteredListManager;
        this.logTextArea = logTextArea;
        this.cache = cache;
        this.customer = customer;
        System.setProperty("java.net.preferIPv4Stack", "true");
        try {
            ServerStream = new DataInputStream(connection.getInputStream());
            ClientStream = new DataOutputStream(connection.getOutputStream());
            appendToLog("ServerHandler initialized for: " + connection);
        } catch (IOException e) {
            appendToLog("Failed to initialize ServerHandler: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            if (isHttpsConnection(connection)) {
                handleHttpsConnection(connection);
            } else {
                appendToLog("Handling connection from " + connection.getInetAddress().getHostAddress());

                String header = readHeader(ServerStream);
                if (header.isEmpty()) {
                    appendToLog("Received empty header, ignoring the request.");
                    return; // Ignore this request or handle it as per your requirements
                }

                appendToLog("Received header: " + header);

                String method = extractMethod(header);
                if ("INVALID".equals(method)) {
                    appendToLog("Invalid request method, ignoring the request.");
                    return; // Ignore this request or handle it as per your requirements
                }

                String path = extractPath(header, method);
                if (path.isEmpty()) {
                    appendToLog("Invalid path, ignoring the request.");
                    return; // Ignore this request or handle it as per your requirements
                }

                String restHeader = extractRestHeader(header);
                String host = extractHost(restHeader);
                if (host == null) {
                    host = "example.com";
                }

                String fullUrl;
                if (method.equals("CONNECT")) {
                    fullUrl = "https://" + path; // For CONNECT, path includes host:port
                } else {
                    fullUrl = "http://" + host + path; // Standard HTTP requests
                }

                URL url = new URL(fullUrl);
                String domain = url.getHost();
                String urlPath = url.getPath();

                if (filteredListManager.isFilteredHost(domain)) {
                    sendUnauthorizedResponse(domain);
                    return;
                }

                appendToLog("Request method: " + method + ", Domain: " + domain + ", Path: " + urlPath);
                logRequest(domain, urlPath, method, 200);
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
                    case "CONNECT":
                        handleConnectRequest(domain, path);
                        break;
                    default:
                        sendMethodNotAllowed();
                }
            }
        } catch (Exception e) {
            appendToLog("Error processing the request: " + e.getMessage());
        }
    }


    private boolean isHttpsConnection(Socket socket) {
        // Simple check: often HTTPS connections use port 443
        return socket.getPort() == 443;
    }


    private void sendToClient(URL url, byte[] cachedData) throws IOException {
        Socket socket = null;
        try {
            socket = new Socket(url.getHost(), url.getPort() == -1 ? 80 : url.getPort());
            InputStream serverInput = cachedData == null ? socket.getInputStream() : new ByteArrayInputStream(cachedData);
            OutputStream serverOutput = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(serverOutput, true);

            if (cachedData == null) {
                writer.println("POST " + url.getFile() + " HTTP/1.1");
                writer.println("Host: " + url.getHost());
                writer.println("Content-Type: application/json"); // Ensure headers are correct
                writer.println();
                writer.flush();
            }

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = serverInput.read(buffer)) != -1) {
                ClientStream.write(buffer, 0, bytesRead);
                appendToLog("Data chunk written to client");
            }
            ClientStream.flush();
            appendToLog("Response fully sent to client");
        } catch (IOException e) {
            appendToLog("Failed to send data to client: " + e.getMessage());
        }
    }



    private String extractMethod(String header) {
        int firstSpace = header.indexOf(' ');
        if (firstSpace == -1) {
            // Log an error or handle it accordingly
            appendToLog("Invalid request header, no method found: " + header);
            return "INVALID"; // Return a string indicating the method could not be determined
        }
        return header.substring(0, firstSpace);
    }


    private String extractRestHeader(String header) {
        int secondLine = header.indexOf("\r\n") + 2;
        if (secondLine < 2 || secondLine > header.length()) {
            appendToLog("No additional headers found or malformed headers.");
            return ""; // Return empty if no additional headers are present
        }
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


    private void sendUnauthorizedResponse(String domain){
        String html = "<html><body><h1>Access to " + domain + " is not allowed!</h1></body></html>";
        String response = "HTTP/1.1 401 Not Authorized\r\n"
                + "Date: " + new Date() + "\r\n"
                + "Server: Custom Proxy Server\r\n"
                + "Content-Length: " + html.length() + "\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n\r\n" + html;
        try {
            ClientStream.writeBytes(response);
        } catch (IOException e) {
            appendToLog("Failed to send unauthorized response: " + e.getMessage());
        }
        appendToLog("Unauthorized access attempt to domain: " + domain);
    }


    private void sendMethodNotAllowed()  {
        String response = "HTTP/1.1 405 Method Not Allowed\r\n"
                + "Date: " + new Date() + "\r\n"
                + "Server: Custom Proxy Server\r\n"
                + "Content-Length: 0\r\n\r\n";
        try {
            ClientStream.writeBytes(response);
        } catch (IOException e) {
            appendToLog("Failed to send Method Not Allowed response: " + e.getMessage());
        }
        appendToLog("Method not allowed");
    }


    private String readHeader(DataInputStream dis) throws IOException {
        ByteArrayOutputStream headerOut = new ByteArrayOutputStream();
        int lastByte = -1;
        int currentByte;
        int lineEndCount = 0;

        while ((currentByte = dis.read()) != -1) {
            if (lastByte == '\r' && currentByte == '\n') {
                lineEndCount++;
                if (lineEndCount == 2) { // Two consecutive \r\n means end of headers
                    break;
                }
            } else if (!(lastByte == '\n' && currentByte == '\r')) {
                lineEndCount = 0;
            }
            headerOut.write(currentByte);
            lastByte = currentByte;
        }
        return headerOut.toString(StandardCharsets.UTF_8);
    }


    private void handlePostRequest(String domain, String path, String restHeader) {
        try {
            URL url = new URL("http://" + domain + path);
            String contentType = getHeaderValue("Content-Type", restHeader);
            String contentLengthValue = getHeaderValue("Content-Length", restHeader);

            if (contentLengthValue == null) {
                appendToLog("POST request failed for domain: " + domain + "; Content-Length header is missing.");
                return; // Properly handle this case, potentially sending a 411 Length Required response
            }

            int contentLength = Integer.parseInt(contentLengthValue);
            if (contentLength > MAX_POST_SIZE) {
                appendToLog("POST request failed for domain: " + domain + "; Content-Length exceeds limit.");
                sendErrorResponse(ClientStream, 413, "Payload Too Large");
                return;
            }

            byte[] requestBody = new byte[contentLength];
            ServerStream.readFully(requestBody);  // Read the full POST body

            // Optionally, modify the requestBody here if needed

            byte[] response = fetchFromServer(url, "POST", contentType, requestBody);
            if (response == null) {
                sendErrorResponse(ClientStream, 502, "Bad Gateway");
                return;
            }

            sendToClient(url, response);
            appendToLog("POST request handled for domain: " + domain);
        } catch (NumberFormatException e) {
            appendToLog("POST request failed for domain: " + domain + " due to invalid Content-Length value.");
        } catch (IOException e) {
            appendToLog("POST request failed for domain: " + domain + " with error: " + e.getMessage());
            sendErrorResponse(ClientStream, 500, "Internal Server Error");
        }
    }


    private void handleOptionsRequest(String domain, String path, String restHeader)  {
        URL url = null;
        try {
            url = new URL("http://" + domain + path);
        } catch (MalformedURLException e) {
            appendToLog("OPTIONS request failed for domain: " + domain + "; Invalid URL: " + e.getMessage());
        }
        byte[] response = null;
        try {
            response = fetchFromServer(url, "OPTIONS", restHeader, new byte[0]);
        } catch (IOException e) {
            appendToLog("OPTIONS request failed for domain: " + domain + "; Error: " + e.getMessage());
        }
        try {
            sendToClient(url, response);
        } catch (IOException e) {
            appendToLog("Failed to send OPTIONS response to client: " + e.getMessage());
        }
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


    private void handleGetHeadRequest(String method, String domain, String path, String restHeader)  {
        URL url = null;
        try {
            url = new URL("http://" + domain + path);
        } catch (MalformedURLException e) {
            appendToLog("GET/HEAD request failed for domain: " + domain + "; Invalid URL: " + e.getMessage());
        }
        String urlString = url.toString();
        String ifModifiedSince = null;

        // Check cache first
        appendToLog("************************************");
        appendToLog("\nChecking cache for URL: " + urlString);
        if (cache.containsKey(urlString)) {
            CachedResources cachedResource = cache.get(urlString);
            if (!cachedResource.isExpired()) {
                SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
                ifModifiedSince = formatter.format(new Date(cachedResource.getLastModifiedTimestamp()));
            }
        }

        byte[] data = null;
        try {
            data = fetchFromServerWithConditionalGet(url, method, restHeader, ifModifiedSince);
        } catch (IOException e) {
            appendToLog("GET/HEAD request failed for domain: " + domain + "; Error: " + e.getMessage());
        }

        // Handle non-modified status (304 Not Modified)
        if (data == null && ifModifiedSince != null) {
            data = cache.get(urlString).getData(); // Use cached data
            logCachedDataSent(urlString, connection.getInetAddress().getHostAddress());
            appendToLog("------------------------------------");
            appendToLog("\nQuery was made to the server and server responded with 304 Not Modified. Cached data sent to client");
            appendToLog("Cached data sent for URL: " + urlString + " to client IP: " + connection.getInetAddress().getHostAddress());

        } else if (data != null) {
            long lastModified = parseLastModified(restHeader); // Extract last-modified date from response headers
            cache.put(urlString, new CachedResources(data, lastModified));  // Update the cache
        }

        try {
            sendToClient(url, data);
        } catch (IOException e) {
            appendToLog("Failed to send GET/HEAD response to client: " + e.getMessage());
        }
        appendToLog("GET/HEAD request handled for domain: " + domain);
    }


    private byte[] fetchFromServerWithConditionalGet(URL url, String method, String headers, String ifModifiedSince) throws IOException {
        try (Socket socket = new Socket(url.getHost(), url.getPort() == -1 ? 80 : url.getPort());
             InputStream serverInputStream = socket.getInputStream();
             OutputStream serverOutputStream = socket.getOutputStream();
             ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
             PrintWriter writer = new PrintWriter(serverOutputStream, true)) {

            StringBuilder requestBuilder = new StringBuilder();
            requestBuilder.append(method).append(" ").append(url.getFile()).append(" HTTP/1.1\r\n")
                    .append("Host: ").append(url.getHost()).append("\r\n");
            if (ifModifiedSince != null) {
                requestBuilder.append("If-Modified-Since: ").append(ifModifiedSince).append("\r\n");
            }
            requestBuilder.append("Connection: close\r\n\r\n");

            writer.print(requestBuilder.toString());
            writer.flush();

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = serverInputStream.read(buffer)) != -1) {
                responseStream.write(buffer, 0, bytesRead);
            }

            // Check if the server responded with "304 Not Modified"
            String responseHeader = responseStream.toString(StandardCharsets.UTF_8);
            if (responseHeader.contains("304 Not Modified")) {
                appendToLog("Server responded with 304 Not Modified for URL: " + url);
                return null;  // No need to update the cache
            }

            return responseStream.toByteArray();
        }
    }


    private long parseLastModified(String headers) {
        String[] lines = headers.split("\r\n");
        SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
        for (String line : lines) {
            if (line.startsWith("Last-Modified:")) {
                String dateString = line.substring(15).trim();
                try {
                    return formatter.parse(dateString).getTime();
                } catch (ParseException e) {
                    appendToLog("Failed to parse Last-Modified header: " + e.getMessage());
                }
            }
        }
        return System.currentTimeMillis();  // Default to current time if header not found or parsing fails
    }


    private byte[] fetchFromServer(URL url, String method, String headers, byte[] body) throws IOException {
        Socket socket = new Socket(url.getHost(), url.getPort() == -1 ? 80 : url.getPort());
        try (InputStream serverInputStream = socket.getInputStream();
             OutputStream serverOutputStream = socket.getOutputStream();
             ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
             PrintWriter writer = new PrintWriter(serverOutputStream, true)) {

            socket.setSoTimeout(300000); // 5 minutes timeout

            // Send request
            StringBuilder requestBuilder = setupRequestHeaders(method, url, headers);
            writer.print(requestBuilder.toString());
            writer.flush();

            // Send body if present (POST request)
            if (body.length > 0) {
                serverOutputStream.write(body);
                serverOutputStream.flush();
            }

            // Read response headers to get Content-Length
            long contentLength = getContentLength(serverInputStream);
            if (contentLength > MAX_FILE_SIZE) {
                throw new IOException("File size exceeds maximum limit: " + contentLength + " bytes");
            }

            // Read the response body
            byte[] buffer = new byte[65536]; // 64 KB
            int bytesRead;
            long totalRead = 0;
            while ((bytesRead = serverInputStream.read(buffer)) != -1) {
                totalRead += bytesRead;
                if (totalRead > MAX_FILE_SIZE) {
                    throw new IOException("File size exceeded during download: " + totalRead + " bytes");
                }
                responseStream.write(buffer, 0, bytesRead);
            }

            return responseStream.toByteArray();
        } finally {
            socket.close();
        }
    }


    private long getContentLength(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        long contentLength = -1;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            if (line.toLowerCase().startsWith("content-length:")) {
                contentLength = Long.parseLong(line.substring(line.indexOf(':') + 1).trim());
                break;
            }
        }
        return contentLength;
    }


    private StringBuilder setupRequestHeaders(String method, URL url, String headers) {
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
        if (!headerSet.contains("Host")) {
            requestBuilder.append("Host: ").append(url.getHost()).append("\r\n");
        }
        requestBuilder.append("Connection: close\r\n\r\n");
        return requestBuilder;
    }


    private String extractPath(String header, String method) {
        int firstSpace = header.indexOf(' ');
        int secondSpace = header.indexOf(' ', firstSpace + 1);
        if (firstSpace == -1 || secondSpace == -1) {
            appendToLog("Invalid request header, cannot extract path: " + header);
            return ""; // Return an empty string or handle the error as appropriate
        }
        if (method.equals("CONNECT")) {
            return header.substring(firstSpace + 1, secondSpace);
        } else {
            String path = header.substring(firstSpace + 1, secondSpace);
            if (path.startsWith("http://") || path.startsWith("https://")) {
                try {
                    URL url = new URL(path);
                    return url.getFile(); // This gets the path part after the domain
                } catch (MalformedURLException e) {
                    appendToLog("Invalid URL in request header: " + path);
                }
            }
            return path;
        }
    }


    private void handleHttpsConnection(Socket clientSock) throws IOException {
        // Use the original clientSock and wrap it with the custom SSLSocketFactory
        SSLSocketFactory defaultFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        CustomSSLSocketFactory customFactory = new CustomSSLSocketFactory(defaultFactory);
        InetAddress clientAddress = clientSock.getInetAddress();
        int clientPort = clientSock.getPort();

        try (SSLSocket sslSocket = (SSLSocket) customFactory.createSocket(clientSock, clientAddress.getHostAddress(), clientPort, true)) {
            sslSocket.startHandshake();

            String sessionHost = sslSocket.getSession().getPeerHost();
            System.out.println("Session host: " + sessionHost);

            // Extract SNI
            String hostname = extractSni(sslSocket);

            if (hostname != null && filteredListManager.isFilteredHost(hostname)) {
                appendToLog("Blocked HTTPS access to filtered domain: " + hostname);
                sslSocket.close();
                return;
            }

            // Assuming there's logic to determine the real server's port it defaults to the same as the client's port
            try (Socket serverSocket = new Socket(hostname, sslSocket.getPort())) {
                relayTraffic(sslSocket, serverSocket);
            }
        } catch (Exception e) {
            appendToLog("Failed to handle HTTPS connection: " + e.getMessage());
        }
    }


    private String extractSni(SSLSocket sslSocket) {
        SSLSession session = sslSocket.getSession();
        String hostname = null;

        // Reflection is not recommended; this is conceptual
        try {
            Field sessionImplField = session.getClass().getDeclaredField("session");
            sessionImplField.setAccessible(true);
            Object sessionImpl = sessionImplField.get(session);

            Field sniHostnamesField = sessionImpl.getClass().getDeclaredField("sniHostnames");
            sniHostnamesField.setAccessible(true);
            List<SNIServerName> sniServerNames = (List<SNIServerName>) sniHostnamesField.get(sessionImpl);

            if (!sniServerNames.isEmpty() && sniServerNames.get(0) instanceof SNIHostName) {
                hostname = ((SNIHostName) sniServerNames.get(0)).getAsciiName();
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            appendToLog("Failed to extract SNI: " + e.getMessage());
        }

        return hostname;
    }


    private void handleConnectRequest(String domain, String domainAndPort) {
        String[] parts = domainAndPort.split(":");
        if (parts.length != 2) {
            sendErrorResponse(ClientStream, 400, "Bad Request: Invalid host and port format.");
            return;
        }
        String host = parts[0];
        int port;
        try {
            port = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            sendErrorResponse(ClientStream, 400, "Bad Request: Port must be a number.");
            return;
        }

        try (Socket serverSocket = new Socket(host, port);
             InputStream clientInput = connection.getInputStream();
             OutputStream clientOutput = connection.getOutputStream();
             InputStream serverInput = serverSocket.getInputStream();
             OutputStream serverOutput = serverSocket.getOutputStream()) {

            // Notify the client that the tunnel has been established
            ClientStream.writeBytes("HTTP/1.1 200 Connection Established\r\n\r\n");

            // Start relay between client and server
            Thread clientToServer = new Thread(() -> {
                try {
                    relayData(clientInput, serverOutput);
                } catch (IOException e) {
                    appendToLog("Error relaying data from client to server: " + e.getMessage());
                }
            });
            Thread serverToClient = new Thread(() -> {
                try {
                    relayData(serverInput, clientOutput);
                } catch (IOException e) {
                    appendToLog("Error relaying data from server to client: " + e.getMessage());
                }
            });
            clientToServer.start();
            serverToClient.start();

            clientToServer.join(); // Wait for threads to finish
            serverToClient.join();
        } catch (Exception e) {
            sendErrorResponse(ClientStream, 500, "Internal Server Error");
            appendToLog("Failed to handle CONNECT request: " + e.getMessage());
        }
    }


    private void relayTraffic(SSLSocket clientSocketInputStream, Socket serverSocketOutputStream) throws IOException {
        // Relay traffic between client and server without modifying it
        Thread clientToServer = new Thread(() -> {
            try {
                InputStream clientInput = clientSocketInputStream.getInputStream();
                OutputStream serverOutput = serverSocketOutputStream.getOutputStream();
                relayData(clientInput, serverOutput);
            } catch (IOException e) {
                appendToLog("Error relaying data from client to server: " + e.getMessage());
            }
        });

        Thread serverToClient = new Thread(() -> {
            try {
                InputStream serverInput = serverSocketOutputStream.getInputStream();
                OutputStream clientOutput = clientSocketInputStream.getOutputStream();
                relayData(serverInput, clientOutput);
            } catch (IOException e) {
                appendToLog("Error relaying data from server to client: " + e.getMessage());
            }
        });

        clientToServer.start();
        serverToClient.start();
    }


    private void relayData(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
            out.flush();
        }
    }


    private void sendErrorResponse(OutputStream out, int statusCode, String message) {
        String response = "HTTP/1.1 " + statusCode + " " + message + "\r\n"
                + "Date: " + new Date() + "\r\n"
                + "Server: Custom Proxy Server\r\n"
                + "Content-Length: 0\r\n\r\n";
        try {
            out.write(response.getBytes());
        } catch (IOException e) {
            appendToLog("Failed to send error response: " + e.getMessage());
        }
    }


    private void logCachedDataSent(String url, String clientIP) {
        appendToLog("Cached data for URL: " + url + " sent to client IP: " + clientIP + "\n");
    }


    private void appendToLog(String message) {
        Platform.runLater(() -> logTextArea.appendText(message + "\n"));
    }


    private void logRequest(String domain, String resourcePath, String method, int statusCode) {
        RequestLogEntry logEntry = new RequestLogEntry(new Date(), connection.getInetAddress().getHostAddress(), domain, resourcePath, method, statusCode, customer);
        try {
            logEntry.saveToDatabase();
        } catch (SQLException e) {
            System.out.println("Error saving log entry: " + e.getMessage());
        }
    }
}