package com.example.demo;

import java.util.ArrayList;
import java.util.List;

import java.sql.*;

public class FilteredListManager {
    private final Connection connection;

    public FilteredListManager() {
        try {
            connection = DatabaseConnection.getConnection();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IllegalStateException("Unable to establish database connection", e);
        }
    }

    public void addHost(String host) {
        try {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO filtered_hosts (host) VALUES (?)");
            statement.setString(1, host);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            // Handle exception appropriately
        }
    }

    public void removeHost(String host) {
        try {
            PreparedStatement statement = connection.prepareStatement("DELETE FROM filtered_hosts WHERE host = ?");
            statement.setString(1, host);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            // Handle exception appropriately
        }
    }

    public List<String> getFilteredHosts() {
        List<String> filteredHosts = new ArrayList<>();
        try {
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery("SELECT host FROM filtered_hosts");
            while (resultSet.next()) {
                filteredHosts.add(resultSet.getString("host"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            // Handle exception appropriately
        }
        return filteredHosts;
    }
}
