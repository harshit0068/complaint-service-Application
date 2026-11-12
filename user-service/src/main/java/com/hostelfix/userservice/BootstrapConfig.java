package com.hostelfix.userservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class BootstrapConfig {
	@Bean
	CommandLineRunner createBootstrapWarden(AppUserRepository users, PasswordEncoder encoder,
			@Value("${HOSTELFIX_BOOTSTRAP_EMAIL:}") String email,
			@Value("${HOSTELFIX_BOOTSTRAP_PASSWORD:}") String password) {
		return args -> {
			if (email.isBlank() && password.isBlank()) {
				return;
			}
			if (email.isBlank() || password.length() < 12) {
				throw new IllegalStateException(
						"Set both HOSTELFIX_BOOTSTRAP_EMAIL and a HOSTELFIX_BOOTSTRAP_PASSWORD of at least 12 characters");
			}
			if (!users.existsByEmailIgnoreCase(email)) {
				users.save(new AppUser("Hostel Warden", email.trim().toLowerCase(),
						encoder.encode(password), UserRole.WARDEN, null));
			}
		};
	}
}
