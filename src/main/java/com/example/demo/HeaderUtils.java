package com.example.demo;

import java.net.URL;
import java.util.*;

public class HeaderUtils {
    /**
     * Processes and returns a set of HTTP headers ensuring no duplicates and handling special cases.
     *
     * @param headers Raw headers string from the client request.
     * @param url The URL for which the request is being made.
     * @param method HTTP method (GET, POST, etc.)
     * @return A string with the processed headers ready to be sent to the destination server.
     */
    public static String processHeaders(String headers, URL url, String method) {
        StringBuilder processedHeaders = new StringBuilder();
        Set<String> sentHeaders = new HashSet<>();

        // Common headers processing
        processedHeaders.append(method).append(" ").append(url.getPath()).append(" HTTP/1.1\r\n");

        String[] lines = headers.split("\r\n");
        for (String line : lines) {
            int colonPos = line.indexOf(':');
            if (colonPos != -1) {
                String headerName = line.substring(0, colonPos).trim();
                if (!sentHeaders.contains(headerName)) {
                    processedHeaders.append(line).append("\r\n");
                    sentHeaders.add(headerName);
                }
            }
        }

        // Ensure the Host header is included
        if (!sentHeaders.contains("Host")) {
            processedHeaders.append("Host: ").append(url.getHost()).append("\r\n");
        }

        // Always close the connection after the request is completed
        processedHeaders.append("Connection: close\r\n");

        return processedHeaders.toString();
    }
}
