package com.hostelfix.complaint_service;

public record CurrentUser(Long id, String name, String email, String role, String block) {
	public boolean canManageComplaints() {
		return "WARDEN".equals(role) || "ADMIN".equals(role);
	}
}
