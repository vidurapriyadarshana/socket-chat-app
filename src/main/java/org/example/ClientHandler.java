package org.example;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ClientHandler extends Thread {

    private final Socket socket;
    private ObjectInputStream inputStream;
    private ObjectOutputStream outputStream;
    private String clientName;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            outputStream = new ObjectOutputStream(socket.getOutputStream());
            inputStream = new ObjectInputStream(socket.getInputStream());

            clientName = (String) inputStream.readObject();
            setName("ClientHandler-" + clientName);

            ServerLogger.userConnected(clientName, socket.getInetAddress().getHostAddress());
            Server.broadcast(new Message("Server", clientName + " joined the chat"));

            while (true) {
                Message message = (Message) inputStream.readObject();

                if (message.getType() == Message.Type.LOGOUT) {
                    Server.broadcast(new Message("Server", clientName + " left the chat"));
                    ServerLogger.userDisconnected(clientName);
                    break;
                }

                Server.broadcast(message);
            }

        } catch (Exception e) {
            if (clientName != null) {
                ServerLogger.userDisconnected(clientName);
                Server.broadcast(new Message("Server", clientName + " lost connection"));
            } else {
                ServerLogger.error("Unnamed client disconnected: " + e.getMessage());
            }
        } finally {
            Server.removeClient(this);
            close();
        }
    }

    public void sendMessage(Message message) {
        try {
            outputStream.writeObject(message);
        } catch (Exception e) {
            ServerLogger.error("Failed to send message to " + clientName + ": " + e.getMessage());
        }
    }

    private void close() {
        try {
            socket.close();
        } catch (Exception e) {
            ServerLogger.error("Failed to close socket for " + clientName + ": " + e.getMessage());
        }
    }
}
