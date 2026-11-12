package com.hostelfix.complaint_service;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class NotificationClient {
	private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);
	private final RestClient restClient;
	private final String internalSecret;

	public NotificationClient(
			@Value("${hostelfix.user-service-url}") String userServiceUrl,
			@Value("${hostelfix.internal-secret}") String internalSecret) {
		this.restClient = RestClient.create(userServiceUrl);
		this.internalSecret = internalSecret;
	}

	public void notifyUser(Long userId, String title, String message) {
		try {
			restClient.post()
					.uri("/api/internal/notifications")
					.header("X-Internal-Secret", internalSecret)
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("userId", userId, "title", title, "message", message))
					.retrieve()
					.toBodilessEntity();
		} catch (RestClientException exception) {
			log.warn("Could not deliver notification to user {}: {}", userId, exception.getMessage());
		}
	}

	public StaffInfo findStaff(Long userId) {
		try {
			return restClient.get()
					.uri("/api/internal/staff/{id}", userId)
					.header("X-Internal-Secret", internalSecret)
					.retrieve()
					.body(StaffInfo.class);
		} catch (HttpClientErrorException.NotFound exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected account is not an active staff member");
		}
	}

	public record StaffInfo(Long id, String name) { }
}
