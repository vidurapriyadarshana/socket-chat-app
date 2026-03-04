package org.example;

import java.time.LocalDateTime;
import java.util.logging.Logger;

public class ServerLogger {

    private static final Logger logger = Logger.getLogger("ChatServer");

    private ServerLogger() {
        // utility class — no instances
    }

    public static void serverStarted(int port) {
        logger.info("SERVER STARTED | Port: " + port + " | Time: " + LocalDateTime.now());
    }

    public static void serverStopped() {
        logger.info("SERVER STOPPED | Time: " + LocalDateTime.now());
    }

    public static void userConnected(String username, String ip) {
        logger.info("USER CONNECTED | Username: " + username + " | IP: " + ip + " | Time: " + LocalDateTime.now());
    }

    public static void userDisconnected(String username) {
        logger.info("USER DISCONNECTED | Username: " + username + " | Time: " + LocalDateTime.now());
    }

    public static void error(String message) {
        logger.severe("ERROR: " + message);
    }
}
