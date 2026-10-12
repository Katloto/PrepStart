package com.prepstart.Repository;

import com.prepstart.model.Progress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProgressRepository extends JpaRepository<Progress, Integer> {

    List<Progress> findByUserId(Integer userId);
}