
package com.chatapp;

public class SystemMessage extends Message {

    public SystemMessage(String content) {
        super("System", content);
    }

    @Override
    public void displayMessage() {
        System.out.println("[SYSTEM] " + getContent());
    }
}
