package de.etiennebader.civ.server.web;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Ein kleiner HTTP/1.1-Server auf Basis von {@link ServerSocket}: eine Anfrage je Verbindung, Körper nur mit
 * {@code Content-Length}, höchstens {@value #MAX_BODY} Bytes. Genügt für die Webseite des Mods und kommt
 * ohne {@code jdk.httpserver} aus, das nicht jede Java-Laufzeit mitbringt.
 */
public final class MiniHttpServer implements AutoCloseable {

    static final int MAX_BODY = 16 * 1024;
    private static final int MAX_HEADER = 16 * 1024;
    private static final int TIMEOUT_MILLIS = 10_000;

    private final ServerSocket socket;
    private final ExecutorService workers;
    private final Thread acceptor;
    private final Function<HttpRequest, HttpResponse> handler;
    private volatile boolean running = true;

    private MiniHttpServer(ServerSocket socket, Function<HttpRequest, HttpResponse> handler, int threads) {
        this.socket = socket;
        this.handler = handler;
        this.workers = Executors.newFixedThreadPool(threads, runnable -> {
            Thread thread = new Thread(runnable, "civ-web-worker");
            thread.setDaemon(true);
            return thread;
        });
        this.acceptor = new Thread(this::acceptLoop, "civ-web-accept");
        this.acceptor.setDaemon(true);
    }

    /**
     * Startet den Server.
     *
     * @param port 0 für einen freien Port
     * @throws IOException wenn der Port nicht gebunden werden kann
     */
    public static MiniHttpServer start(String bind, int port, int threads, Function<HttpRequest, HttpResponse> handler)
            throws IOException {
        Objects.requireNonNull(handler, "handler");
        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(InetAddress.getByName(bind), port));
        MiniHttpServer server = new MiniHttpServer(socket, handler, threads);
        server.acceptor.start();
        return server;
    }

    public int port() {
        return socket.getLocalPort();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket client = socket.accept();
                workers.execute(() -> serve(client));
            } catch (SocketException e) {
                return;
            } catch (IOException e) {
                if (!running) {
                    return;
                }
            }
        }
    }

    private void serve(Socket client) {
        try (client) {
            client.setSoTimeout(TIMEOUT_MILLIS);
            HttpResponse response;
            try {
                HttpRequest request = read(new BufferedInputStream(client.getInputStream()),
                        client.getInetAddress().getHostAddress());
                response = handler.apply(request);
            } catch (BadRequestException e) {
                response = HttpResponse.error(e.status, e.getMessage());
            } catch (RuntimeException e) {
                response = HttpResponse.error(500, "Interner Fehler");
            }
            write(client.getOutputStream(), response);
        } catch (IOException ignored) {
            // Verbindung abgebrochen; nichts zu tun.
        }
    }

    static HttpRequest read(InputStream in, String remote) throws IOException {
        String head = readHead(in);
        String[] lines = head.split("\r\n");
        String[] requestLine = lines[0].split(" ");
        if (requestLine.length != 3) {
            throw new BadRequestException(400, "Ungültige Anfrage");
        }
        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0) {
                headers.put(lines[i].substring(0, colon).strip().toLowerCase(Locale.ROOT), lines[i].substring(colon + 1).strip());
            }
        }
        int length = 0;
        if (headers.containsKey("content-length")) {
            try {
                length = Integer.parseInt(headers.get("content-length"));
            } catch (NumberFormatException e) {
                throw new BadRequestException(400, "Ungültige Länge");
            }
        }
        if (length < 0 || length > MAX_BODY) {
            throw new BadRequestException(413, "Anfrage zu groß");
        }
        byte[] body = in.readNBytes(length);
        String target = requestLine[1];
        int question = target.indexOf('?');
        String path = question < 0 ? target : target.substring(0, question);
        Map<String, String> query = new HashMap<>();
        if (question >= 0) {
            for (String pair : target.substring(question + 1).split("&")) {
                String[] parts = pair.split("=", 2);
                if (parts.length == 2) {
                    query.put(decode(parts[0]), decode(parts[1]));
                }
            }
        }
        return new HttpRequest(requestLine[0].toUpperCase(Locale.ROOT), path, query, headers,
                new String(body, StandardCharsets.UTF_8), remote);
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(400, "Ungültige Kodierung");
        }
    }

    private static String readHead(InputStream in) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int last = 0;
        while (last != 0x0D0A0D0A) {
            int b = in.read();
            if (b < 0) {
                throw new BadRequestException(400, "Verbindung vorzeitig beendet");
            }
            head.write(b);
            if (head.size() > MAX_HEADER) {
                throw new BadRequestException(431, "Kopfzeilen zu groß");
            }
            last = (last << 8) | b;
        }
        String text = head.toString(StandardCharsets.ISO_8859_1);
        return text.substring(0, text.length() - 4);
    }

    private static void write(OutputStream out, HttpResponse response) throws IOException {
        StringBuilder head = new StringBuilder()
                .append("HTTP/1.1 ").append(response.status()).append(' ').append(reason(response.status())).append("\r\n")
                .append("Content-Type: ").append(response.contentType()).append("\r\n")
                .append("Content-Length: ").append(response.body().length).append("\r\n")
                .append("Connection: close\r\n")
                .append("X-Content-Type-Options: nosniff\r\n")
                .append("Referrer-Policy: no-referrer\r\n");
        response.headers().forEach((name, value) -> head.append(name).append(": ").append(value).append("\r\n"));
        head.append("\r\n");
        out.write(head.toString().getBytes(StandardCharsets.UTF_8));
        out.write(response.body());
        out.flush();
    }

    private static String reason(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 413 -> "Payload Too Large";
            case 429 -> "Too Many Requests";
            case 503 -> "Service Unavailable";
            default -> status >= 500 ? "Server Error" : "Status";
        };
    }

    @Override
    public void close() {
        running = false;
        try {
            socket.close();
        } catch (IOException ignored) {
            // Beim Herunterfahren egal.
        }
        workers.shutdown();
        try {
            workers.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Eine Anfrage, die nicht verarbeitet werden kann. */
    static final class BadRequestException extends RuntimeException {

        private static final long serialVersionUID = 1L;
        final int status;

        BadRequestException(int status, String message) {
            super(message);
            this.status = status;
        }
    }
}
