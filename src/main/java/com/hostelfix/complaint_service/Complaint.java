package com.hostelfix.complaint_service;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "complaints")
public class Complaint {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long studentId;

	@Column(nullable = false, length = 150)
	private String title;

	@Column(nullable = false, length = 100)
	private String studentName;

	@Column(name = "category_id", nullable = false)
	private Integer categoryId;

	@Column(nullable = false, length = 50)
	private String category;

	@Column(nullable = false, length = 30)
	private String block;

	@Column(nullable = false, length = 2000)
	private String description;

	@Column(length = 500)
	private String photoUrl;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private ComplaintStatus status = ComplaintStatus.OPEN;

	private Long assignedStaffId;

	@Column(length = 100)
	private String assignedStaffName;

	@Column(length = 2000)
	private String resolutionNote;

	private Integer rating;

	@Column(length = 2000)
	private String feedback;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	private Instant updatedAt;

	private Instant resolvedAt;

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
		updatedAt = createdAt;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	public Long getId() { return id; }
	public Long getStudentId() { return studentId; }
	public void setStudentId(Long studentId) { this.studentId = studentId; }
	public String getTitle() { return title; }
	public void setTitle(String title) { this.title = title; }
	public String getStudentName() { return studentName; }
	public void setStudentName(String studentName) { this.studentName = studentName; }
	public Integer getCategoryId() { return categoryId; }
	public void setCategoryId(Integer categoryId) { this.categoryId = categoryId; }
	public String getCategory() { return category; }
	public void setCategory(String category) { this.category = category; }
	public String getBlock() { return block; }
	public void setBlock(String block) { this.block = block; }
	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }
	public String getPhotoUrl() { return photoUrl; }
	public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
	public ComplaintStatus getStatus() { return status; }
	public void setStatus(ComplaintStatus status) { this.status = status; }
	public Long getAssignedStaffId() { return assignedStaffId; }
	public void setAssignedStaffId(Long assignedStaffId) { this.assignedStaffId = assignedStaffId; }
	public String getAssignedStaffName() { return assignedStaffName; }
	public void setAssignedStaffName(String assignedStaffName) { this.assignedStaffName = assignedStaffName; }
	public String getResolutionNote() { return resolutionNote; }
	public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }
	public Integer getRating() { return rating; }
	public void setRating(Integer rating) { this.rating = rating; }
	public String getFeedback() { return feedback; }
	public void setFeedback(String feedback) { this.feedback = feedback; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
	public Instant getResolvedAt() { return resolvedAt; }
	public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
}
