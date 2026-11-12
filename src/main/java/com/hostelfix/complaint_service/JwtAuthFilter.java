package com.hostelfix.complaint_service;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
	private final ObjectMapper objectMapper;
	private final byte[] secret;

	public JwtAuthFilter(ObjectMapper objectMapper,
			@Value("${hostelfix.jwt.secret}") String secret) {
		this.objectMapper = objectMapper;
		this.secret = secret.getBytes(StandardCharsets.UTF_8);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (authorization == null || !authorization.startsWith("Bearer ")) {
			filterChain.doFilter(request, response);
			return;
		}

		try {
			CurrentUser user = readUser(authorization.substring(7));
			var authentication = new UsernamePasswordAuthenticationToken(user, null,
					List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));
			SecurityContextHolder.getContext().setAuthentication(authentication);
			filterChain.doFilter(request, response);
		} catch (RuntimeException | IOException exception) {
			SecurityContextHolder.clearContext();
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.setContentType("application/json");
			response.getWriter().write("{\"error\":\"Invalid or expired bearer token\"}");
		}
	}

	private CurrentUser readUser(String token) throws IOException {
		String[] parts = token.split("\\.", -1);
		if (parts.length != 3) {
			throw new IllegalArgumentException("Malformed token");
		}
		byte[] suppliedSignature = Base64.getUrlDecoder().decode(parts[2]);
		byte[] expectedSignature = sign(parts[0] + "." + parts[1]);
		if (!MessageDigest.isEqual(suppliedSignature, expectedSignature)) {
			throw new IllegalArgumentException("Invalid token signature");
		}

		Map<String, Object> claims = objectMapper.readValue(
				Base64.getUrlDecoder().decode(parts[1]),
				new TypeReference<Map<String, Object>>() { });
		Object expiry = claims.get("exp");
		if (!(expiry instanceof Number number) || number.longValue() <= System.currentTimeMillis() / 1000) {
			throw new IllegalArgumentException("Expired token");
		}
		return new CurrentUser(
				Long.valueOf(claims.get("sub").toString()),
				claims.get("name").toString(),
				claims.get("email").toString(),
				claims.get("role").toString(),
				claims.get("block") == null ? null : claims.get("block").toString());
	}

	private byte[] sign(String content) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret, "HmacSHA256"));
			return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
		} catch (Exception exception) {
			throw new IllegalStateException("Unable to verify bearer token", exception);
		}
	}
}
