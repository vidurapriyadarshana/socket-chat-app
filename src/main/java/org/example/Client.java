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

            new Thread(() -> {
                try {
                    while (true) {
                        String message = in.readUTF();

                        if (message.startsWith(username + ": ")) {
                            continue;
                        }

                        System.out.println(message);
                    }
                } catch (Exception e) {
                    System.out.println("Disconnected from server");
                }
            }).start();

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
