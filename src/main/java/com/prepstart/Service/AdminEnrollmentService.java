package com.prepstart.Service;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Academic enrollment using only the existing PrepStart tables.
 * University: User.university_id
 * Course: inferred from Enrollment -> Module.course_id (no CourseEnrollment table)
 * Module: Enrollment(user_id, module_id)
 * Admin access is restricted at the controller path /admin/** by SecurityConfig.
 */
@Service
public class AdminEnrollmentService {
    private final JdbcTemplate jdbc;

    public AdminEnrollmentService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listUsers() {
        return jdbc.queryForList("""
            SELECT u.user_id AS userId,
                   CONCAT(u.first_name, ' ', u.last_name) AS fullName,
                   u.email AS email,
                   COALESCE(r.name, 'Unassigned') AS roleName,
                   COALESCE(u.status, 'unknown') AS status,
                   COALESCE(v.name, 'Not assigned') AS universityName,
                   DATE_FORMAT(u.created_at, '%d %b %Y') AS createdAt,
                   (SELECT COUNT(DISTINCT m.course_id)
                    FROM `Enrollment` e
                    INNER JOIN `Module` m ON m.module_id = e.module_id
                    WHERE e.user_id = u.user_id AND m.course_id IS NOT NULL) AS courseCount,
                   (SELECT COUNT(DISTINCT e.module_id)
                    FROM `Enrollment` e
                    WHERE e.user_id = u.user_id) AS moduleCount
            FROM `User` u
            LEFT JOIN `Role` r ON u.role_id = r.role_id
            LEFT JOIN `University` v ON u.university_id = v.university_id
            ORDER BY u.user_id DESC
            """);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> student(long userId) {
        Map<String, Object> user;
        try {
            user = jdbc.queryForMap("""
                SELECT u.user_id AS userId,
                       u.first_name AS firstName,
                       u.last_name AS lastName,
                       u.email AS email,
                       u.university_id AS universityId,
                       COALESCE(v.name, 'Not assigned') AS universityName,
                       COALESCE(u.status, 'unknown') AS status,
                       COALESCE(r.name, '') AS roleName
                FROM `User` u
                LEFT JOIN `Role` r ON u.role_id = r.role_id
                LEFT JOIN `University` v ON u.university_id = v.university_id
                WHERE u.user_id = ?
                """, userId);
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("Student account not found.");
        }
        if (!isStudentRole(String.valueOf(user.get("roleName")))) {
            throw new IllegalArgumentException("Only Student accounts can be enrolled in academic courses.");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> universities() {
        return jdbc.queryForList("SELECT university_id AS id, name FROM `University` ORDER BY name");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> availableCourses(long userId) {
        Map<String, Object> user = student(userId);
        if (user.get("universityId") == null) return List.of();
        return jdbc.queryForList("""
            SELECT c.course_id AS id, c.name AS name, c.code AS code,
                   (SELECT COUNT(*) FROM `Module` m WHERE m.course_id = c.course_id) AS moduleCount
            FROM `Course` c WHERE c.university_id = ?
            ORDER BY c.name, c.code
            """, user.get("universityId"));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> availableModules(long userId) {
        Map<String, Object> user = student(userId);
        if (user.get("universityId") == null) return List.of();
        return jdbc.queryForList("""
            SELECT m.module_id AS id, m.name AS name, m.code AS code,
                   c.name AS courseName, c.code AS courseCode, c.course_id AS courseId
            FROM `Module` m INNER JOIN `Course` c ON m.course_id = c.course_id
            WHERE c.university_id = ?
            ORDER BY c.name, m.name
            """, user.get("universityId"));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> enrolledCourses(long userId) {
        student(userId);
        return jdbc.queryForList("""
            SELECT c.course_id AS id, c.code AS code, c.name AS name,
                   DATE_FORMAT(MIN(e.enrollment_date), '%d %b %Y') AS enrolledAt,
                   COUNT(DISTINCT e.module_id) AS moduleCount,
                   (SELECT COUNT(*) FROM `Module` available
                    WHERE available.course_id = c.course_id) AS totalModules
            FROM `Enrollment` e
            INNER JOIN `Module` m ON m.module_id = e.module_id
            INNER JOIN `Course` c ON c.course_id = m.course_id
            WHERE e.user_id = ?
            GROUP BY c.course_id, c.code, c.name
            ORDER BY c.name
            """, userId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> enrolledModules(long userId) {
        student(userId);
        return jdbc.queryForList("""
            SELECT m.module_id AS id, m.code AS code, m.name AS name,
                   c.name AS courseName,
                   DATE_FORMAT(MIN(e.enrollment_date), '%d %b %Y') AS enrolledAt
            FROM `Enrollment` e
            INNER JOIN `Module` m ON e.module_id = m.module_id
            INNER JOIN `Course` c ON m.course_id = c.course_id
            WHERE e.user_id = ?
            GROUP BY m.module_id, m.code, m.name, c.name
            ORDER BY c.name, m.name
            """, userId);
    }

    @Transactional
    public void assignUniversity(long userId, long universityId) {
        student(userId);
        if (count("SELECT COUNT(*) FROM `University` WHERE university_id = ?", universityId) == 0) {
            throw new IllegalArgumentException("The selected university does not exist.");
        }
        long mismatchedModules = count("""
            SELECT COUNT(*) FROM `Enrollment` e
            LEFT JOIN `Module` m ON e.module_id = m.module_id
            LEFT JOIN `Course` c ON m.course_id = c.course_id
            WHERE e.user_id = ? AND (c.university_id IS NULL OR c.university_id <> ?)
            """, userId, universityId);
        if (mismatchedModules > 0) {
            throw new IllegalArgumentException("Remove the student's enrollments from other universities before changing their university.");
        }
        jdbc.update("UPDATE `User` SET university_id = ? WHERE user_id = ?", universityId, userId);
    }

    /**
     * Enroll in all modules currently offered by a course. No independent course
     * enrollment exists in the original schema, so courses without modules cannot
     * be recorded. Returns number of newly added module enrollments.
     */
    @Transactional
    public int enrollCourse(long userId, long courseId) {
        long universityId = requiredUniversity(student(userId));
        assertCourseAtUniversity(courseId, universityId);
        long moduleCount = count("SELECT COUNT(*) FROM `Module` WHERE course_id = ?", courseId);
        if (moduleCount == 0) {
            throw new IllegalArgumentException("This course has no modules yet. Add modules to the course before enrolling students.");
        }
        return jdbc.update("""
            INSERT INTO `Enrollment` (user_id, module_id)
            SELECT ?, m.module_id
            FROM `Module` m
            WHERE m.course_id = ? AND NOT EXISTS (
                SELECT 1 FROM `Enrollment` e
                WHERE e.user_id = ? AND e.module_id = m.module_id
            )
            """, userId, courseId, userId);
    }

    @Transactional
    public boolean enrollModule(long userId, long moduleId) {
        long universityId = requiredUniversity(student(userId));
        long matching = count("""
            SELECT COUNT(*) FROM `Module` m
            INNER JOIN `Course` c ON m.course_id = c.course_id
            WHERE m.module_id = ? AND c.university_id = ?
            """, moduleId, universityId);
        if (matching == 0) {
            throw new IllegalArgumentException("The selected module is not part of this student's university.");
        }
        // The parent course automatically appears when at least one module is enrolled.
        return jdbc.update("""
            INSERT INTO `Enrollment` (user_id, module_id)
            SELECT ?, ? WHERE NOT EXISTS (
                SELECT 1 FROM `Enrollment` WHERE user_id = ? AND module_id = ?
            )
            """, userId, moduleId, userId, moduleId) > 0;
    }

    @Transactional
    public boolean removeModule(long userId, long moduleId) {
        student(userId);
        // Study history remains untouched, only membership rows are deleted.
        return jdbc.update("DELETE FROM `Enrollment` WHERE user_id = ? AND module_id = ?", userId, moduleId) > 0;
    }

    /** Removes all of the student's modules within this course. */
    @Transactional
    public int removeCourse(long userId, long courseId) {
        student(userId);
        return jdbc.update("""
            DELETE e FROM `Enrollment` e
            INNER JOIN `Module` m ON e.module_id = m.module_id
            WHERE e.user_id = ? AND m.course_id = ?
            """, userId, courseId);
    }

    private boolean isStudentRole(String roleName) {
        String value = roleName == null ? "" : roleName.trim();
        return "Student".equalsIgnoreCase(value) || "ROLE_STUDENT".equalsIgnoreCase(value);
    }

    private long requiredUniversity(Map<String, Object> user) {
        if (user.get("universityId") == null) {
            throw new IllegalArgumentException("Assign the student to a university first.");
        }
        return ((Number) user.get("universityId")).longValue();
    }

    private void assertCourseAtUniversity(long courseId, long universityId) {
        if (count("SELECT COUNT(*) FROM `Course` WHERE course_id = ? AND university_id = ?", courseId, universityId) == 0) {
            throw new IllegalArgumentException("The selected course is not part of this student's university.");
        }
    }

    private long count(String query, Object... args) {
        Long value = jdbc.queryForObject(query, Long.class, args);
        return value == null ? 0 : value;
    }
}
