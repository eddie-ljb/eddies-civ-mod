package de.etiennebader.civ.server.web;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * Eine HTTP-Antwort.
 */
public record HttpResponse(int status, String contentType, byte[] body, Map<String, String> headers) {

    public HttpResponse {
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(body, "body");
        headers = Map.copyOf(headers);
    }

    public static HttpResponse json(int status, String json) {
        return new HttpResponse(status, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8), Map.of());
    }

    public static HttpResponse error(int status, String message) {
        return json(status, Json.object().put("error", message).build());
    }

    public HttpResponse withHeader(String name, String value) {
        java.util.Map<String, String> copy = new java.util.LinkedHashMap<>(headers);
        copy.put(name, value);
        return new HttpResponse(status, contentType, body, copy);
    }
}
