package com.example.demo;

import java.sql.*;
import java.util.Date;

public class DatabaseConnection {
    static {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            throw new IllegalStateException("PostgreSQL JDBC Driver not found", e);
        }
    }

    private static final String DB_URL = "jdbc:postgresql://localhost:5432/proxy";
    private static final String USER = "postgres";
    private static final String PASSWORD = "12345";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, USER, PASSWORD);
    }

    public void storeInDatabase(String url, byte[] responseData, Date creationTime) {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASSWORD)) {
            String sql = "INSERT INTO cached_responses (url, response_data, creation_time) VALUES (?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, url);
                pstmt.setBytes(2, responseData);
                pstmt.setTimestamp(3, new Timestamp(creationTime.getTime()));
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace(); // Handle database exception properly
        }
    }

}
