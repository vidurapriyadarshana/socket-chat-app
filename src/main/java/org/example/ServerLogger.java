package org.example;

import java.time.LocalDateTime;
import java.util.logging.Logger;

public class ServerLogger {
    private static final Logger logger = Logger.getLogger("ChatServer");

    public static void serverStarted(int port) {
        logger.info("SERVER STARTED");
        logger.info("Port: " + port);
        logger.info("Start Time: " + LocalDateTime.now());
    }

    public static void serverStopped() {
        logger.info("SERVER STOPPED");
        logger.info("Stop Time: " + LocalDateTime.now());
    }

    public static void userConnected(String username, String ip) {
        logger.info("USER CONNECTED");
        logger.info("Username: " + username);
        logger.info("IP: " + ip);
        logger.info("Connected At: " + LocalDateTime.now());
    }

    public static void userDisconnected(String username) {
        logger.info("USER DISCONNECTED");
        logger.info("Username: " + username);
        logger.info("Disconnected At: " + LocalDateTime.now());
    }

    public static void error(String message) {
        logger.severe("ERROR: " + message);
    }

}
