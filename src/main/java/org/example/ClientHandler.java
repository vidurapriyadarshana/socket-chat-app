package org.example;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

public class ClientHandler extends Thread {

    private final Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private String username;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            username = in.readUTF();
            setName("ClientHandler-" + username);
            Server.registerClient(username, this);

            ServerLogger.userConnected(username, socket.getInetAddress().getHostAddress());
            Server.broadcast(username + " joined the chat");

            while (true) {
                String message = in.readUTF();

                if (message.equalsIgnoreCase("/quit")) {
                    Server.broadcast(username + " left the chat");
                    ServerLogger.userDisconnected(username);
                    break;
                }

                if (message.startsWith("@")) {
                    int spaceIndex = message.indexOf(' ');
                    if (spaceIndex > 1) {
                        String targetUsername = message.substring(1, spaceIndex);
                        String privateText = message.substring(spaceIndex + 1);
                        Server.sendPrivate(username, targetUsername, privateText);
                    } else {
                        sendMessage("[Server]: Usage: @username message");
                    }
                    continue;
                }

                Server.broadcast(username + ": " + message);
            }

        } catch (Exception e) {
            if (username != null) {
                ServerLogger.userDisconnectedUnexpectedly(username);
                Server.broadcast(username + " lost connection");
            } else {
                ServerLogger.error("Unnamed client disconnected: " + e.getMessage());
            }
        } finally {
            Server.removeClient(this);
            close();
        }
    }

    public void sendMessage(String message) {
        try {
            out.writeUTF(message);
            out.flush();
        } catch (Exception e) {
            ServerLogger.error("Failed to send message to " + username + ": " + e.getMessage());
        }
    }

    public String getUsername() {
        return username;
    }

    private void close() {
        try {
            socket.close();
        } catch (Exception e) {
            ServerLogger.error("Failed to close socket for " + username + ": " + e.getMessage());
        }
    }
}
