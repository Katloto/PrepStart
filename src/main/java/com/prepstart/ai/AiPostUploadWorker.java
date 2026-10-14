package com.prepstart.ai;

import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/** Separate after-upload pipeline: no change to UploadContentController or its HTML required. */
@Component
public class AiPostUploadWorker {
    private static final int MAX_TEXT = 180_000; // Stop, rather than silently omit a document's tail.
    private static final int CHUNK_SIZE = 9_000;
    private static final int MAX_CHUNKS = 25;
    private static final Pattern SAFE_STORED = Pattern.compile("[0-9a-f]{32}\\.(pdf|ppt|pptx|doc|docx|txt|xls|xlsx)");
    private static final Set<String> INPUT_EXT = Set.of("pdf", "ppt", "pptx", "doc", "docx", "txt", "xls", "xlsx");
    private final JdbcTemplate jdbc;
    private final GeminiStudyClient gemini;
    private final StudyArtifactWriter writer;
    private final TransactionTemplate tx;
    private final Path uploads;
    private final boolean enabled;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public AiPostUploadWorker(JdbcTemplate jdbc, GeminiStudyClient gemini, StudyArtifactWriter writer,
                              @Qualifier("aiStudyTransactionTemplate") TransactionTemplate tx,
                              @Value("${prepstart.upload.directory:uploads/materials}") String uploadPath,
                              @Value("${prepstart.ai.enabled:false}") boolean enabled) {
        this.jdbc = jdbc;
        this.gemini = gemini;
        this.writer = writer;
        this.tx = tx;
        this.uploads = Path.of(uploadPath).toAbsolutePath().normalize();
        this.enabled = enabled;
    }

    private record Source(int id, int moduleId, Integer uploader, String title, String fileUrl, String extension, Integer year) {}

