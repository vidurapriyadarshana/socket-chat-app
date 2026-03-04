package org.example;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class Server {

    public static final int PORT = 5000;
    public static final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    public static final Map<String, ClientHandler> clientMap = new ConcurrentHashMap<>();

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
        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }
    }

    public static void registerClient(String username, ClientHandler handler) {
        clientMap.put(username, handler);
    }

    public static void removeClient(ClientHandler client) {
        clients.remove(client);
        clientMap.values().remove(client);
    }

    public static void sendPrivate(String fromUsername, String toUsername, String text) {
        ClientHandler target = clientMap.get(toUsername);
        if (target == null) {
            ClientHandler sender = clientMap.get(fromUsername);
            if (sender != null) {
                sender.sendMessage("[Server]: User '" + toUsername + "' not found.");
            }
            return;
        }
        target.sendMessage("[PM from " + fromUsername + "]: " + text);
    }
}
