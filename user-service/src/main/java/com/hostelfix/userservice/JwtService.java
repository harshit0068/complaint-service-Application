package com.hostelfix.userservice;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
	private final ObjectMapper objectMapper;
	private final byte[] secret;

	public JwtService(ObjectMapper objectMapper,
			@Value("${hostelfix.jwt.secret}") String secret) {
		this.objectMapper = objectMapper;
		this.secret = secret.getBytes(StandardCharsets.UTF_8);
		if (this.secret.length < 32) {
			throw new IllegalArgumentException("HOSTELFIX_JWT_SECRET must be at least 32 bytes");
		}
	}

	public String issue(AppUser user) {
		try {
			String header = encode(Map.of("alg", "HS256", "typ", "JWT"));
			Map<String, Object> claims = new HashMap<>();
			claims.put("sub", user.getId());
			claims.put("name", user.getName());
			claims.put("email", user.getEmail());
			claims.put("role", user.getRole().name());
			claims.put("block", user.getBlock());
			claims.put("iat", Instant.now().getEpochSecond());
			claims.put("exp", Instant.now().plusSeconds(43_200).getEpochSecond());
			String payload = encode(claims);
			String content = header + "." + payload;
			return content + "." + ENCODER.encodeToString(sign(content));
		} catch (Exception exception) {
			throw new IllegalStateException("Unable to create sign-in token", exception);
		}
	}

	private String encode(Map<String, Object> values) throws Exception {
		return ENCODER.encodeToString(objectMapper.writeValueAsBytes(values));
	}

	private byte[] sign(String content) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(secret, "HmacSHA256"));
		return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
	}
}
