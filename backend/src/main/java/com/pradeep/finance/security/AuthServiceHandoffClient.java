package com.pradeep.finance.security;

import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Exchanges a browser-visible, one-time code for an owner ID over the private service channel. */
@Component
public class AuthServiceHandoffClient {
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
        if (serviceKey == null || serviceKey.isBlank() || code == null || code.isBlank()) return Optional.empty();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri("/auth/finance/handoff/exchange")
                    .header("X-Finance-Handoff-Key", serviceKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("code", code))
                    .retrieve()
                    .body(Map.class);
            Object ownerUserId = response == null ? null : response.get("ownerUserId");
            return ownerUserId instanceof String owner && !owner.isBlank() ? Optional.of(owner) : Optional.empty();
        } catch (RestClientException ignored) {
            return Optional.empty();
        }
    }
}
