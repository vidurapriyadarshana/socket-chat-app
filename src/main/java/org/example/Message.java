package org.example;

import java.io.Serial;
import java.io.Serializable;

public class Message implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public enum Type {
        CHAT,
        LOGOUT
    }

    private final String sender;
    private final String content;
    private final Type type;

    public Message(String sender, String content) {
        this.sender = sender;
        this.content = content;
        this.type = Type.CHAT;
    }

    public Message(String sender, String content, Type type) {
        this.sender = sender;
        this.content = content;
        this.type = type;
    }

    public String getSender() {
        return sender;
    }

    public String getContent() {
        return content;
    }

    public Type getType() {
        return type;
    }

    @Override
    public String toString() {
        return sender + ": " + content;
    }
}
