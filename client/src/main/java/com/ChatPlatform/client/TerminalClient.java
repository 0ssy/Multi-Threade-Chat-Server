package com.ChatPlatform.client;

import com.ChatPlatform.protocol.Message;
import com.ChatPlatform.protocol.MessageType;

import java.util.Map;
import java.util.Scanner;

public class TerminalClient {
    private final ClientConnection conn;

    public TerminalClient(String host, int port, String username, String password) {
        this.conn = new ClientConnection(host, port, username, password, new ClientConnection.Listener() {
            public void onMessage(Message m) { System.out.println("[" + m.type() + "] " + m.fields()); }
            public void onDisconnected() { System.out.println("*** disconnected (reconnecting...)"); }
            public void onConnectFailed(Throwable t) { System.out.println("*** connect failed: " + t); }
        });
    }

    public void start() throws Exception {
        conn.connect();
        System.out.println("Connected as " + conn.username() + ". Type /help for commands.");
        Scanner sc = new Scanner(System.in);
        while (sc.hasNextLine()) {
            String line = sc.nextLine();
            if (line == null || line.isEmpty()) continue;
            if (!handleSlash(line)) {
                conn.send(new Message(MessageType.MSG, Map.of("room", "lobby", "text", line)));
            }
        }
        conn.close();
    }

    private boolean handleSlash(String line) {
        if (!line.startsWith("/")) return false;
        String[] parts = line.substring(1).trim().split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String rest = parts.length > 1 ? parts[1] : "";
        switch (cmd) {
            case "join" -> conn.join(rest.isEmpty() ? "lobby" : rest);
            case "leave" -> conn.leave(rest.isEmpty() ? "lobby" : rest);
            case "msg", "dm" -> {
                String[] t = rest.split("\\s+", 2);
                if (t.length < 2) System.out.println("usage: /msg <user> <text>");
                else conn.send(new Message(MessageType.DM, Map.of("to", t[0], "text", t[1])));
            }
            case "who" -> conn.send(new Message(MessageType.LIST_USERS, Map.of()));
            case "rooms" -> conn.send(new Message(MessageType.LIST_ROOMS, Map.of()));
            case "history" -> {
                String[] h = rest.split("\\s+", 2);
                int limit = 50;
                String before = null;
                String room = h.length > 0 ? h[0] : "lobby";
                if (h.length > 1) { try { limit = Integer.parseInt(h[1]); } catch (NumberFormatException ignored) {} }
                conn.history(room, before, limit);
            }
            case "presence" -> conn.presence(rest.isEmpty() ? "ONLINE" : rest);
            case "typing" -> conn.typing(rest.isEmpty() ? "lobby" : rest);
            case "quit" -> { conn.close(); return true; }
            case "reconnect" -> { conn.close(); try { conn.connect(); } catch (Exception e) { System.out.println("reconnect failed: " + e); } }
            case "help" -> {
                System.out.println("/join [room]  /leave [room]  /msg <user> <text>");
                System.out.println("/who  /rooms  /history [room] [limit]  /presence <status>");
                System.out.println("/typing [room]  /reconnect  /quit  /help");
            }
            default -> System.out.println("unknown command: " + cmd);
        }
        return true;
    }

    public void close() { conn.close(); }

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5000;
        String user = args.length > 2 ? args[2] : "user" + (int)(Math.random()*10000);
        TerminalClient c = new TerminalClient(host, port, user, "pass");
        Runtime.getRuntime().addShutdownHook(new Thread(c::close));
        c.start();
    }
}
