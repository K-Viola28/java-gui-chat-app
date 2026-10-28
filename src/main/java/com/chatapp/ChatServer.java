
package com.chatapp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

public class ChatServer {

    private static final int PORT = 8080;

    private static final Path STATIC_DIR =
            Path.of("src/main/resources/static")
                    .toAbsolutePath()
                    .normalize();

    private static Database database;

    public static void main(String[] args)
            throws IOException, SQLException {

        database = new Database();

        // Add the welcome message only when the database is empty.
        if (database.getAllMessages().isEmpty()) {
            database.saveMessage(
                    new SystemMessage(
                            "Welcome to the chat! Send your first message below."
                    )
            );
        }

        HttpServer server = HttpServer.create(
                new InetSocketAddress("0.0.0.0", PORT), 0
        );

        server.createContext(
                "/api/messages", ChatServer::handleMessages
        );

        server.createContext("/", ChatServer::handleStaticFiles);

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("Chat application running on port " + PORT);
        System.out.println("Database connected successfully.");
    }

    private static void handleMessages(HttpExchange exchange)
            throws IOException {

        try {
            String method = exchange.getRequestMethod();

            if ("GET".equalsIgnoreCase(method)) {

                List<Message> messages = database.getAllMessages();
                StringBuilder json = new StringBuilder("[");
                boolean first = true;

                for (Message message : messages) {
                    if (!first) {
                        json.append(",");
                    }

                    json.append(messageToJson(message));
                    first = false;
                }

                json.append("]");

                sendText(
                        exchange, 200,
                        "application/json; charset=utf-8",
                        json.toString()
                );

            } else if ("POST".equalsIgnoreCase(method)) {

                String body = new String(
                        exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8
                );

                Map<String, String> form = parseForm(body);

                String sender =
                        form.getOrDefault("sender", "Guest").trim();

                String content =
                        form.getOrDefault("content", "").trim();

                if (sender.isEmpty()) {
                    sender = "Guest";
                }

                if (sender.length() > 50 || content.length() > 2000) {
                    sendText(
                            exchange, 400,
                            "application/json; charset=utf-8",
                            "{\"error\":\"Name or message is too long.\"}"
                    );
                    return;
                }

                if (content.isEmpty()) {
                    sendText(
                            exchange, 400,
                            "application/json; charset=utf-8",
                            "{\"error\":\"Message cannot be empty.\"}"
                    );
                    return;
                }

                Message message = new TextMessage(sender, content);

                // Save the message before reporting success.
                database.saveMessage(message);

                sendText(
                        exchange, 201,
                        "application/json; charset=utf-8",
                        messageToJson(message)
                );

            } else {
                exchange.getResponseHeaders().set("Allow", "GET, POST");

                sendText(
                        exchange, 405,
                        "application/json; charset=utf-8",
                        "{\"error\":\"Method not allowed.\"}"
                );
            }

        } catch (SQLException e) {
            e.printStackTrace();

            sendText(
                    exchange, 500,
                    "application/json; charset=utf-8",
                    "{\"error\":\"A database operation failed.\"}"
            );
        }
    }

    private static void handleStaticFiles(HttpExchange exchange)
            throws IOException {

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(
                    exchange, 405, "text/plain; charset=utf-8",
                    "Method not allowed."
            );
            return;
        }

        String requestedPath = exchange.getRequestURI().getPath();

        if ("/".equals(requestedPath)) {
            requestedPath = "/index.html";
        }

        String relativePath = requestedPath.startsWith("/")
                ? requestedPath.substring(1)
                : requestedPath;

        Path file = STATIC_DIR.resolve(relativePath).normalize();

        if (!file.startsWith(STATIC_DIR)
                || !Files.isRegularFile(file)) {
            sendText(
                    exchange, 404, "text/plain; charset=utf-8",
                    "File not found."
            );
            return;
        }

        String name = file.getFileName()
                .toString()
                .toLowerCase(Locale.ROOT);

        String contentType;

        if (name.endsWith(".html")) {
            contentType = "text/html; charset=utf-8";
        } else if (name.endsWith(".css")) {
            contentType = "text/css; charset=utf-8";
        } else if (name.endsWith(".js")) {
            contentType = "application/javascript; charset=utf-8";
        } else {
            contentType = "application/octet-stream";
        }

        byte[] data = Files.readAllBytes(file);

        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");

        exchange.sendResponseHeaders(200, data.length);

        try (OutputStream output = exchange.getResponseBody()) {
            output.write(data);
        }
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> values = new HashMap<>();

        for (String pair : body.split("&")) {
            int separator = pair.indexOf("=");

            String key = separator >= 0
                    ? pair.substring(0, separator)
                    : pair;

            String value = separator >= 0
                    ? pair.substring(separator + 1)
                    : "";

            values.put(
                    URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8)
            );
        }

        return values;
    }

    private static String messageToJson(Message message) {
        String type = message instanceof SystemMessage
                ? "system" : "text";

        return "{\"sender\":\"" + jsonEscape(message.getSender())
                + "\",\"content\":\"" + jsonEscape(message.getContent())
                + "\",\"type\":\"" + type + "\"}";
    }

    private static String jsonEscape(String value) {
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            switch (c) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (c < 0x20) {
                        result.append(String.format("\\u%04x", (int) c));
                    } else {
                        result.append(c);
                    }
                }
            }
        }

        return result.toString();
    }

    private static void sendText(
            HttpExchange exchange,
            int status,
            String contentType,
            String body
    ) throws IOException {

        byte[] data = body.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, data.length);

        try (OutputStream output = exchange.getResponseBody()) {
            output.write(data);
        }
    }
}

