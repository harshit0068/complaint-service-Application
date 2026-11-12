package com.hostelfix.complaint_service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class ComplaintController {
	private static final List<String> CATEGORIES = List.of(
			"Plumbing", "Electrical", "Wi-Fi", "Mess", "Cleaning", "Furniture", "Other");
	private static final List<String> IMAGE_TYPES = List.of("image/jpeg", "image/png", "image/webp", "image/gif");
	private final ComplaintRepository complaints;
	private final CategoryRepository categories;
	private final Path uploadDirectory;
	private final NotificationClient notifications;

	public ComplaintController(ComplaintRepository complaints, CategoryRepository categories,
			@Value("${hostelfix.upload-dir}") String uploadDirectory,
			NotificationClient notifications) {
		this.complaints = complaints;
		this.categories = categories;
		this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
		this.notifications = notifications;
	}

	@GetMapping("/health")
	public Map<String, String> health() {
		return Map.of("status", "ok", "service", "complaint-service");
	}

	@GetMapping("/complaints")
	public List<Complaint> list(@AuthenticationPrincipal CurrentUser user) {
		requireUser(user);
		if (user.canManageComplaints()) {
			return complaints.findAll().stream()
					.sorted(Comparator.comparing(Complaint::getCreatedAt).reversed())
					.toList();
		}
		if ("STAFF".equals(user.role())) {
			return complaints.findByAssignedStaffIdOrderByCreatedAtDesc(user.id());
		}
		if ("STUDENT".equals(user.role())) {
			return complaints.findByStudentIdOrderByCreatedAtDesc(user.id());
		}
		throw forbidden();
	}

	@GetMapping("/complaints/{id}")
	public Complaint get(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user) {
		Complaint complaint = findComplaint(id);
		requireCanView(complaint, user);
		return complaint;
	}

	@PostMapping("/complaints")
	@PreAuthorize("hasRole('STUDENT')")
	public Complaint create(@RequestPart("complaint") ComplaintRequest request,
			@RequestPart(value = "photo", required = false) MultipartFile photo,
			@AuthenticationPrincipal CurrentUser user) {
		requireUser(user);
		String category = requiredText(request.category(), "Category", 50);
		if (!CATEGORIES.contains(category)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid complaint category");
		}
		String block = requiredText(request.block(), "Block", 30);
		if (!block.equals(user.block())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Complaints must be submitted for your registered block");
		}
		String description = requiredText(request.description(), "Description", 2000);
		Category complaintCategory = categories.findByNameIgnoreCase(category)
				.orElseGet(() -> categories.save(new Category(category)));
		String title = category + ": " + description;

		Complaint complaint = new Complaint();
		complaint.setStudentId(user.id());
		complaint.setTitle(title.substring(0, Math.min(title.length(), 150)));
		complaint.setStudentName(user.name());
		complaint.setCategoryId(complaintCategory.getId());
		complaint.setCategory(category);
		complaint.setBlock(block);
		complaint.setDescription(description);
		if (photo != null && !photo.isEmpty()) {
			complaint.setPhotoUrl(savePhoto(photo));
		}
		return complaints.save(complaint);
	}

	@PatchMapping("/complaints/{id}/assign")
	@PreAuthorize("hasAnyRole('WARDEN', 'ADMIN')")
	public Complaint assign(@PathVariable Long id, @RequestBody AssignmentRequest request,
			@AuthenticationPrincipal CurrentUser user) {
		Complaint complaint = findComplaint(id);
		if (request.staffId() == null || request.staffId() < 1) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a staff member");
		}
		NotificationClient.StaffInfo staffMember = notifications.findStaff(request.staffId());
		complaint.setAssignedStaffId(staffMember.id());
		complaint.setAssignedStaffName(staffMember.name());
		complaint.setStatus(ComplaintStatus.ASSIGNED);
		complaint.setResolvedAt(null);
		Complaint saved = complaints.save(complaint);
		notifications.notifyUser(staffMember.id(), "Complaint assigned",
				"Complaint #" + id + " has been assigned to you.");
		return saved;
	}

	@PatchMapping("/complaints/{id}/status")
	@PreAuthorize("hasAnyRole('STAFF', 'WARDEN', 'ADMIN')")
	public Complaint updateStatus(@PathVariable Long id, @RequestBody StatusRequest request,
			@AuthenticationPrincipal CurrentUser user) {
		Complaint complaint = findComplaint(id);
		requireCanUpdate(complaint, user);
		if (request.status() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
		}
		if ("STAFF".equals(user.role()) && complaint.getAssignedStaffId() == null) {
			throw forbidden();
		}
		if (request.status() == ComplaintStatus.RESOLVED
				&& (request.resolutionNote() == null || request.resolutionNote().isBlank())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add a resolution note before closing");
		}
		complaint.setStatus(request.status());
		complaint.setResolutionNote(request.resolutionNote() == null
				? null : request.resolutionNote().trim());
		complaint.setResolvedAt(request.status() == ComplaintStatus.RESOLVED ? Instant.now() : null);
		Complaint saved = complaints.save(complaint);
		notifications.notifyUser(complaint.getStudentId(), "Complaint updated",
				"Complaint #" + id + " is now " + request.status().name().replace('_', ' ').toLowerCase() + ".");
		return saved;
	}

	@PostMapping("/complaints/{id}/rating")
	@PreAuthorize("hasRole('STUDENT')")
	public Complaint rate(@PathVariable Long id, @RequestBody RatingRequest request,
			@AuthenticationPrincipal CurrentUser user) {
		Complaint complaint = findComplaint(id);
		requireOwner(complaint, user);
		if (complaint.getStatus() != ComplaintStatus.RESOLVED) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Only resolved complaints can be rated");
		}
		if (request.rating() == null || request.rating() < 1 || request.rating() > 5) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rating must be from 1 to 5");
		}
		complaint.setRating(request.rating());
		complaint.setFeedback(request.feedback() == null ? null : request.feedback().trim());
		return complaints.save(complaint);
	}

	@GetMapping("/reports")
	@PreAuthorize("hasAnyRole('WARDEN', 'ADMIN')")
	public Reports reports() {
		List<Complaint> all = complaints.findAll();
		Map<String, Double> averageResolutionMinutes = all.stream()
				.filter(item -> item.getResolvedAt() != null)
				.collect(Collectors.groupingBy(Complaint::getCategory,
						Collectors.averagingDouble(item -> Duration.between(
								item.getCreatedAt(), item.getResolvedAt()).toSeconds() / 60.0)));
		Map<String, Long> pendingByBlock = all.stream()
				.filter(item -> item.getStatus() != ComplaintStatus.RESOLVED)
				.collect(Collectors.groupingBy(Complaint::getBlock, Collectors.counting()));
		long resolved = all.stream().filter(item -> item.getStatus() == ComplaintStatus.RESOLVED).count();
		return new Reports(all.size(), resolved, all.size() - resolved,
				averageResolutionMinutes, pendingByBlock);
	}

	private String savePhoto(MultipartFile photo) {
		if (!IMAGE_TYPES.contains(photo.getContentType())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Photo must be JPG, PNG, WEBP or GIF");
		}
		String extension = switch (photo.getContentType()) {
			case "image/jpeg" -> ".jpg";
			case "image/png" -> ".png";
			case "image/webp" -> ".webp";
			case "image/gif" -> ".gif";
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported photo type");
		};
		String name = UUID.randomUUID() + extension;
		try {
			Files.createDirectories(uploadDirectory);
			Files.copy(photo.getInputStream(), uploadDirectory.resolve(name));
			return "/uploads/" + name;
		} catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to save complaint photo", exception);
		}
	}

	private Complaint findComplaint(Long id) {
		return complaints.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Complaint not found"));
	}

	private void requireCanView(Complaint complaint, CurrentUser user) {
		requireUser(user);
		if (user.canManageComplaints()
				|| ("STUDENT".equals(user.role()) && complaint.getStudentId().equals(user.id()))
				|| ("STAFF".equals(user.role()) && complaint.getAssignedStaffId() != null
						&& complaint.getAssignedStaffId().equals(user.id()))) {
			return;
		}
		throw forbidden();
	}

	private void requireCanUpdate(Complaint complaint, CurrentUser user) {
		requireUser(user);
		if (user.canManageComplaints()) {
			return;
		}
		if ("STAFF".equals(user.role()) && complaint.getAssignedStaffId() != null
				&& complaint.getAssignedStaffId().equals(user.id())) {
			return;
		}
		throw forbidden();
	}

	private void requireOwner(Complaint complaint, CurrentUser user) {
		requireUser(user);
		if (!"STUDENT".equals(user.role()) || !complaint.getStudentId().equals(user.id())) {
			throw forbidden();
		}
	}

	private void requireUser(CurrentUser user) {
		if (user == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue");
		}
	}

	private ResponseStatusException forbidden() {
		return new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to do that");
	}

	private String requiredText(String value, String label, int maxLength) {
		if (value == null || value.isBlank() || value.trim().length() > maxLength) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					label + " is required and must be at most " + maxLength + " characters");
		}
		return value.trim();
	}

	public record ComplaintRequest(String category, String block, String description) { }
	public record AssignmentRequest(Long staffId) { }
	public record StatusRequest(ComplaintStatus status, String resolutionNote) { }
	public record RatingRequest(Integer rating, String feedback) { }
	public record Reports(long total, long resolved, long pending,
			Map<String, Double> averageResolutionMinutes, Map<String, Long> pendingByBlock) { }
}
