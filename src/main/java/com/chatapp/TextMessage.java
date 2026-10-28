
package com.chatapp;

public class TextMessage extends Message {

    public TextMessage(String sender, String content) {
        super(sender, content);
    }

    @Override
    public void displayMessage() {
        System.out.println("[TEXT] " + getSender() + ": " + getContent());
    }
}
