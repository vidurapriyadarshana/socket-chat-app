package org.example;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.Scanner;

public class Client {

    public static void main(String[] args) {

        try (
            Socket socket = new Socket("localhost", 5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            Scanner scanner = new Scanner(System.in)
        ) {
            System.out.print("Enter your username: ");
            String username = scanner.nextLine();
            out.writeUTF(username);

            // Thread to receive messages
            new Thread(() -> {
                try {
                    while (true) {
                        String message = in.readUTF();

                        // Skip messages that are our own broadcast echo
                        if (message.startsWith(username + ": ")) {
                            continue;
                        }

                        System.out.println(message);
                    }
                } catch (Exception e) {
                    System.out.println("Disconnected from server");
                }
            }).start();

            // Send messages
            while (true) {
                String message = scanner.nextLine();
                out.writeUTF(message);

                if (message.equalsIgnoreCase("/quit")) {
                    break;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
