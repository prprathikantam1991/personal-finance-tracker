package com.pradeep.finance.security;

import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Exchanges a browser-visible, one-time code for an owner ID over the private service channel. */
@Component
public class AuthServiceHandoffClient {
    private static final Logger log = LoggerFactory.getLogger(AuthServiceHandoffClient.class);
    private final RestClient restClient;
    private final String serviceKey;

    public AuthServiceHandoffClient(
            RestClient.Builder restClientBuilder,
            @Value("${finance.security.handoff.auth-service-url:http://localhost:8081}") String authServiceUrl,
            @Value("${finance.security.handoff.service-key:}") String serviceKey) {
        this.restClient = restClientBuilder.baseUrl(authServiceUrl).build();
        this.serviceKey = serviceKey;
    }

    public Optional<String> exchange(String code) {
        if (serviceKey == null || serviceKey.isBlank()) {
            log.warn("Finance handoff is unavailable because its service credential is not configured");
            return Optional.empty();
        }
        if (code == null || code.isBlank()) {
            log.warn("Finance handoff callback was received without a code");
            return Optional.empty();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(uriBuilder -> uriBuilder.path("/auth/finance/handoff/exchange")
                            .queryParam("code", code)
                            .build())
                    .header("X-Finance-Handoff-Key", serviceKey)
                    .retrieve()
                    .body(Map.class);
            Object ownerUserId = response == null ? null : response.get("ownerUserId");
            return ownerUserId instanceof String owner && !owner.isBlank() ? Optional.of(owner) : Optional.empty();
        } catch (RestClientException exception) {
            // Do not log the exception: the request URL includes a single-use handoff code.
            log.warn("Finance handoff exchange was rejected or unavailable");
            return Optional.empty();
        }
    }
}
