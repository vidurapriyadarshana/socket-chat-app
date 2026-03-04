package org.example;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;

public class Client {

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 5000;
    private static final String LOGOUT_COMMAND = "/logout";

    public static void main(String[] args) {
        try (
            Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
            ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream());
            Scanner input = new Scanner(System.in)
        ) {
            System.out.print("Enter username: ");
            String name = input.nextLine().trim();
            outputStream.writeObject(name);

            Thread receiveThread = new Thread(() -> {
                try {
                    while (true) {
                        Message message = (Message) inputStream.readObject();
                        System.out.println(message);
                    }
                } catch (IOException e) {
                    System.out.println("[Disconnected from server]");
                } catch (ClassNotFoundException e) {
                    System.out.println("[Error: unknown message type received]");
                }
            }, "ReceiveThread-" + name);
            receiveThread.setDaemon(true);
            receiveThread.start();

            System.out.println("Connected as " + name + ". Type " + LOGOUT_COMMAND + " to exit.");

            while (true) {
                String text = input.nextLine();
                if (LOGOUT_COMMAND.equalsIgnoreCase(text.trim())) {
                    outputStream.writeObject(new Message(name, "has left the chat", Message.Type.LOGOUT));
                    System.out.println("[You have logged out]");
                    break;
                }
                outputStream.writeObject(new Message(name, text));
            }

        } catch (IOException e) {
            System.out.println("[Could not connect to server: " + e.getMessage() + "]");
        } catch (Exception e) {
            System.out.println("[Unexpected error: " + e.getMessage() + "]");
        }
    }
}
