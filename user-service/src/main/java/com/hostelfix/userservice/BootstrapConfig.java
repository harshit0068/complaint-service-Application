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
			@Value("${HOSTELFIX_BOOTSTRAP_PASSWORD:}") String password,
			@Value("${HOSTELFIX_BOOTSTRAP_RESET:false}") boolean reset) {
		return args -> {
			if (email.isBlank() && password.isBlank()) {
				return;
			}
			if (email.isBlank() || password.length() < 8) {
				throw new IllegalStateException(
						"Set both HOSTELFIX_BOOTSTRAP_EMAIL and a HOSTELFIX_BOOTSTRAP_PASSWORD of at least 8 characters");
			}
			String normalizedEmail = email.trim().toLowerCase();
			var existing = users.findByEmailIgnoreCase(normalizedEmail);
			if (!reset) {
				if (existing.isEmpty()) {
					users.save(new AppUser("Hostel Warden", normalizedEmail,
							encoder.encode(password), UserRole.WARDEN, null));
				}
				return;
			}
			if (existing.isPresent() && existing.get().getRole() != UserRole.WARDEN) {
				throw new IllegalStateException("The bootstrap email belongs to a non-Warden account");
			}
			var wardens = users.findByRoleOrderByNameAsc(UserRole.WARDEN);
			AppUser warden;
			if (existing.isPresent()) {
				warden = existing.get();
			} else if (wardens.size() == 1) {
				warden = wardens.get(0);
			} else if (wardens.isEmpty()) {
				users.save(new AppUser("Hostel Warden", normalizedEmail,
						encoder.encode(password), UserRole.WARDEN, null));
				return;
			} else {
				throw new IllegalStateException(
						"Cannot choose which Warden to reset; set HOSTELFIX_BOOTSTRAP_EMAIL to an existing Warden email");
			}
			warden.setBootstrapCredentials(normalizedEmail, encoder.encode(password));
			users.save(warden);
		};
	}
}
