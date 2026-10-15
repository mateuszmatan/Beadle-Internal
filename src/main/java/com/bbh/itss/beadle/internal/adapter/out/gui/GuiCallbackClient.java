package com.bbh.itss.beadle.internal.adapter.out.gui;

import com.bbh.itss.beadle.internal.application.callback.port.out.CallbackPort;
import com.bbh.itss.beadle.internal.config.ServiceTokenProperties;
import com.bbh.itss.beadle.internal.domain.callback.StatusCallback;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import static com.bbh.itss.beadle.internal.adapter.out.http.ExternalSystems.client;
import static com.bbh.itss.beadle.internal.adapter.out.http.ExternalSystems.refusal;
import static com.bbh.itss.beadle.internal.application.calllog.CallerContext.CORRELATION_HEADER;
import static com.bbh.itss.beadle.internal.application.calllog.CallerContext.SYSTEM_USER;
import static com.bbh.itss.beadle.internal.application.calllog.CallerContext.USER_HEADER;
import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * Tells Beadle-GUI that a ticket it had created moved, with the service token every call between the Beadle
 * services carries; Beadle-GUI answers {@code 202}.
 */
@Component
class GuiCallbackClient implements CallbackPort {

    static final String GUI = "Beadle-GUI";
    static final String PATH = "/api/callbacks/internal";
    static final String TAKE = "take the callback for ";

    private final RestClient client;

    GuiCallbackClient(GuiProperties properties, ServiceTokenProperties token, RestClient.Builder builder) {
        this.client = client(builder, properties.url(), token.token());
    }

    @Override
    public void notify(StatusCallback callback) {
        try {
            ResponseEntity<Void> answer = client.post().uri(PATH).contentType(APPLICATION_JSON).accept(APPLICATION_JSON)
                    .headers(headers -> caller(headers, callback)).body(callback).retrieve().toBodilessEntity();
            if (!answer.getStatusCode().is2xxSuccessful()) {
                throw new RestClientException(GUI + " answered " + answer.getStatusCode());
            }
        } catch (RestClientException e) {
            throw refusal(GUI, TAKE + callback.key(), e);
        }
    }

    private static void caller(HttpHeaders headers, StatusCallback callback) {
        headers.set(USER_HEADER, SYSTEM_USER);
        if (callback.correlationId() != null) {
            headers.set(CORRELATION_HEADER, callback.correlationId());
        }
    }
}
