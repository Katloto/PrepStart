package com.prepstart.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/** Uploads are stored outside /static and served only after authorization. */
@Controller
public class UploadContentController {

    private static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    private static final Set<String> EXTENSIONS = Set.of("pdf", "ppt", "pptx", "doc", "docx", "txt", "xls", "xlsx");
    private static final Set<String> TYPES = Set.of("LECTURE_NOTES", "SLIDES", "PAST_EXAM", "ASSIGNMENT", "TUTORIAL", "OTHER");
    private static final Pattern STORED_NAME = Pattern.compile("[a-f0-9]{32}\\.(pdf|ppt|pptx|doc|docx|txt|xls|xlsx)");
    private static final String URL_PREFIX = "/uploadcontent/file/";

    private final JdbcTemplate jdbc;
    private final Path storageDirectory;

    public UploadContentController(JdbcTemplate jdbc,
                                   @Value("${prepstart.upload.directory:uploads/materials}") String directory) {
        this.jdbc = jdbc;
        this.storageDirectory = Path.of(directory).toAbsolutePath().normalize();
    }

    private record Account(int id, boolean admin, String email) { }
    public record CourseOption(int id, String label) { }
    public record ModuleOption(int id, int courseId, String label, String academicYear) { }
    public record UploadedItem(int id, String title, String type, String moduleCode,
                               Integer year, String date, String fileUrl) { }
    private record StoredItem(int id, int moduleId, Integer uploadedBy,
                              String title, String fileType, String fileUrl) { }

