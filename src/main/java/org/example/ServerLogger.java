package org.example;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class ServerLogger {

    private static final Logger logger = Logger.getLogger("ChatServer");

    static {
        try {
            FileHandler fileHandler = new FileHandler("server_logs.txt", true);
            fileHandler.setFormatter(new Formatter() {
                @Override
                public String format(LogRecord record) {
                    return record.getLevel() + " | " + record.getMessage() + System.lineSeparator();
                }
            });
            logger.addHandler(fileHandler);
        } catch (IOException e) {
            logger.severe("Failed to initialize log file: " + e.getMessage());
        }
    }

    private ServerLogger() {

    }

    public static void serverStarted(int port) {
        logger.info("SERVER STARTED | Port: " + port + " | Time: " + LocalDateTime.now());
    }

    public static void serverStopped() {
        logger.info("SERVER STOPPED | Time: " + LocalDateTime.now());
    }

    public static void newClientConnected() {
        logger.info("NEW CLIENT CONNECTED | Time: " + LocalDateTime.now());
    }

    public static void userConnected(String username, String ip) {
        logger.info("USER CONNECTED | Username: " + username + " | IP: " + ip + " | Time: " + LocalDateTime.now());
    }

    public static void userDisconnected(String username) {
        logger.info("USER DISCONNECTED | Username: " + username + " | Time: " + LocalDateTime.now());
    }

    public static void userDisconnectedUnexpectedly(String username) {
        logger.warning("USER DISCONNECTED UNEXPECTEDLY | Username: " + username + " | Time: " + LocalDateTime.now());
    }

    public static void error(String message) {
        logger.severe("ERROR: " + message);
    }
}
