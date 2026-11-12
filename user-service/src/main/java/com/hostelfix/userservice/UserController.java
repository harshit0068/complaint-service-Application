package com.hostelfix.userservice;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class UserController {
	private static final List<String> STAFF_SPECIALTIES = List.of(
			"Plumbing", "Electrical", "Wi-Fi", "Cleaning", "Mess", "Furniture", "General maintenance");
	private final AppUserRepository users;
	private final NotificationRepository notifications;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final String internalSecret;

	public UserController(AppUserRepository users, NotificationRepository notifications,
			PasswordEncoder passwordEncoder, JwtService jwtService,
			@Value("${hostelfix.internal-secret}") String internalSecret) {
		this.users = users;
		this.notifications = notifications;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.internalSecret = internalSecret;
	}

	@GetMapping("/health")
	public java.util.Map<String, String> health() {
		return java.util.Map.of("status", "ok", "service", "user-service");
	}

	@PostMapping("/auth/register")
	public AuthResponse register(@RequestBody RegisterRequest request) {
		String name = required(request.name(), "Name", 100);
		String email = validEmail(request.email());
		String password = validPassword(request.password());
		String block = required(request.block(), "Block", 30);
		if (users.existsByEmailIgnoreCase(email)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
		}
		AppUser user = users.save(new AppUser(name, email, passwordEncoder.encode(password),
				UserRole.STUDENT, block));
		return new AuthResponse(jwtService.issue(user), profile(user));
	}

	@PostMapping("/auth/login")
	public AuthResponse login(@RequestBody LoginRequest request) {
		if (request.email() == null || request.password() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email and password are required");
		}
		AppUser user = users.findByEmailIgnoreCase(request.email().trim())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
		}
		return new AuthResponse(jwtService.issue(user), profile(user));
	}

	@GetMapping("/auth/me")
	public UserProfile me(@AuthenticationPrincipal AuthenticatedUser user) {
		requireUser(user);
		AppUser current = users.findById(user.id())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account no longer exists"));
		return profile(current);
	}

	@GetMapping("/users/staff")
	@PreAuthorize("hasAnyRole('WARDEN', 'ADMIN')")
	public List<UserProfile> staff() {
		return users.findByRoleOrderByNameAsc(UserRole.STAFF).stream().map(this::profile).toList();
	}

	@GetMapping("/internal/staff/{id}")
	public UserProfile internalStaff(@PathVariable Long id,
			@RequestHeader("X-Internal-Secret") String providedSecret) {
		requireInternalSecret(providedSecret);
		AppUser staffMember = users.findById(id)
				.filter(user -> user.getRole() == UserRole.STAFF)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff member not found"));
		return profile(staffMember);
	}

	@PostMapping("/users/staff")
	@PreAuthorize("hasAnyRole('WARDEN', 'ADMIN')")
	public UserProfile createStaff(@RequestBody StaffRequest request) {
		String name = required(request.name(), "Name", 100);
		String email = validEmail(request.email());
		String password = validPassword(request.password());
		String specialty = required(request.specialty(), "Work specialty", 50);
		if (!STAFF_SPECIALTIES.contains(specialty)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid work specialty");
		}
		if (users.existsByEmailIgnoreCase(email)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
		}
		return profile(users.save(new AppUser(name, email, passwordEncoder.encode(password),
				UserRole.STAFF, null, specialty)));
	}

	@PatchMapping("/users/staff/{id}/specialty")
	@PreAuthorize("hasAnyRole('WARDEN', 'ADMIN')")
	public UserProfile updateStaffSpecialty(@PathVariable Long id, @RequestBody SpecialtyRequest request) {
		AppUser staffMember = users.findById(id)
				.filter(user -> user.getRole() == UserRole.STAFF)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff member not found"));
		String specialty = required(request.specialty(), "Work specialty", 50);
		if (!STAFF_SPECIALTIES.contains(specialty)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid work specialty");
		}
		staffMember.setSpecialty(specialty);
		return profile(users.save(staffMember));
	}

	@GetMapping("/notifications")
	public List<Notification> myNotifications(@AuthenticationPrincipal AuthenticatedUser user) {
		requireUser(user);
		return notifications.findByUserIdOrderByCreatedAtDesc(user.id());
	}

	@PatchMapping("/notifications/{id}/read")
	public Notification markRead(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
		requireUser(user);
		Notification notification = notifications.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
		if (!notification.getUserId().equals(user.id())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot update another user's notification");
		}
		notification.markRead();
		return notifications.save(notification);
	}

	@PostMapping("/internal/notifications")
	public Notification createNotification(@RequestHeader("X-Internal-Secret") String providedSecret,
			@RequestBody NotificationRequest request) {
		requireInternalSecret(providedSecret);
		if (request.userId() == null || request.userId() < 1 || !users.existsById(request.userId())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Notification recipient is required");
		}
		return notifications.save(new Notification(request.userId(),
				required(request.title(), "Title", 150), required(request.message(), "Message", 500)));
	}

	private UserProfile profile(AppUser user) {
		return new UserProfile(user.getId(), user.getName(), user.getEmail(), user.getRole(),
				user.getBlock(), user.getSpecialty());
	}

	private void requireInternalSecret(String providedSecret) {
		if (!MessageDigest.isEqual(internalSecret.getBytes(StandardCharsets.UTF_8),
				providedSecret.getBytes(StandardCharsets.UTF_8))) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal service credential");
		}
	}

	private void requireUser(AuthenticatedUser user) {
		if (user == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue");
		}
	}

	private String required(String value, String label, int max) {
		if (value == null || value.isBlank() || value.trim().length() > max) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					label + " is required and must be at most " + max + " characters");
		}
		return value.trim();
	}

	private String validEmail(String email) {
		String value = required(email, "Email", 254).toLowerCase();
		if (!value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email address");
		}
		return value;
	}

	private String validPassword(String password) {
		if (password == null || password.length() < 8 || password.length() > 72) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be 8 to 72 characters");
		}
		return password;
	}

	public record RegisterRequest(String name, String email, String password, String block) { }
	public record LoginRequest(String email, String password) { }
	public record StaffRequest(String name, String email, String password, String specialty) { }
	public record SpecialtyRequest(String specialty) { }
	public record NotificationRequest(Long userId, String title, String message) { }
	public record UserProfile(Long id, String name, String email, UserRole role, String block, String specialty) { }
	public record AuthResponse(String token, UserProfile user) { }
}
