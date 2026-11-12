package com.hostelfix.userservice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "notifications")
public class Notification {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false, length = 150)
	private String title;

	@Column(nullable = false, length = 500)
	private String message;

	@Column(name = "is_read", nullable = false)
	private boolean read;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	protected Notification() { }

	public Notification(Long userId, String title, String message) {
		this.userId = userId;
		this.title = title;
		this.message = message;
	}

	public Long getId() { return id; }
	public Long getUserId() { return userId; }
	public String getTitle() { return title; }
	public String getMessage() { return message; }
	public boolean isRead() { return read; }
	public Instant getCreatedAt() { return createdAt; }
	public void markRead() { read = true; }
}
