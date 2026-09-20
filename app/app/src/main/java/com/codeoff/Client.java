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

    public void sendCancelMatchmaking() {
        String json = gson.toJson(Map.of("type", "cancel_matchmaking"));
        send(json);
    }

    public void sendLogin(String savedUUID) {
        String json = savedUUID == null
            ? gson.toJson(Map.of("type", "login"))
            : gson.toJson(Map.of("type", "login", "uuid", savedUUID));
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
            case "matchmake_cancelled":
                Main.cardLayout.show(Main.cardPanel, "TITLE");
                break;
            case "match_found":
                Main.cardLayout.show(Main.cardPanel, "MATCHFOUND");
                matchId = data.get("matchId").getAsString();
                break;
            case "error":
                System.out.println("Server error: " + data.get("message").getAsString());
                Main.showError(data.get("message").getAsString());
                break;
            case "start_match":
                Main.resetGame();
                Main.cardLayout.show(Main.cardPanel, "GAME");
                Main.setPrompt(data.get("prompt").getAsString());
                Main.setMatchRank(data.get("rank").isJsonNull() ? null : data.get("rank").getAsString());
                Main.startCountdown(data.get("timeLimit").getAsInt());
                System.out.println("Match started! " + data);
                break;
            case "solution_result":
                Main.showSolutionResult(data.get("uuid").getAsString(), data.get("score").getAsDouble(),
                    data.has("feedback") ? data.get("feedback").getAsString() : null,
                    data.has("compileError") ? data.get("compileError").getAsString() : null);
                break;
            case "time_up":
                Main.onTimeUp();
                break;
            case "match_over":
                matchId = null;
                Main.showMatchOver(data);
                break;
            case "uuid":
                Main.setUUID(data.get("uuid").getAsString());
                if (!data.has("devAccount") || !data.get("devAccount").getAsBoolean()) {
                    Main.saveUUID(data.get("uuid").getAsString());
                }
                Main.setElo(data.get("elo").getAsInt(), data.get("rank").getAsString());
                Main.setHelpInfo(data.getAsJsonArray("ranks"), data.get("casualTimeLimit").getAsInt());
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