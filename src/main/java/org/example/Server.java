package org.example;

import java.util.ArrayList;
import java.util.List;

public class Server {

    public static List<ClientHandler> clients = new ArrayList<>();

    public static void main(String[] args) {
        int port = 3000;
        System.out.println("Server Started On: " + port);



    }

    public static void broadcast(Message message){
        for(ClientHandler client : clients){
            client.sendMessage(message);
        }
    }

    public static void removeClient(ClientHandler clientHandler) {
        clients.remove(clientHandler);
    }
}
