package com.prepstart.repository;

import com.prepstart.model.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Integer> {

    List<Enrollment> findByUserId(Integer userId);
}