    private String authenticatedEmail(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        }
        if (authentication.getPrincipal() instanceof OAuth2User oauth) {
            for (String claim : List.of("email", "preferred_username")) {
                String email = oauth.getAttribute(claim);
                if (email != null && !email.isBlank()) return email;
            }
        }
        return authentication.getName();
    }

    private Account account(Authentication authentication) {
        String email = authenticatedEmail(authentication);
        List<Integer> ids = jdbc.query(
                "SELECT user_id FROM `User` WHERE email = ?",
                (rs, n) -> rs.getInt(1), email);
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Your login does not have a matching PrepStart user record");
        }
        boolean admin = authentication.getAuthorities().stream().anyMatch(a ->
                "ROLE_ADMIN".equals(a.getAuthority()));
        return new Account(ids.get(0), admin, email);
    }

    private boolean canUseModule(Account account, int moduleId) {
        Integer count = jdbc.queryForObject(
                account.admin()
                    ? "SELECT COUNT(*) FROM `Module` WHERE module_id = ?"
                    : "SELECT COUNT(*) FROM Enrollment WHERE module_id = ? AND user_id = ?",
                Integer.class,
                account.admin() ? new Object[]{moduleId} : new Object[]{moduleId, account.id()});
        return count != null && count > 0;
    }

    @GetMapping({"/uploadcontent", "/upload-content"})
    public String uploadPage(Authentication auth, Model model,
                             @RequestParam(value = "uploaded", required = false) String uploaded,
                             @RequestParam(value = "tooLarge", required = false) String tooLarge) {
        Account account = account(auth);
        List<CourseOption> courses = jdbc.query(
                account.admin()
                    ? "SELECT course_id, CONCAT(code, ' · ', name) label FROM Course ORDER BY name"
                    : """
                      SELECT DISTINCT c.course_id, CONCAT(c.code, ' · ', c.name) label
                      FROM Course c JOIN `Module` m ON m.course_id = c.course_id
                      JOIN Enrollment e ON e.module_id = m.module_id
                      WHERE e.user_id = ? ORDER BY label
                      """,
                (rs, row) -> new CourseOption(rs.getInt("course_id"), rs.getString("label")),
                account.admin() ? new Object[]{} : new Object[]{account.id()});

        List<ModuleOption> modules = jdbc.query(
                account.admin()
                    ? "SELECT module_id, course_id, CONCAT(code, ' · ', name) label, academic_year FROM `Module` ORDER BY name"
                    : """
                      SELECT DISTINCT m.module_id, m.course_id,
                          CONCAT(m.code, ' · ', m.name) label, m.academic_year
                      FROM `Module` m JOIN Enrollment e ON e.module_id = m.module_id
                      WHERE e.user_id = ? ORDER BY label
                      """,
                (rs, row) -> new ModuleOption(rs.getInt("module_id"), rs.getInt("course_id"),
                        rs.getString("label"), rs.getString("academic_year")),
                account.admin() ? new Object[]{} : new Object[]{account.id()});

        List<UploadedItem> recent = jdbc.query("""
                SELECT c.content_id, c.title, c.content_type, c.file_type,
                       c.`year`, c.created_at, c.file_url,
                       COALESCE(m.code, 'Module') module_code
                FROM Content c LEFT JOIN `Module` m ON m.module_id = c.module_id
                WHERE c.uploaded_by = ? AND c.file_url LIKE '/uploadcontent/file/%'
                ORDER BY c.created_at DESC, c.content_id DESC LIMIT 6
                """, (rs, row) -> new UploadedItem(
                rs.getInt("content_id"), rs.getString("title"), rs.getString("content_type"),
                rs.getString("module_code"), (Integer) rs.getObject("year"),
                rs.getTimestamp("created_at") == null ? "" :
                        rs.getTimestamp("created_at").toLocalDateTime().toLocalDate().toString(),
                rs.getString("file_url")), account.id());

        model.addAttribute("courses", courses);
        model.addAttribute("modules", modules);
        model.addAttribute("recentUploads", recent);
        model.addAttribute("noModules", modules.isEmpty());
        model.addAttribute("currentYear", LocalDate.now().getYear());
        model.addAttribute("uploaded", "1".equals(uploaded));
        if ("1".equals(tooLarge)) model.addAttribute("errorMessage", "Maximum upload size is 20 MB.");
        model.addAttribute("uploaderInitials", initials(account.email()));
        return "uploadcontent";
    }

    private String initials(String email) {
        String user = email.split("@", 2)[0];
        return user.isEmpty() ? "PS" : user.substring(0, Math.min(2, user.length())).toUpperCase(Locale.ROOT);
    }

    @GetMapping("/uploadcontent/topics")
    @ResponseBody
    public List<String> topics(Authentication auth, @RequestParam int moduleId) {
        Account account = account(auth);
        if (!canUseModule(account, moduleId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Module access denied");
        }
        return jdbc.query("SELECT name FROM Topic WHERE module_id = ? ORDER BY name",
                (rs, row) -> rs.getString("name"), moduleId);
    }

    @PostMapping(value = "/uploadcontent", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String upload(Authentication auth,
                         @RequestParam int courseId,
                         @RequestParam int moduleId,
                         @RequestParam String contentType,
                         @RequestParam String title,
                         @RequestParam int academicYear,
                         @RequestParam(value = "topicOrChapter", required = false) String topicOrChapter,
                         @RequestParam(value = "description", required = false) String description,
                         @RequestParam("file") MultipartFile file,
                         RedirectAttributes flash) {
        Account account = account(auth);
        String cleanTitle = title == null ? "" : title.strip();
        String topic = topicOrChapter == null ? "" : topicOrChapter.strip();
        String details = description == null ? "" : description.strip();
        if (cleanTitle.isEmpty() || cleanTitle.length() > 150
                || topic.length() > 150 || details.length() > 1800) {
            return error(flash, "Please check the title and description lengths.");
        }
        if (!TYPES.contains(contentType)) return error(flash, "Choose a valid material type.");
        int current = LocalDate.now().getYear();
        if (academicYear < 2000 || academicYear > current + 1) {
            return error(flash, "Choose a valid academic year.");
        }
        Integer allowed = jdbc.queryForObject(
                account.admin()
                    ? "SELECT COUNT(*) FROM `Module` WHERE module_id = ? AND course_id = ?"
                    : """
                      SELECT COUNT(*) FROM `Module` m
                      JOIN Enrollment e ON e.module_id = m.module_id
                      WHERE m.module_id = ? AND m.course_id = ? AND e.user_id = ?
                      """,
                Integer.class,
                account.admin() ? new Object[]{moduleId, courseId}
                                : new Object[]{moduleId, courseId, account.id()});
        if (allowed == null || allowed == 0) return error(flash, "Select a module you're allowed to upload into.");
        if (file == null || file.isEmpty()) return error(flash, "Please select a file to upload.");
        if (file.getSize() > MAX_FILE_BYTES) return error(flash, "The maximum file size is 20 MB.");

        String originalName = Optional.ofNullable(file.getOriginalFilename()).orElse("");
        String extension = extensionOf(originalName);
        if (!EXTENSIONS.contains(extension)) {
            return error(flash, "Supported files: PDF, Word, PowerPoint, Excel and TXT.");
        }

        Path destination = null;
        try {
            if (!looksLikeAllowedFile(file, extension)) {
                return error(flash, "The selected file does not match its file extension.");
            }
            Files.createDirectories(storageDirectory);
            String storedName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
            destination = storageDirectory.resolve(storedName).normalize();
            if (!destination.startsWith(storageDirectory)) {
                return error(flash, "Invalid upload destination.");
            }
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, destination);
            }

            String text = (topic.isBlank() ? "" : "Topic/Chapter: " + topic + "\n") + details;
            String fileUrl = URL_PREFIX + storedName;
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO Content
                          (module_id, user_id, title, description, content_type,
                           uploaded_by, file_url, file_type, `year`)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """);
                ps.setInt(1, moduleId);
                ps.setInt(2, account.id());
                ps.setString(3, cleanTitle);
                ps.setString(4, text);
                ps.setString(5, contentType);
                ps.setInt(6, account.id());
                ps.setString(7, fileUrl);
                ps.setString(8, extension);
                ps.setInt(9, academicYear);
                return ps;
            });
            return "redirect:/uploadcontent?uploaded=1";
        } catch (IOException | DataAccessException ex) {
            if (destination != null) {
                try { Files.deleteIfExists(destination); } catch (IOException ignored) { }
            }
            return error(flash, "Upload couldn't be saved. Check the server log or storage permissions.");
        }
    }

    private static String extensionOf(String name) {
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        int dot = base.lastIndexOf('.');
        return dot < 0 ? "" : base.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean looksLikeAllowedFile(MultipartFile file, String ext) throws IOException {
        byte[] bytes;
        try (InputStream in = file.getInputStream()) { bytes = in.readNBytes(8); }
        if (bytes.length < 4) return false;
        return switch (ext) {
            case "pdf" -> bytes.length >= 5 && new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-");
            case "docx", "pptx", "xlsx" -> bytes[0] == 'P' && bytes[1] == 'K'
                    && (bytes[2] == 3 || bytes[2] == 5 || bytes[2] == 7);
            case "doc", "ppt", "xls" -> (bytes[0] & 0xff) == 0xD0 && (bytes[1] & 0xff) == 0xCF
                    && (bytes[2] & 0xff) == 0x11 && (bytes[3] & 0xff) == 0xE0;
            case "txt" -> {
                boolean binary = false;
                for (byte b : bytes) if (b == 0) { binary = true; break; }
                yield !binary;
            }
            default -> false;
        };
    }

    private String error(RedirectAttributes flash, String message) {
        flash.addFlashAttribute("errorMessage", message);
        return "redirect:/uploadcontent";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String oversized() {
        return "redirect:/uploadcontent?tooLarge=1";
    }

    @GetMapping("/uploadcontent/file/{storedName:.+}")
    public ResponseEntity<Resource> download(Authentication auth,
                                              @PathVariable String storedName) throws IOException {
        Account account = account(auth);
        if (!STORED_NAME.matcher(storedName).matches()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        String url = URL_PREFIX + storedName;
        List<StoredItem> files = jdbc.query("""
                SELECT content_id, module_id, uploaded_by, title, file_type, file_url
                FROM Content WHERE file_url = ?
                """, (rs, row) -> new StoredItem(
                rs.getInt("content_id"), rs.getInt("module_id"),
                (Integer) rs.getObject("uploaded_by"), rs.getString("title"),
                rs.getString("file_type"), rs.getString("file_url")), url);
        if (files.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        StoredItem content = files.get(0);
        boolean owner = content.uploadedBy() != null && account.id() == content.uploadedBy();
        if (!account.admin() && !owner && !canUseModule(account, content.moduleId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot access this material");
        }
        Path path = storageDirectory.resolve(storedName).normalize();
        if (!path.startsWith(storageDirectory) || !Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found on disk");
        }
        String safeTitle = content.title().replaceAll("[\\r\\n\\\\/<>:\"|?*]", "_");
        String ext = extensionOf(storedName);
        String name = (safeTitle.length() > 100 ? safeTitle.substring(0, 100) : safeTitle) + "." + ext;
        Resource resource = new PathResource(path);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(Files.size(path))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }
}
