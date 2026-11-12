package com.hostelfix.userservice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "users")
public class AppUser {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false, unique = true, length = 254)
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(name = "password", nullable = false)
	private String legacyPasswordHash;

	@Column(nullable = false, length = 30)
	@Enumerated(EnumType.STRING)
	private UserRole role;

	@Column(length = 30)
	private String block;

	@Column(length = 50)
	private String specialty;

	@Column(updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	protected AppUser() { }

	public AppUser(String name, String email, String passwordHash, UserRole role, String block) {
		this(name, email, passwordHash, role, block, null);
	}

	public AppUser(String name, String email, String passwordHash, UserRole role, String block, String specialty) {
		this.name = name;
		this.email = email;
		this.passwordHash = passwordHash;
		this.legacyPasswordHash = passwordHash;
		this.role = role;
		this.block = block;
		this.specialty = specialty;
	}

	public Long getId() { return id; }
	public String getName() { return name; }
	public String getEmail() { return email; }
	public String getPasswordHash() { return passwordHash; }
	public UserRole getRole() { return role; }
	public String getBlock() { return block; }
	public String getSpecialty() { return specialty; }
	public void setSpecialty(String specialty) { this.specialty = specialty; }
}
