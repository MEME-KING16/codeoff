package com.codeoff;

public class Main {

    static Client client;
    
    public static void main(String[] args) {
        client = new Client();
        client.connect();
        client.sendMessage("Hello, Server!");
    }
}