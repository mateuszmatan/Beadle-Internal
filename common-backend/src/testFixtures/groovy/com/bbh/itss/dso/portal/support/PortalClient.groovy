package com.bbh.itss.dso.portal.support

import groovy.transform.Canonical

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

import static com.bbh.itss.dso.portal.support.Json.toJson
import static java.net.http.HttpClient.Builder.NO_PROXY
import static java.net.http.HttpRequest.BodyPublishers.noBody
import static java.net.http.HttpRequest.BodyPublishers.ofString
import static java.time.Duration.ofSeconds

class PortalClient {

    final String baseUrl
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(ofSeconds(5))
            .proxy(NO_PROXY)
            .build()

    PortalClient(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll('/+$', '')
    }

    Response get(String path) {
        send(request(path).GET())
    }

    Response post(String path, Object body = null) {
        send(request(path).POST(body == null ? noBody() : json(body)))
    }

    Response postRaw(String path, String contentType, String body) {
        send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(ofSeconds(30))
                .header('Content-Type', contentType)
                .POST(ofString(body)))
    }

    Response put(String path, Object body) {
        send(request(path).PUT(json(body)))
    }

    Response delete(String path) {
        send(request(path).DELETE())
    }

    private HttpRequest.Builder request(String path) {
        HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(ofSeconds(30))
                .header('Content-Type', 'application/json')
    }

    private static HttpRequest.BodyPublisher json(Object body) {
        ofString(toJson(body))
    }

    private Response send(HttpRequest.Builder request) {
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString())
        new Response(response.statusCode(), response.headers().map(), response.body())
    }

    @Canonical
    static class Response {
        int status
        Map<String, List<String>> headers
        String body

        Object getJson() {
            Json.parse(body)
        }

        String header(String name) {
            headers.find { it.key.equalsIgnoreCase(name) }?.value?.first()
        }

        @Override
        String toString() {
            "HTTP $status: $body"
        }
    }
}
