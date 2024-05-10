package com.example.demo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;

public class RequestLogEntry {
    private Date date; // java.util.Date
    private String clientIP;
    private String domain;
    private String resourcePath;
    private String method;
    private int statusCode;
    private Customer customer; // Assuming User is the class/entity representing users

    public RequestLogEntry(Date date, String clientIP, String domain, String resourcePath, String method, int statusCode, Customer customer) {
        this.date = date;
        this.clientIP = clientIP;
        this.domain = domain;
        this.resourcePath = resourcePath;
        this.method = method;
        this.statusCode = statusCode;
        this.customer = customer;
    }

    public void saveToDatabase() throws SQLException {
        String sql = "INSERT INTO request_logs (date, client_ip, domain, resource_path, method, status_code, customer_id) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setTimestamp(1, new java.sql.Timestamp(date.getTime()));
            pstmt.setString(2, clientIP);
            pstmt.setString(3, domain);
            pstmt.setString(4, resourcePath);
            pstmt.setString(5, method);
            pstmt.setInt(6, statusCode);
            pstmt.setInt(7, customer.getId()); // Assuming Customer class has getId() method

            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error saving request log entry: " + e.getMessage(), e);
        }
    }

    public Date getDate() {
        return date;
    }

    public String getClientIP() {
        return clientIP;
    }

    public String getDomain() {
        return domain;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public String getMethod() {
        return method;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Customer getUser() {
        return customer;
    }

    public void setUser(Customer customer) {
        this.customer = customer;
    }
}
