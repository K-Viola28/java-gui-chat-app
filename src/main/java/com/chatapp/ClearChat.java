
package com.chatapp;

public class ClearChat {

    public static void main(String[] args) {
        try {
            Database database = new Database();
            database.clearMessages();

            System.out.println("All chat messages have been cleared.");

        } catch (Exception e) {
            System.out.println("Could not clear messages: " + e.getMessage());
        }
    }
}
