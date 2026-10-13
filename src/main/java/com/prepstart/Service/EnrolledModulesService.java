package com.prepstart.Service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Reads the existing PrepStart tables. Does not create fake course/module records. */
@Service
public class EnrolledModulesService {

    private static final Pattern LOCAL_UPLOAD = Pattern.compile(
            "^/uploadcontent/file/[a-f0-9]{32}\\.(?:pdf|ppt|pptx|doc|docx|txt|xls|xlsx)$");

    private final JdbcTemplate jdbc;

    public EnrolledModulesService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> studentByEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to view your modules");
        }
        List<Map<String, Object>> found = jdbc.query("""
                SELECT user_id, first_name, last_name, email
                FROM `User`
                WHERE email = ? AND LOWER(COALESCE(status,'active')) = 'active'
                """, (rs, n) -> {
            Map<String, Object> student = new HashMap<>();
            student.put("id", rs.getInt("user_id"));
            student.put("firstName", rs.getString("first_name"));
            student.put("lastName", rs.getString("last_name"));
            student.put("email", rs.getString("email"));

            // The shared Thymeleaf navbar uses student.initials.
            // Always provide it, even when the user's first/last name is blank.
            String firstName = rs.getString("first_name");
            String lastName = rs.getString("last_name");
            String firstInitial = firstName == null || firstName.isBlank()
                    ? "" : firstName.substring(0, 1).toUpperCase(Locale.ROOT);
            String lastInitial = lastName == null || lastName.isBlank()
                    ? "" : lastName.substring(0, 1).toUpperCase(Locale.ROOT);
            String initials = firstInitial + lastInitial;
            student.put("initials", initials.isBlank() ? "PS" : initials);
            return student;
        }, email);
        if (found.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "The signed-in email is not linked to an active PrepStart user");
        }
        return found.get(0);
    }

    /** Only the current user's Enrollment rows; duplicates do not duplicate cards. */
    public List<Map<String, Object>> modulesForUser(int userId) {
        return jdbc.query("""
                SELECT DISTINCT m.module_id, m.code, m.name, m.description,
                       m.academic_year, c.name AS course_name, c.code AS course_code,
                       (SELECT COUNT(*) FROM Content ct
                        WHERE ct.module_id = m.module_id
                          AND ct.file_url IS NOT NULL AND TRIM(ct.file_url) <> '') AS file_count
                FROM Enrollment e
                JOIN `Module` m ON m.module_id = e.module_id
                JOIN Course c ON c.course_id = m.course_id
                WHERE e.user_id = ?
                ORDER BY c.name, m.name, m.code
                """, (rs, n) -> moduleMap(rs), userId);
    }

    /** A module can only be opened if this very account is enrolled in it. */
    public Map<String, Object> moduleForUser(int moduleId, int userId) {
        List<Map<String, Object>> matches = jdbc.query("""
                SELECT m.module_id, m.code, m.name, m.description,
                       m.academic_year, c.name AS course_name, c.code AS course_code,
                       (SELECT COUNT(*) FROM Content ct
                        WHERE ct.module_id = m.module_id
                          AND ct.file_url IS NOT NULL AND TRIM(ct.file_url) <> '') AS file_count
                FROM `Module` m JOIN Course c ON c.course_id = m.course_id
                WHERE m.module_id = ? AND EXISTS (
                   SELECT 1 FROM Enrollment e
                   WHERE e.module_id = m.module_id AND e.user_id = ?)
                """, (rs, n) -> moduleMap(rs), moduleId, userId);
        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "This module is not part of your enrolments");
        }
        return matches.get(0);
    }

    public Map<String, Object> moduleByCodeForUser(String code, int userId) {
        List<Integer> ids = jdbc.query("""
                SELECT m.module_id FROM `Module` m
                WHERE m.code = ? AND EXISTS (
                    SELECT 1 FROM Enrollment e WHERE e.module_id=m.module_id AND e.user_id=?)
                ORDER BY m.module_id LIMIT 1
                """, (rs, n) -> rs.getInt(1), code, userId);
        if (ids.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "This module is not part of your enrolments");
        return moduleForUser(ids.get(0), userId);
    }

    private Map<String, Object> moduleMap(ResultSet rs) throws SQLException {
        Map<String, Object> m = new HashMap<>();
        m.put("id", rs.getInt("module_id"));
        m.put("code", rs.getString("code"));
        m.put("name", rs.getString("name"));
        m.put("description", rs.getString("description"));
        m.put("academicYear", rs.getString("academic_year"));
        m.put("courseName", rs.getString("course_name"));
        m.put("courseCode", rs.getString("course_code"));
        m.put("fileCount", rs.getInt("file_count"));
        return m;
    }

    public record MaterialGroups(List<Map<String, Object>> pdfs,
                                 List<Map<String, Object>> presentations,
                                 List<Map<String, Object>> documents,
                                 List<Map<String, Object>> flashcards) { }

    /** Classify by actual extension first. A PPTX is never put under Documents. */
    public MaterialGroups materialsForModule(int moduleId) {
        List<Map<String, Object>> pdfs = new ArrayList<>();
        List<Map<String, Object>> presentations = new ArrayList<>();
        List<Map<String, Object>> documents = new ArrayList<>();
        List<Map<String, Object>> flashcards = new ArrayList<>();

        List<Map<String, Object>> files = jdbc.query("""
                SELECT ct.content_id, ct.title, ct.description, ct.content_type,
                       ct.file_type, ct.file_url, ct.created_at,
                       COALESCE(NULLIF(TRIM(CONCAT_WS(' ', u.first_name, u.last_name)), ''),
                                'PrepStart') AS author
                FROM Content ct
                LEFT JOIN `User` u ON u.user_id = COALESCE(ct.uploaded_by, ct.user_id)
                WHERE ct.module_id = ?
                ORDER BY ct.created_at DESC, ct.content_id DESC
                """, (rs, n) -> {
            String ext = normalizedExtension(rs.getString("file_type"), rs.getString("file_url"));
            String rawUrl = rs.getString("file_url");
            Map<String, Object> m = new HashMap<>();
            m.put("id", rs.getInt("content_id"));
            m.put("title", rs.getString("title"));
            m.put("description", rs.getString("description"));
            m.put("author", rs.getString("author"));
            m.put("kind", ext.isBlank() ? "Resource" : ext.toUpperCase(Locale.ROOT));
            m.put("available", safeDestination(rawUrl) != null);
            m.put("extension", ext);
            m.put("contentType", rs.getString("content_type"));
            return m;
        }, moduleId);

        for (Map<String, Object> file : files) {
            String ext = (String) file.get("extension");
            String type = String.valueOf(file.get("contentType")).toUpperCase(Locale.ROOT);
            if ("pdf".equals(ext)) {
                pdfs.add(file);
            } else if ("ppt".equals(ext) || "pptx".equals(ext)
                    || (ext.isBlank() && (type.equals("SLIDES") || type.equals("PRESENTATION")))) {
                presentations.add(file);
            } else {
                documents.add(file);
            }
        }

        // Generated slides have no file_url in the original Slide table.
        // Display their titles but do not generate broken download links.
        List<Map<String, Object>> generated = jdbc.query("""
                SELECT sl.slide_id, sl.title, sl.description,
                       COALESCE(NULLIF(TRIM(CONCAT_WS(' ', u.first_name, u.last_name)), ''),
                                'PrepStart') AS author
                FROM Slide sl
                JOIN ProcessedContent pc ON pc.ai_content_id = sl.ai_content_id
                LEFT JOIN Content ct ON ct.content_id = pc.content_id
                LEFT JOIN `User` u ON u.user_id = sl.user_id
                WHERE COALESCE(pc.module_id, ct.module_id) = ?
                ORDER BY sl.slide_id DESC
                """, (rs, n) -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", rs.getInt("slide_id"));
            m.put("title", rs.getString("title") == null ? "Generated slides" : rs.getString("title"));
            m.put("description", rs.getString("description"));
            m.put("author", rs.getString("author"));
            m.put("kind", "GENERATED");
            m.put("available", false);
            return m;
        }, moduleId);
        presentations.addAll(generated);

        flashcards.addAll(jdbc.query("""
                SELECT f.flashcard_id, f.question, f.answer
                FROM Flashcards f
                JOIN ProcessedContent pc ON pc.ai_content_id=f.ai_content_id
                LEFT JOIN Content ct ON ct.content_id=pc.content_id
                WHERE COALESCE(pc.module_id,ct.module_id)=?
                ORDER BY f.flashcard_id DESC
                """, (rs, n) -> {
            Map<String, Object> f = new HashMap<>();
            f.put("id", rs.getInt("flashcard_id"));
            f.put("question", rs.getString("question"));
            f.put("answer", rs.getString("answer"));
            return f;
        }, moduleId));

        return new MaterialGroups(pdfs, presentations, documents, flashcards);
    }

    private static String normalizedExtension(String fileType, String url) {
        String type = fileType == null ? "" : fileType.strip().toLowerCase(Locale.ROOT);
        if (type.startsWith(".")) type = type.substring(1);
        if (type.matches("pdf|ppt|pptx|doc|docx|txt|xls|xlsx")) return type;
        if (url != null) {
            String lower = url.toLowerCase(Locale.ROOT).split("[?#]", 2)[0];
            int dot = lower.lastIndexOf('.');
            if (dot >= 0) {
                String ext = lower.substring(dot + 1);
                if (ext.matches("pdf|ppt|pptx|doc|docx|txt|xls|xlsx")) return ext;
            }
        }
        return "";
    }

    /** Only URLs used by the existing private file endpoint, or valid HTTPS URLs. */
    private static String safeDestination(String url) {
        if (url == null || url.isBlank() || url.contains("\\")
                || url.contains("\r") || url.contains("\n")) return null;
        if (LOCAL_UPLOAD.matcher(url).matches()) return url;
        try {
            URI uri = URI.create(url);
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getRawUserInfo() == null) return url;
        } catch (IllegalArgumentException ignored) { }
        return null;
    }

    /** Re-check enrolment for every file-open request, not only for the module page. */
    public String approvedContentUrl(int contentId, int userId) {
        List<String> urls = jdbc.query("""
                SELECT ct.file_url FROM Content ct
                WHERE ct.content_id = ? AND EXISTS (
                    SELECT 1 FROM Enrollment e
                    WHERE e.user_id = ? AND e.module_id = ct.module_id)
                """, (rs, n) -> rs.getString("file_url"), contentId, userId);
        if (urls.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "This file is not available in your enrolled modules");
        String url = safeDestination(urls.get(0));
        if (url == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "This material has no supported file link");
        jdbc.update("""
                INSERT INTO ContentInteraction (user_id, content_id, interaction_type, `value`)
                VALUES (?, ?, 'view', 1)
                """, userId, contentId);
        return url;
    }
}
