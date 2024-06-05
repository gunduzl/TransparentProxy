package com.example.demo;

import java.net.URL;
import java.util.*;

public class HeaderUtils {

    public static String processHeaders(String headers, URL url, String method) {
        StringBuilder processedHeaders = new StringBuilder();
        Set<String> sentHeaders = new HashSet<>();

        // Common headers processing
        processedHeaders.append(method).append(" ").append(url.getPath()).append(" HTTP/1.1\r\n");
        // it produces a string like "GET /path HTTP/1.1\r\n"

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
