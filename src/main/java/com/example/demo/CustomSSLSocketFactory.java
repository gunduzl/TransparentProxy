package com.example.demo;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public class CustomSSLSocketFactory extends SSLSocketFactory {
    private SSLSocketFactory factory;

    public CustomSSLSocketFactory(SSLSocketFactory factory) {
        this.factory = factory;
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return factory.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return factory.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
        SSLSocket sslSocket = (SSLSocket) factory.createSocket(socket, host, port, autoClose);
        sslSocket.addHandshakeCompletedListener(event -> {
            SSLSession session = event.getSession();
            try {
                String sni = extractSniFromSession(session);
                System.out.println("SNI Hostname: " + sni);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        return sslSocket;
    }

    // Implement other abstract methods by delegating to the factory...
    @Override
    public Socket createSocket(String s, int i) throws IOException, UnknownHostException {
        return factory.createSocket(s, i);
    }

    @Override
    public Socket createSocket(String s, int i, InetAddress inetAddress, int i1) throws IOException, UnknownHostException {
        return factory.createSocket(s, i, inetAddress, i1);
    }

    @Override
    public Socket createSocket(InetAddress inetAddress, int i) throws IOException {
        return factory.createSocket(inetAddress, i);
    }

    @Override
    public Socket createSocket(InetAddress inetAddress, int i, InetAddress inetAddress1, int i1) throws IOException {
        return factory.createSocket(inetAddress, i, inetAddress1, i1);
    }

    private String extractSniFromSession(SSLSession session) {
        // Placeholder function; actual implementation might require access to private fields via reflection
        return "Extracted SNI"; // Replace with actual extraction logic
    }
}
