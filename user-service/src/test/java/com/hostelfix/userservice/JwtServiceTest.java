package com.hostelfix.userservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class JwtServiceTest {
	@Test
	void issueCreatesSignedTokenWithIdentityAndExpiryClaims() throws Exception {
		AppUser user = mock(AppUser.class);
		when(user.getId()).thenReturn(17L);
		when(user.getName()).thenReturn("Aanya Sharma");
		when(user.getEmail()).thenReturn("aanya@university.edu");
		when(user.getRole()).thenReturn(UserRole.STUDENT);
		when(user.getBlock()).thenReturn("A Block");

		String token = new JwtService(new ObjectMapper(),
				"test-secret-with-at-least-thirty-two-characters")
				.issue(user);
		String[] parts = token.split("\\.");

		assertEquals(3, parts.length);
		var claims = new ObjectMapper().readValue(Base64.getUrlDecoder().decode(parts[1]),
				new TypeReference<java.util.Map<String, Object>>() { });
		assertEquals("17", claims.get("sub").toString());
		assertEquals("Aanya Sharma", claims.get("name"));
		assertEquals("aanya@university.edu", claims.get("email"));
		assertEquals("STUDENT", claims.get("role"));
		assertEquals("A Block", claims.get("block"));
		assertTrue(((Number) claims.get("exp")).longValue() > System.currentTimeMillis() / 1000);
	}
}
