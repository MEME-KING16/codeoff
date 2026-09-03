package com.codeoff;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Client {
    private String host = "localhost";
    private int port = 3000;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    public void connect() {        
        try {
            // Establish the socket connection
            socket = new Socket(host, port);
            
            // Setup output stream to send data to server
            out = new PrintWriter(socket.getOutputStream(), true);
            
            // Setup input stream to read data from server
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            System.out.println("Connected successfully!");
        } catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        } else {
            System.err.println("Output stream is not initialized.");
        }
    }

    public String receiveMessage() {
        if (in != null) {
            try {
                return in.readLine();
            } catch (Exception e) {
                System.err.println("Error reading from input stream: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.err.println("Input stream is not initialized.");
        }
        return null;
    }
}
