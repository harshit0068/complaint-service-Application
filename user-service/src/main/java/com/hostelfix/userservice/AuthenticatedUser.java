package com.hostelfix.userservice;

public record AuthenticatedUser(Long id, String name, String email, UserRole role) { }
