package com.hostelfix.complaint_service;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
	List<Complaint> findByStudentIdOrderByCreatedAtDesc(Long studentId);
	List<Complaint> findByAssignedStaffIdOrderByCreatedAtDesc(Long assignedStaffId);
}
