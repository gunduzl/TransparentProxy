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
                        handleGetHeadRequest(ClientStream,method, domain, urlPath);
                        break;
                    case "POST":
                        BufferedReader ClientBuffer =new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        handlePostRequest(ClientBuffer, ClientStream, url);
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


    private void handlePostRequest(BufferedReader clientInput, OutputStream clientOutput, URL url) throws IOException {
        List<String> headers = new ArrayList<>();
        StringBuilder bodyBuilder = new StringBuilder();
        String contentLength = "0";
        String line;
        while (!(line = clientInput.readLine()).isEmpty()) {
            headers.add(line);
            if (line.startsWith("Content-Length:")) {
                contentLength = line.split(":")[1].trim();
            }
        }

        int length = Integer.parseInt(contentLength);
        char[] body = new char[length];
        clientInput.read(body, 0, length);
        bodyBuilder.append(body);

        String urlString = url.toString();

        fetchAndCachePOST(clientOutput, url, "POST", urlString, headers, bodyBuilder.toString());
    }


    private void fetchAndCachePOST(OutputStream clientOutput,URL url, String method,String urlString, List<String> headers, String requestBody) throws IOException {
        if (cache.containsKey(urlString)) {
            CachedResources resource = cache.get(urlString);
            if (!resource.isExpired()) {
                byte[] data = resource.getData();
                clientOutput.write(data);
                clientOutput.flush();
                appendToLog("Cached data sent for URL: " + urlString);
                return;
            } else {
                appendToLog("Cached data expired for URL: " + urlString + ". Fetching from server...");
            }
        } else {
            appendToLog("No cached data found for URL: " + urlString + ". Fetching from server...");
        }

        ByteArrayOutputStream bufferStream = new ByteArrayOutputStream();
        try (Socket socket = new Socket(url.getHost(), url.getDefaultPort());
             InputStream serverInput = socket.getInputStream();
             OutputStream serverOutput = socket.getOutputStream();
             PrintWriter writer = new PrintWriter(serverOutput, true)) {

            writer.println(method + " " + url.getFile() + " HTTP/1.1");
            writer.println("Host: " + url.getHost());
            writer.println("Connection: close");
            headers.forEach(header -> writer.println(header));
            writer.println(); // End of headers
            writer.print(requestBody);
            writer.flush();

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = serverInput.read(buffer)) != -1) {
                bufferStream.write(buffer, 0, bytesRead);
            }
            byte[] data = bufferStream.toByteArray();
            clientOutput.write(data);
            clientOutput.flush();

            // Cache the fresh response
            cache.put(urlString, new CachedResources(url, data, System.currentTimeMillis()));
            appendToLog("FOR THE POST METHOD: New data fetched and cached for URL: " + urlString);
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


    private void handleGetHeadRequest(DataOutputStream clientStream, String method, String domain, String path) throws IOException {
        URL url = new URL("http://" + domain + path);
        String urlString = url.toString();

        appendToLog("Checking cache for URL: " + urlString);
        CachedResources cachedResource = cache.get(urlString);
        if (cachedResource != null && !cachedResource.isExpired()) {
            appendToLog("--------------CACHED DATA IS SEND TO CLIENT------------");
            appendToLog("Serving cached data for URL: " + urlString);
            appendToLog("-------------------------------------------------------");
            clientStream.write(cachedResource.getData());
            clientStream.flush();
        } else {
            if (cachedResource != null) {
                appendToLog("Cached data is expired for URL: " + urlString);
            } else {
                appendToLog("No cached data found for URL: " + urlString);
            }
            fetchAndCacheGET_HEAD(url, method, clientStream, urlString);
        }
    }

    private void fetchAndCacheGET_HEAD(URL url, String method, OutputStream clientOutput, String urlString) throws IOException {
        ByteArrayOutputStream bufferStream = new ByteArrayOutputStream();
        try (Socket socket = new Socket(url.getHost(), url.getPort() == -1 ? 80 : url.getPort());
             InputStream serverInput = socket.getInputStream();
             OutputStream serverOutput = socket.getOutputStream();
             PrintWriter writer = new PrintWriter(serverOutput, true)) {

            // Request the resource
            writer.println(method + " " + url.getFile() + " HTTP/1.1");
            writer.println("Host: " + url.getHost());
            writer.println("Connection: close");
            writer.println();
            writer.flush();

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = serverInput.read(buffer)) != -1) {
                bufferStream.write(buffer, 0, bytesRead);
            }

            byte[] data = bufferStream.toByteArray();
            clientOutput.write(data);
            clientOutput.flush();

            // Cache the fetched data with a new expiry time
            long expiryTime = System.currentTimeMillis() + 1000 * 60 * 10; // 10 minutes from now
            CachedResources newCachedResource = new CachedResources(url,data, expiryTime);
            cache.put(urlString, newCachedResource);
            appendToLog("New data fetched and cached with expiry for URL: " + urlString);
        }
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