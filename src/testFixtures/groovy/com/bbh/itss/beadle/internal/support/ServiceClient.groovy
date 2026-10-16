package com.bbh.itss.beadle.internal.support

import groovy.transform.Canonical

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

import static com.bbh.itss.beadle.internal.support.Json.toJson
import static java.net.http.HttpClient.Builder.NO_PROXY
import static java.net.http.HttpRequest.BodyPublishers.noBody
import static java.net.http.HttpRequest.BodyPublishers.ofString
import static java.time.Duration.ofSeconds

class ServiceClient {

    final String baseUrl
    final Map<String, String> headers
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(ofSeconds(5))
            .proxy(NO_PROXY)
            .build()

    ServiceClient(String baseUrl, Map<String, String> headers = [:]) {
        this.baseUrl = baseUrl.replaceAll('/+$', '')
        this.headers = headers.asImmutable()
    }

    ServiceClient withHeaders(Map<String, String> more) {
        new ServiceClient(baseUrl, headers + more)
    }

    ServiceClient withoutHeader(String name) {
        new ServiceClient(baseUrl, headers.findAll { !it.key.equalsIgnoreCase(name) })
    }

    Response get(String path) {
        send(request(path).GET())
    }

    Response post(String path, Object body = null) {
        send(request(path).POST(body == null ? noBody() : json(body)))
    }

    Response postRaw(String path, String contentType, String body) {
        send(request(path, contentType).POST(ofString(body)))
    }

    Response put(String path, Object body) {
        send(request(path).PUT(json(body)))
    }

    Response delete(String path) {
        send(request(path).DELETE())
    }

    private HttpRequest.Builder request(String path, String contentType = 'application/json') {
        def builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(ofSeconds(30))
                .header('Content-Type', contentType)
        headers.each { name, value -> builder.header(name, value) }
        builder
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