    @Scheduled(initialDelayString = "${prepstart.ai.initial-delay-ms:15000}",
               fixedDelayString = "${prepstart.ai.poll-ms:30000}")
    public void pollUploads() {
        if (!enabled || !running.compareAndSet(false, true)) return;
        try {
            // Recover interrupted processing only after a lengthy restart/stall; one instance recommended.
            jdbc.update("UPDATE AiProcessingJob SET status='PENDING', updated_at=NOW() " +
                    "WHERE status='PROCESSING' AND updated_at < DATE_SUB(NOW(), INTERVAL 30 MINUTE)");
            if (gemini.isConfigured()) {
                jdbc.update("UPDATE AiProcessingJob SET status='PENDING' WHERE status='WAITING_FOR_KEY'");
            }
            jdbc.update("UPDATE AiProcessingJob SET status='PENDING', retry_at=NULL WHERE status='RETRY' AND retry_at <= NOW()");
            jdbc.update("""
                    INSERT IGNORE INTO AiProcessingJob (content_id, status)
                    SELECT c.content_id, 'PENDING' FROM Content c
                    WHERE c.file_url LIKE '/uploadcontent/file/%'
                      AND c.file_type IN ('pdf','ppt','pptx','doc','docx','txt','xls','xlsx')
                      AND (c.content_type IS NULL OR c.content_type <> 'AI_GENERATED')
                      AND NOT EXISTS (SELECT 1 FROM AiProcessingJob j WHERE j.content_id=c.content_id)
                    ORDER BY c.content_id ASC LIMIT 100
                    """);
            List<Integer> ids = jdbc.query("SELECT content_id FROM AiProcessingJob WHERE status='PENDING' ORDER BY job_id LIMIT 2",
                    (rs, n) -> rs.getInt(1));
            for (int contentId : ids) {
                int claimed = jdbc.update("UPDATE AiProcessingJob SET status='PROCESSING', started_at=NOW(), " +
                        "updated_at=NOW(), last_error=NULL WHERE content_id=? AND status='PENDING'", contentId);
                if (claimed == 0) continue;
                if (!gemini.isConfigured()) {
                    jdbc.update("UPDATE AiProcessingJob SET status='WAITING_FOR_KEY', " +
                            "last_error='Set GEMINI_API_KEY and restart the application', updated_at=NOW() WHERE content_id=?", contentId);
                    continue;
                }
                try { process(contentId); }
                catch (GeminiStudyClient.AiException ex) { markFailure(contentId, ex.getMessage(), ex.retryable); }
                catch (Exception ex) { markFailure(contentId, "Processing failed: " + ex.getClass().getSimpleName() +
                        " (see server logs)", false); ex.printStackTrace(); }
            }
        } catch (Exception ex) {
            System.err.println("AI post-upload queue error: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        } finally { running.set(false); }
    }

    private void markFailure(int id, String message, boolean retryable) {
        String code = retryable ? "RETRY" : "FAILED";
        String compact = message == null ? "Unknown error" : message.substring(0, Math.min(450, message.length()));
        jdbc.update("UPDATE AiProcessingJob SET status=?, last_error=?, updated_at=NOW(), " +
                        "retry_at=CASE WHEN ?='RETRY' THEN DATE_ADD(NOW(), INTERVAL 30 MINUTE) ELSE NULL END " +
                        "WHERE content_id=?", code, compact, code, id);
    }

    private void process(int contentId) throws Exception {
        List<Source> rows = jdbc.query("""
                SELECT content_id, module_id, COALESCE(uploaded_by,user_id) AS author_id,
                       title, file_url, file_type, `year`
                  FROM Content WHERE content_id=?
                """, (rs, n) -> new Source(rs.getInt("content_id"), rs.getInt("module_id"),
                (Integer) rs.getObject("author_id"), rs.getString("title"), rs.getString("file_url"),
                rs.getString("file_type"), (Integer) rs.getObject("year")), contentId);
        if (rows.isEmpty()) throw new IllegalStateException("Original upload is missing");
        Source source = rows.getFirst();
        if (source.fileUrl() == null || !source.fileUrl().startsWith("/uploadcontent/file/")) {
            throw new IllegalStateException("Not an uploaded local file");
        }
        String storedName = source.fileUrl().substring("/uploadcontent/file/".length());
        if (!SAFE_STORED.matcher(storedName).matches() || !INPUT_EXT.contains(source.extension())) {
            throw new IllegalStateException("Unsafe stored filename or unsupported extension");
        }
        Path original = uploads.resolve(storedName).normalize();
        if (!original.startsWith(uploads) || !Files.isRegularFile(original, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Original file not present in uploads/materials");
        }
        if (Files.size(original) > 20L * 1024 * 1024) throw new IllegalStateException("File exceeds 20 MB");

        // Tika extracts all text it supports. Embedded images, scanned documents and handwriting need OCR.
        // Request one character beyond the cap to DETECT oversize input instead of silently truncating it.
        String text;
        try {
            Tika tika = new Tika();
            tika.setMaxStringLength(MAX_TEXT + 1);
            text = tika.parseToString(original.toFile());
        } catch (Exception ex) {
            throw new IllegalStateException("Text extraction failed (encrypted, malformed or unsupported document)", ex);
        }
        text = text == null ? "" : text.replace("\u0000", "").strip();
        if (text.length() < 80) {
            throw new IllegalStateException("No extractable text found. Scanned/image-only documents require OCR (not included).");
        }
        if (text.length() > MAX_TEXT) {
            throw new IllegalStateException("Document exceeds 180,000 extracted characters; no content was silently skipped.");
        }
        List<String> parts = chunk(text);
        if (parts.size() > MAX_CHUNKS) throw new IllegalStateException("Document needs too many AI chunks for the configured free-tier budget");
        int wordCount = text.trim().split("\\s+").length;
        jdbc.update("UPDATE AiProcessingJob SET words_scanned=?, sections_total=?, updated_at=NOW() WHERE content_id=?",
                wordCount, parts.size(), contentId);
        List<GeminiStudyClient.Section> sections = new ArrayList<>();
        for (int n = 0; n < parts.size(); n++) {
            sections.add(gemini.analyse(parts.get(n), n + 1, parts.size()));
            jdbc.update("UPDATE AiProcessingJob SET sections_done=?, updated_at=NOW() WHERE content_id=?", n + 1, contentId);
            // Keep request bursts gentle. Real Gemini project-specific quotas still apply.
            if (n + 1 < parts.size()) Thread.sleep(5500);
        }
        List<StudyArtifactWriter.Artifact> created = List.of();
        try {
            created = writer.write(source.title(), sections, uploads);
            List<StudyArtifactWriter.Artifact> files = created;
            int questions = sections.stream().mapToInt(s -> s.questions().size()).sum();
            int cards = sections.stream().mapToInt(s -> s.cards().size()).sum();
            tx.executeWithoutResult(status -> {
                long processedId = insertKey("INSERT INTO ProcessedContent(content_id,module_id,type) VALUES (?,?,?)",
                        source.id(), source.moduleId(), "AI_STUDY");
                for (var artifact : files) {
                    String filename = artifact.file().getFileName().toString();
                    jdbc.update("""
                            INSERT INTO Content(module_id,user_id,title,description,content_type,
                                                uploaded_by,file_url,file_type,`year`)
                            VALUES(?,?,?,?,?,?,?,?,?)
                            """, source.moduleId(), source.uploader(), artifact.title(),
                            "AI-generated study material from Content #" + source.id() + ". Check against the original.",
                            "AI_GENERATED", source.uploader(), "/uploadcontent/file/" + filename,
                            artifact.extension(), source.year() == null ? LocalDate.now().getYear() : source.year());
                }
                for (var section : sections) for (var card : section.cards()) {
                    long cardId = insertKey("INSERT INTO Flashcards(ai_content_id,question,answer) VALUES (?,?,?)",
                            processedId, card.question(), card.answer());
                    jdbc.update("INSERT INTO AiFlashcardSource(flashcard_id,source_content_id,source_excerpt) VALUES (?,?,?)",
                            cardId, source.id(), card.quote());
                }
                // Create quizzes in the exact tables and single-answer format QuizMeService expects.
                for (String difficulty : List.of("Easy", "Medium", "Hard")) {
                    List<GeminiStudyClient.Question> subset = sections.stream()
                            .flatMap(s -> s.questions().stream())
                            .filter(q -> difficulty.equals(q.difficulty()))
                            .toList();
                    if (subset.isEmpty()) continue;
                    long quizId = insertKey("INSERT INTO Quiz(ai_content_id,topic_id,difficulty_level) VALUES (?,NULL,?)",
                            processedId, difficulty);
                    for (var question : subset) {
                        long questionId = insertKey("INSERT INTO Question(quiz_id,question_text) VALUES (?,?)",
                                quizId, question.question());
                        jdbc.update("INSERT INTO AiQuestionSource(question_id,source_content_id,source_excerpt) VALUES (?,?,?)",
                                questionId, source.id(), question.quote());
                        for (int i = 0; i < 4; i++) {
                            jdbc.update("INSERT INTO QuestionOption(question_id,option_text,is_correct) VALUES(?,?,?)",
                                    questionId, question.options().get(i), i == question.correctIndex());
                        }
                    }
                }
                jdbc.update("""
                        UPDATE AiProcessingJob SET status='COMPLETED', generated_files=?, generated_questions=?,
                           generated_flashcards=?, completed_at=NOW(), updated_at=NOW(), retry_at=NULL,
                           last_error=NULL WHERE content_id=?
                        """, files.size(), questions, cards, source.id());
            });
        } catch (Exception ex) {
            // If DB transaction failed, remove files that never became usable Content rows.
            for (var artifact : created) {
                try { Files.deleteIfExists(artifact.file()); } catch (IOException ignored) { }
            }
            throw ex;
        }
    }

    private long insertKey(String sql, Object... args) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(c -> {
            PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                Object v = args[i];
                if (v == null) ps.setNull(i + 1, Types.NULL);
                else ps.setObject(i + 1, v);
            }
            return ps;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new IllegalStateException("Insert returned no generated key");
        return key.longValue();
    }

    private List<String> chunk(String text) {
        List<String> result = new ArrayList<>();
        int pos = 0;
        while (pos < text.length()) {
            int end = Math.min(text.length(), pos + CHUNK_SIZE);
            if (end < text.length()) {
                int breakAt = text.lastIndexOf("\n\n", end);
                if (breakAt > pos + CHUNK_SIZE / 2) end = breakAt + 2;
            }
            String content = text.substring(pos, end);
            if (!content.isBlank()) result.add(content);
            pos = end;
        }
        return result;
    }
}
