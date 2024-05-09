package com.example.demo;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class ServerHandler extends Thread {
    private final Socket conSock;
    private final FilteredListManager filteredListManager;
    private DataInputStream dIS;
    private DataOutputStream dOS;
    private static final int BUFFER_SIZE = 8192; // Buffer size for reading response


    // Caching mechanism
    private static final String CACHE_DIR = "./cache";
    private Map<String, File> cacheMap = new HashMap<>();

    public ServerHandler(Socket c, FilteredListManager filteredListManager) {
        conSock = c;
        this.filteredListManager = filteredListManager;
        try {
            dIS = new DataInputStream(conSock.getInputStream());
            dOS = new DataOutputStream(conSock.getOutputStream());
            ensureCacheDirExists(); // Ensure the cache directory exists
            System.out.println("ServerHandler initialized for: " + conSock);
        } catch (IOException e) {
            System.err.println("Error initializing ServerHandler: " + e.getMessage());
            try {
                conSock.close();
            } catch (IOException ex) {
                System.err.println("Error closing socket: " + ex.getMessage());
            }
        }
    }

    private void ensureCacheDirExists() {
        File cacheDir = new File(CACHE_DIR);
        if (!cacheDir.exists()) {
            boolean wasCreated = cacheDir.mkdirs();
            if (wasCreated) {
                System.out.println("Cache directory was created successfully.");
            } else {
                System.out.println("Failed to create cache directory.");
            }
        }
    }

    @Override
    public void run() {
        try {
            System.out.println("Handling connection from " + conSock.getInetAddress());
            String header = readHeader(dIS);
            System.out.println("Received header: " + header);

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
                dOS.write(buffer, 0, bytesRead);
            }
            dOS.flush();
        } finally {
            conSock.close();
        }
    }


    private String extractMethod(String header) {
        int firstSpace = header.indexOf(' ');
        return header.substring(0, firstSpace);
    }

    private String extractPath(String header) {
        int firstSpace = header.indexOf(' ');
        int secondSpace = header.indexOf(' ', firstSpace + 1);
        return header.substring(firstSpace + 1, secondSpace);
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
        dOS.writeBytes(response);
        conSock.close();
    }

    private void sendMethodNotAllowed() throws IOException {
        String response = "HTTP/1.1 405 Method Not Allowed\r\n"
                + "Date: " + new Date() + "\r\n"
                + "Server: Custom Proxy Server\r\n"
                + "Content-Length: 0\r\n\r\n";
        dOS.writeBytes(response);
        conSock.close();
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
        // Add logic for handling POST requests
    }

    private void handleOptionsRequest(String domain, String path, String restHeader) throws IOException {
        // Add logic for handling OPTIONS requests
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
    }

    private void sendNotModifiedResponse() throws IOException {
        dOS.writeBytes("HTTP/1.1 304 Not Modified\r\n");
        dOS.writeBytes("\r\n");
        dOS.flush();
    }



    private void handleGetHeadRequest(String method, String domain, String path, String restHeader) throws IOException, ParseException {
        File cachedFile = cacheMap.get(path);
        boolean isCached = cachedFile != null && cachedFile.exists();
        byte[] data = null;

        if (isCached) {
            // Check if the cached version is still fresh
            Date lastModified = new Date(cachedFile.lastModified());
            String ifModifiedSince = getHeaderValue("If-Modified-Since", restHeader);
            if (ifModifiedSince != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
                sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
                Date ifModifiedSinceDate = sdf.parse(ifModifiedSince);
                if (lastModified.compareTo(ifModifiedSinceDate) < 0) {
                    sendNotModifiedResponse();
                    return;
                }
            }
            data = Files.readAllBytes(cachedFile.toPath());
        }

        if (data == null) {  // Fetch new data if not cached or cache is stale
            URL url = new URL("http://" + domain + path);
            data = fetchFromServer(url, method, restHeader);
            cacheResource(path, data);
        }

        sendToClient(new URL("http://" + domain + path), data);
    }



    private byte[] fetchFromServer(URL url, String method, String headers) throws IOException {
        try (Socket socket = new Socket(url.getHost(), url.getPort() == -1 ? 80 : url.getPort());
             InputStream serverInputStream = socket.getInputStream();
             OutputStream serverOutputStream = socket.getOutputStream();
             ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
             PrintWriter writer = new PrintWriter(serverOutputStream, true)) {

            // Manage headers to avoid duplicates
            Set<String> headerSet = new HashSet<>();
            StringBuilder requestBuilder = new StringBuilder();
            requestBuilder.append(method).append(" ").append(url.getFile()).append(" HTTP/1.1\r\n");

            // Split headers and add them if not already added
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

            // Add Host if not already included
            if (!headerSet.contains("Host")) {
                requestBuilder.append("Host: ").append(url.getHost()).append("\r\n");
            }

            requestBuilder.append("Connection: close\r\n\r\n");

            // Log the complete request for debugging
            System.out.println("Complete Request:\n" + requestBuilder.toString());

            // Send the request
            writer.print(requestBuilder.toString());
            writer.flush();

            // Read and print the response for debugging
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = serverInputStream.read(buffer)) != -1) {
                responseStream.write(buffer, 0, bytesRead);
                System.out.write(buffer, 0, bytesRead);  // Print the raw response for debugging
            }

            return responseStream.toByteArray();
        }
    }


}