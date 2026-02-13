package handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class StaticFileHandler implements HttpHandler {

    private static final String HTML_FILE_PATH = "doc/register.html";

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }

        Path filePath = Paths.get(HTML_FILE_PATH);

        if (Files.exists(filePath)) {
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");

            byte[] content = Files.readAllBytes(filePath);
            exchange.sendResponseHeaders(200, content.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(content);
            }
        } else {
            String errorMsg = "404 Not Found: " + HTML_FILE_PATH + " nicht gefunden.\n" +
                    "Server luft in: " + Paths.get("").toAbsolutePath().toString();
            sendResponse(exchange, 404, errorMsg);
        }
    }

    private void sendResponse(HttpExchange exchange, int code, String text) throws IOException {
        byte[] body = text.getBytes("UTF-8");
        exchange.sendResponseHeaders(code, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
