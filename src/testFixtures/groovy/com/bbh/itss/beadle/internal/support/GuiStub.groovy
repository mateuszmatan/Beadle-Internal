package com.bbh.itss.beadle.internal.support

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import groovy.transform.Canonical

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

import static java.nio.charset.StandardCharsets.UTF_8

/**
 * Stands in for Beadle-GUI: takes the callbacks of Beadle-Internal on {@code /api/callbacks/internal}, answers
 * {@code 202}, or {@code 503} as many times as a spec asked for a ticket, and keeps every request it got.
 */
class GuiStub {

    static final String PATH = '/api/callbacks/internal'

    private final HttpServer server
    private final List<Callback> received = new CopyOnWriteArrayList<>()
    private final Map<String, Integer> refusals = new ConcurrentHashMap<>()

    private GuiStub(HttpServer server) {
        this.server = server
    }

    static GuiStub start() {
        def stub = new GuiStub(HttpServer.create(new InetSocketAddress(InetAddress.loopbackAddress, 0), 0))
        stub.server.createContext(PATH) { HttpExchange exchange -> stub.answer(exchange) }
        stub.server.start()
        stub
    }

    String getUrl() {
        "http://${server.address.hostString}:${server.address.port}"
    }

    List<Callback> callbacksFor(String key) {
        received.findAll { it.body?.key == key }
    }

    void refuse(String key, int times) {
        refusals[key] = times
    }

    private void answer(HttpExchange exchange) {
        try {
            def body = Json.parse(exchange.requestBody.getText(UTF_8.name())) as Map
            int status = refused(body?.key as String) ? 503 : 202
            received << new Callback(exchange.requestMethod, exchange.requestURI.path,
                    exchange.requestHeaders.collectEntries { name, values -> [name.toLowerCase(), values.first()] },
                    body, status)
            exchange.sendResponseHeaders(status, -1)
        } finally {
            exchange.close()
        }
    }

    private boolean refused(String key) {
        Integer left = key == null ? null : refusals[key]
        if (!left) {
            return false
        }
        refusals[key] = left - 1
        true
    }

    @Canonical
    static class Callback {
        String method
        String path
        Map<String, String> headers
        Map body
        int status

        String header(String name) {
            headers[name.toLowerCase()]
        }
    }
}
