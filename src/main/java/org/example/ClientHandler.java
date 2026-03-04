package org.example;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ClientHandler extends Thread{

    private Socket socket;
    private ObjectInputStream inputStream;
    private ObjectOutputStream outputStream;
    private String clientName;

    public ClientHandler(Socket socket){
        this.socket = socket;
    }

    @Override
    public void run() {
        try{
            outputStream = new ObjectOutputStream(socket.getOutputStream());
            inputStream = new ObjectInputStream(socket.getInputStream());

            clientName = (String) inputStream.readObject();
            System.out.println(clientName + " Joined The Chat");

            Server.broadcast(new Message("Server", clientName + " Joined"));

            while(true){
                Message message = (Message) inputStream.readObject();
                Server.broadcast(message);
            }
        } catch (Exception e) {
            System.out.println("Client Name: " + clientName);
        } finally {
            Server.removeClient(this);
        }
    }

    public void sendMessage(Message message){
        try{
            outputStream.writeObject(message);
        } catch (Exception e) {
            ServerLogger.error(e.getMessage());
        }
    }

    private void close(){
        try{
            socket.close();
        } catch (Exception ignored) {

        }
    }
}
