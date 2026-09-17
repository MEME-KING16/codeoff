package com.codeoff;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.net.URI;
import java.util.Map;

public class Client extends WebSocketClient {
    private static final Gson gson = new Gson();
    private String matchId;
    public Client(URI serverUri) {
        super(serverUri);
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        System.out.println("Connected!");
    }

    public void sendMatchmake(String mode, String uuid) {
        String json = gson.toJson(Map.of("type", "matchmake", "mode", mode, "uuid", uuid));
        send(json);
    }

    public void sendSolution(String solution) {
        String json = gson.toJson(Map.of("type", "submit_solution", "solution", solution, "matchId", matchId, "uuid", Main.getUUID()));
        send(json);
    }

    @Override
    public void onMessage(String message) {
        JsonObject data = gson.fromJson(message, JsonObject.class);
        String type = data.get("type").getAsString();

        switch (type) {
            case "matchmake_ack":
                String mode = data.get("mode").getAsString();
                System.out.println("Starting Matchmaking for: " + mode);
                    Main.cardLayout.show(Main.cardPanel, "MATCHMAKING");
                break;
            case "match_found":
                Main.cardLayout.show(Main.cardPanel, "MATCHFOUND");
                matchId = data.get("matchId").getAsString();
                break;
            case "error":
                System.out.println("Server error: " + data.get("message").getAsString());
                break;
            case "start_match":
                Main.cardLayout.show(Main.cardPanel, "GAME");
                Main.setPrompt(data.get("prompt").getAsString());
                System.out.println("Match started! " + data);
                break;
            case "uuid":
                Main.setUUID(data.get("uuid").getAsString());
                break;
            default:
                System.out.println("Unknown message type: " + type);
        }
    }

    public String getMatchId() {
        return matchId;
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("Closed: " + code + " " + reason);
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
    }
}