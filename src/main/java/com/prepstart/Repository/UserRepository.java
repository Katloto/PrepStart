package com.prepstart.Repository;

import com.prepstart.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // ===== METHODS FOR USERNAME =====
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    // ===== Find user by reset token =====
    Optional<User> findByResetToken(String resetToken);
}