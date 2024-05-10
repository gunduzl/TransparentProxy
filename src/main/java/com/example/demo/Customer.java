package com.example.demo;

public class Customer {
    private int id;
    private String username;
    private String password;
    private boolean isLoginBefore;
    private RequestLogEntry requestLogEntry;

    public Customer(int id, String username, String password, boolean isLoginBefore, RequestLogEntry requestLogEntry) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.isLoginBefore = isLoginBefore;
        this.requestLogEntry = requestLogEntry;
    }


    public int getId() {
        return id;
    }


    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isLoginBefore() {
        return isLoginBefore;
    }

    public void setLoginBefore(boolean loginBefore) {
        isLoginBefore = loginBefore;
    }

    public RequestLogEntry getRequestLogEntry() {
        return requestLogEntry;
    }

    public void setRequestLogEntry(RequestLogEntry requestLogEntry) {
        this.requestLogEntry = requestLogEntry;
    }
}
