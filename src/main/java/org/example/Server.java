package org.example;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Server {

    public static final int PORT = 5000;
    public static final List<ClientHandler> clients = new CopyOnWriteArrayList<>();

    private Server() {
        
    }

    public static void main(String[] args) {
        ServerLogger.serverStarted(PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {

            while (true) {
                Socket socket = serverSocket.accept();
                ServerLogger.newClientConnected();

                ClientHandler handler = new ClientHandler(socket);
                clients.add(handler);
                handler.start();
            }

        } catch (Exception e) {
            ServerLogger.error(e.getMessage());
        } finally {
            ServerLogger.serverStopped();
        }
    }

    public static void broadcast(String message) {
        ServerLogger.messageBroadcast(message);
        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }
    }

    public static void removeClient(ClientHandler client) {
        clients.remove(client);
    }
}
