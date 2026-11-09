package de.etiennebader.civ.server.web;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Eine geparste HTTP-Anfrage.
 *
 * @param headers Namen in Kleinbuchstaben
 */
public record HttpRequest(String method, String path, Map<String, String> query, Map<String, String> headers,
                          String body, String remoteAddress) {

    public HttpRequest {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(path, "path");
        query = Map.copyOf(query);
        headers = Map.copyOf(headers);
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(remoteAddress, "remoteAddress");
    }

    public Optional<String> header(String name) {
        return Optional.ofNullable(headers.get(name.toLowerCase(java.util.Locale.ROOT)));
    }

    /** Der Wert eines Cookies, falls gesetzt. */
    public Optional<String> cookie(String name) {
        return header("cookie").flatMap(cookies -> {
            for (String part : cookies.split(";")) {
                String[] pair = part.strip().split("=", 2);
                if (pair.length == 2 && pair[0].equals(name)) {
                    return Optional.of(pair[1]);
                }
            }
            return Optional.empty();
        });
    }
}
