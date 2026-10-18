package com.bbh.itss.beadle.internal.adapter.out.gui;

import com.bbh.itss.beadle.internal.adapter.ServiceTokenProperties;
import com.bbh.itss.beadle.internal.adapter.out.http.SystemClient;
import com.bbh.itss.beadle.internal.application.callback.port.out.CallbackPort;
import com.bbh.itss.beadle.internal.application.calllog.CallerContext;
import com.bbh.itss.beadle.internal.domain.callback.StatusCallback;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URLEncoder;

import static com.bbh.itss.beadle.internal.application.calllog.CallerContext.CORRELATION_HEADER;
import static com.bbh.itss.beadle.internal.application.calllog.CallerContext.SYSTEM_USER;
import static com.bbh.itss.beadle.internal.application.calllog.CallerContext.USER_HEADER;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Tells Beadle-GUI that a ticket it had created moved: the service token every call between the Beadle services
 * carries, {@code system} as {@code X-Beadle-User} (percent-encoded in UTF-8 as the contract has every value of that
 * header) and, in {@code X-Correlation-Id} only, the correlation id of the action that created the ticket, which the
 * poll puts in the {@link CallerContext}. Beadle-GUI answers {@code 202}.
 */
@Component
class GuiCallbackClient implements CallbackPort {

    static final String PATH = "/api/callbacks/internal";

    private final SystemClient gui;

    GuiCallbackClient(GuiProperties properties, ServiceTokenProperties token, RestClient.Builder builder) {
        this.gui = new SystemClient("Beadle-GUI", builder, properties.url(), token.token());
    }

    @Override
    public void notify(StatusCallback callback) {
        String correlationId = CallerContext.current().correlationId();
        gui.call("take the callback for " + callback.key(), client -> {
            ResponseEntity<Void> answer = client.post().uri(PATH).headers(headers -> {
                headers.set(USER_HEADER, URLEncoder.encode(SYSTEM_USER, UTF_8));
                if (correlationId != null) {
                    headers.set(CORRELATION_HEADER, correlationId);
                }
            }).body(callback).retrieve().toBodilessEntity();
            if (!answer.getStatusCode().is2xxSuccessful()) {
                throw new RestClientException(gui.name() + " answered " + answer.getStatusCode());
            }
            return answer;
        });
    }
}
