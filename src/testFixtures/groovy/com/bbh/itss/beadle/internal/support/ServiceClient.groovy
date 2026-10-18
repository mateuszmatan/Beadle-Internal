package com.bbh.itss.beadle.internal.support

import groovy.transform.Canonical

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

import static java.net.http.HttpClient.Builder.NO_PROXY
import static java.net.http.HttpRequest.BodyPublishers.ofString
import static java.time.Duration.ofSeconds

/**
 * Calls the API of the running application over HTTP, as Beadle-GUI does.
 */
class ServiceClient {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(ofSeconds(5)).proxy(NO_PROXY).build()

    final String baseUrl
    final Map<String, String> headers

    ServiceClient(String baseUrl, Map<String, String> headers = [:]) {
        this.baseUrl = baseUrl
        this.headers = headers.asImmutable()
    }

    ServiceClient withHeaders(Map<String, String> more) {
        new ServiceClient(baseUrl, headers + more)
    }

    Response get(String path) {
        send(request(path).GET())
    }

    Response post(String path, Object body) {
        send(request(path).POST(ofString(Json.toJson(body))))
    }

    Response put(String path, Object body) {
        send(request(path).PUT(ofString(Json.toJson(body))))
    }

    private HttpRequest.Builder request(String path) {
        def builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(ofSeconds(30))
                .header('Content-Type', 'application/json')
        headers.each { name, value -> builder.header(name, value) }
        builder
    }

    private static Response send(HttpRequest.Builder request) {
        HttpResponse<String> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString())
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
