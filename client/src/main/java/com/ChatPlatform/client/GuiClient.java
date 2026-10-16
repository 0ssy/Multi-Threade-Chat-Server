package com.ChatPlatform.client;

import com.ChatPlatform.protocol.Message;
import com.ChatPlatform.protocol.MessageType;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.Map;

public class GuiClient extends Application {
    private static final String ESPRESSO = "#482E1D";
    private static final String CARAMEL = "#895D2B";
    private static final String LEAFY = "#A3966A";
    private static final String SAND = "#F0DAAE";
    private static final String CINNAMON = "#90553C";

    private ClientConnection conn;
    private TextArea chat = new TextArea();
    private TextField input = new TextField();
    private String username;
    private Label status = new Label("disconnected");

    @Override
    public void start(Stage stage) throws Exception {
        username = "user" + (int)(Math.random()*10000);
        connect();

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + SAND + ";");

        Label header = new Label("Chat Platform");
        header.setStyle("-fx-background-color: " + ESPRESSO + "; -fx-text-fill: " + SAND + "; -fx-font-size: 16px; -fx-padding: 10px;");
        HBox headerBox = new HBox(header);
        headerBox.setStyle("-fx-background-color: " + ESPRESSO + ";");
        HBox.setHgrow(header, Priority.ALWAYS);
        status.setStyle("-fx-text-fill: " + CINNAMON + "; -fx-padding: 0 10px;");
        headerBox.getChildren().add(status);
        root.setTop(headerBox);

        chat.setEditable(false);
        chat.setStyle("-fx-control-inner-background: " + SAND + "; -fx-text-fill: " + ESPRESSO + ";");
        root.setCenter(chat);

        Button send = new Button("Send");
        send.setStyle("-fx-background-color: " + LEAFY + "; -fx-text-fill: white;");
        send.setOnAction(e -> sendMsg());
        input.setOnAction(e -> sendMsg());
        input.setPromptText("Type a message or /command");
        HBox bottom = new HBox(input, send);
        HBox.setHgrow(input, Priority.ALWAYS);
        bottom.setPadding(new Insets(5));
        bottom.setStyle("-fx-background-color: " + ESPRESSO + ";");
        root.setBottom(bottom);

        Scene scene = new Scene(root, 700, 500);
        stage.setTitle("Chat Client");
        stage.setScene(scene);
        stage.show();
    }

    private void connect() {
        try {
            conn = new ClientConnection("localhost", 5000, username, "pass", new ClientConnection.Listener() {
                public void onMessage(Message m) { Platform.runLater(() -> append(m)); }
                public void onDisconnected() { Platform.runLater(() -> { status.setText("disconnected (reconnecting...)"); appendSys("disconnected"); }); }
                public void onConnectFailed(Throwable t) { Platform.runLater(() -> appendSys("connect failed: " + t)); }
            });
            conn.connect();
            conn.join("lobby");
            Platform.runLater(() -> { status.setText("connected"); appendSys("connected as " + username); });
        } catch (Exception e) {
            Platform.runLater(() -> appendSys("connect failed: " + e));
        }
    }

    private void append(Message m) {
        String t = m.type() + " " + m.fields();
        chat.appendText(t + "\n");
    }
    private void appendSys(String s) { chat.appendText("* " + s + "\n"); }

    private void sendMsg() {
        String text = input.getText().trim();
        if (text.isEmpty()) return;
        input.clear();
        if (text.startsWith("/")) {
            handleSlash(text);
        } else {
            conn.send(new Message(MessageType.MSG, Map.of("room", "lobby", "text", text)));
        }
    }

    private void handleSlash(String line) {
        String[] parts = line.substring(1).trim().split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String rest = parts.length > 1 ? parts[1] : "";
        switch (cmd) {
            case "join" -> conn.join(rest.isEmpty() ? "lobby" : rest);
            case "leave" -> conn.leave(rest.isEmpty() ? "lobby" : rest);
            case "msg", "dm" -> {
                String[] t = rest.split("\\s+", 2);
                if (t.length < 2) appendSys("usage: /msg <user> <text>");
                else conn.send(new Message(MessageType.DM, Map.of("to", t[0], "text", t[1])));
            }
            case "who" -> conn.send(new Message(MessageType.LIST_USERS, Map.of()));
            case "rooms" -> conn.send(new Message(MessageType.LIST_ROOMS, Map.of()));
            case "presence" -> conn.presence(rest.isEmpty() ? "ONLINE" : rest);
            case "typing" -> conn.typing(rest.isEmpty() ? "lobby" : rest);
            case "quit" -> { conn.close(); Platform.exit(); }
            case "help" -> {
                appendSys("/join [room]  /leave [room]  /msg <user> <text>");
                appendSys("/who  /rooms  /presence <status>  /typing [room]  /quit  /help");
            }
            default -> appendSys("unknown command: " + cmd);
        }
    }

    public static void main(String[] args) { launch(args); }
}
