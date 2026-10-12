package com.prepstart.Service;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Uses the EXISTING prepstart.sql tables (no new models or schema changes):
 * User, Enrollment, Module, Topic, Progress, QuizAttempt, UserResponse, Quiz.
 *
 * QuizMeService stores QuizAttempt.score as raw number correct, NOT percent;
 * the denominator is COUNT(DISTINCT UserResponse.question_id) per attempt.
 */
@Service
public class ProgressDashboardService {
    private static final DateTimeFormatter HISTORY_DATE =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter SMALL_DATE =
            DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_LABEL =
            DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);

    private final JdbcTemplate jdbc;

    public ProgressDashboardService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Null chart score means no trustworthy value: never fabricate 0%. */
    public record ChartData(String rangeLabel, List<String> labels,
                            List<Double> scores, List<Integer> counts, int recordedAttempts) { }

    private record Student(int id, String firstName, String lastName, String email) { }
    private record ModuleStat(int id, String code, String name,
                              int topicCount, int completedTopics, double sumPercent) {
        Integer percent() {
            return topicCount == 0 ? null : (int) Math.round(sumPercent / topicCount);
        }
        boolean completed() { return topicCount > 0 && completedTopics == topicCount; }
    }
    private record Attempt(long id, LocalDateTime completedAt, Integer score,
                           int questionCount, String moduleCode, String difficulty) {
        Double percent() {
            if (score == null || questionCount <= 0 || score < 0 || score > questionCount) return null;
            return (score * 100.0) / questionCount;
        }
    }

    public Map<String, Object> dashboard(String email) {
        Student student = student(email);
        List<ModuleStat> moduleStats = modules(student.id());
        List<Attempt> attempts = attempts(student.id());

        int allTopics = moduleStats.stream().mapToInt(ModuleStat::topicCount).sum();
        double totalPercent = moduleStats.stream().mapToDouble(ModuleStat::sumPercent).sum();
        int completedModules = (int) moduleStats.stream().filter(ModuleStat::completed).count();
        int withTopics = (int) moduleStats.stream().filter(m -> m.topicCount() > 0).count();
        List<Attempt> scored = attempts.stream().filter(a -> a.percent() != null).toList();
        Double avg = scored.isEmpty() ? null :
                scored.stream().mapToDouble(a -> a.percent()).average().orElse(0.0);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("overallProgress", allTopics == 0 ? "—" : Math.round(totalPercent / allTopics) + "%");
        summary.put("modulesCompleted", allTopics == 0 ? "— / " + moduleStats.size() :
                completedModules + " / " + moduleStats.size());
        summary.put("quizCount", attempts.size());
        summary.put("averageScore", avg == null ? "—" : Math.round(avg) + "%");
        summary.put("topicCount", allTopics);
        summary.put("configuredModules", withTopics);
        summary.put("scoredQuizCount", scored.size());
        summary.put("unscoredQuizCount", attempts.size() - scored.size());

        List<Map<String, Object>> moduleItems = new ArrayList<>();
        for (ModuleStat m : moduleStats) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", m.id());
            item.put("code", m.code());
            item.put("name", m.name());
            item.put("percent", m.percent());
            item.put("topicCount", m.topicCount());
            item.put("completedTopics", m.completedTopics());
            moduleItems.add(item);
        }

        List<Map<String, Object>> history = new ArrayList<>();
        for (Attempt attempt : attempts.stream().limit(8).toList()) {
            Map<String, Object> row = new LinkedHashMap<>();
            Double pct = attempt.percent();
            row.put("date", attempt.completedAt().format(HISTORY_DATE));
            row.put("module", attempt.moduleCode() == null ? "General quiz" : attempt.moduleCode());
            row.put("difficulty", attempt.difficulty() == null ? "" : attempt.difficulty());
            row.put("result", pct == null ? "Score unavailable" :
                    attempt.score() + " / " + attempt.questionCount());
            row.put("percentLabel", pct == null ? "—" : Math.round(pct) + "%");
            row.put("scored", pct != null);
            history.add(row);
        }

        String initials = (student.firstName().isBlank() ? "" : student.firstName().substring(0, 1)) +
                (student.lastName().isBlank() ? "" : student.lastName().substring(0, 1));
        Map<String, Object> user = Map.of("firstName", student.firstName(),
                "email", student.email(), "initials", initials.isBlank() ? "PS" : initials.toUpperCase(Locale.ROOT));

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("student", user);
        output.put("summary", summary);
        output.put("modules", moduleItems);
        output.put("recentAttempts", history);
        return output;
    }

    public ChartData chart(String email, String requestedRange) {
        int id = student(email).id();
        String range = requestedRange.toLowerCase(Locale.ROOT);
        if (!range.equals("week") && !range.equals("month") && !range.equals("semester")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown time range");
        }

        LocalDate today = LocalDate.now(); // server-local date; DB timestamps use the same configured zone
        LocalDate start;
        String label;
        if (range.equals("week")) {
            start = today.minusDays(6);
            label = "Last 7 days";
        } else if (range.equals("month")) {
            start = today.minusDays(29);
            label = "Last 30 days";
        } else {
            // Explicit calendar semesters, not an invented university-specific semester schedule.
            start = LocalDate.of(today.getYear(), today.getMonthValue() <= 6 ? 1 : 7, 1);
            label = "Current calendar semester (Jan–Jun / Jul–Dec)";
        }

        record Bucket(LocalDate from, LocalDate through, String title) { }
        List<Bucket> buckets = new ArrayList<>();
        if (range.equals("week")) {
            for (int i = 0; i < 7; i++) {
                LocalDate day = start.plusDays(i);
                buckets.add(new Bucket(day, day, day.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH))));
            }
        } else if (range.equals("month")) {
            for (LocalDate cursor = start; !cursor.isAfter(today); cursor = cursor.plusDays(7)) {
                LocalDate finish = cursor.plusDays(6);
                if (finish.isAfter(today)) finish = today;
                buckets.add(new Bucket(cursor, finish, cursor.format(SMALL_DATE)));
            }
        } else {
            for (YearMonth month = YearMonth.from(start); !month.atDay(1).isAfter(today); month = month.plusMonths(1)) {
                LocalDate from = month.atDay(1);
                LocalDate through = month.atEndOfMonth().isAfter(today) ? today : month.atEndOfMonth();
                buckets.add(new Bucket(from, through, from.format(MONTH_LABEL)));
            }
        }

        List<Attempt> attempts = attempts(id);
        List<String> labels = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        int plotted = 0;
        for (Bucket b : buckets) {
            List<Attempt> matches = attempts.stream().filter(a -> a.percent() != null &&
                    !a.completedAt().toLocalDate().isBefore(b.from()) &&
                    !a.completedAt().toLocalDate().isAfter(b.through())).toList();
            labels.add(b.title());
            counts.add(matches.size());
            if (matches.isEmpty()) {
                scores.add(null); // no attempts = gap, not a zero score
            } else {
                double mean = matches.stream().mapToDouble(a -> a.percent()).average().orElseThrow();
                scores.add(Math.round(mean * 10.0) / 10.0);
                plotted += matches.size();
            }
        }
        return new ChartData(label, labels, scores, counts, plotted);
    }

    private Student student(String email) {
        if (email == null || email.isBlank() || email.equals("anonymousUser")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in");
        }
        try {
            return jdbc.queryForObject("""
                    SELECT user_id, first_name, last_name, email FROM `User`
                    WHERE email = ? AND status = 'active'
                    """, (rs, row) -> new Student(rs.getInt("user_id"),
                    rs.getString("first_name"), rs.getString("last_name"), rs.getString("email")), email);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "The signed-in account is not linked to an active PrepStart student");
        }
    }

    /** All enrolled modules; absent Progress rows count as 0 for existing topics. */
    private List<ModuleStat> modules(int userId) {
        return jdbc.query("""
                SELECT m.module_id, m.code, m.name,
                       COUNT(t.topic_id) AS topic_count,
                       COALESCE(SUM(CASE WHEN p.percentage >= 100 THEN 1 ELSE 0 END), 0) AS completed_topics,
                       COALESCE(SUM(COALESCE(p.percentage, 0)), 0) AS sum_percent
                FROM `Module` m
                LEFT JOIN Topic t ON t.module_id = m.module_id
                LEFT JOIN (
                    SELECT topic_id,
                           MAX(GREATEST(0, LEAST(100, COALESCE(completion_percentage,0)))) AS percentage
                    FROM Progress
                    WHERE user_id = ?
                    GROUP BY topic_id
                ) p ON p.topic_id = t.topic_id
                WHERE EXISTS (
                    SELECT 1 FROM Enrollment e
                    WHERE e.module_id = m.module_id AND e.user_id = ?
                )
                GROUP BY m.module_id, m.code, m.name
                ORDER BY m.name, m.code
                """, (rs, row) -> new ModuleStat(rs.getInt("module_id"),
                rs.getString("code"), rs.getString("name"),
                rs.getInt("topic_count"), rs.getInt("completed_topics"),
                rs.getDouble("sum_percent")), userId, userId);
    }

    /** Completed attempts only. Subquery avoids multiplying rows when user responses exist. */
    private List<Attempt> attempts(int userId) {
        return jdbc.query("""
                SELECT a.attempt_id, a.score, a.completed_at,
                       (SELECT COUNT(DISTINCT r.question_id) FROM UserResponse r
                        WHERE r.attempt_id = a.attempt_id) AS question_count,
                       m.code AS module_code, q.difficulty_level AS difficulty
                FROM QuizAttempt a
                LEFT JOIN Quiz q ON q.quiz_id = a.quiz_id
                LEFT JOIN Topic t ON t.topic_id = q.topic_id
                LEFT JOIN `Module` m ON m.module_id = t.module_id
                WHERE a.user_id = ? AND a.completed_at IS NOT NULL
                ORDER BY a.completed_at DESC, a.attempt_id DESC
                """, this::readAttempt, userId);
    }

    private Attempt readAttempt(ResultSet rs, int row) throws SQLException {
        Timestamp completed = rs.getTimestamp("completed_at");
        int rawScore = rs.getInt("score");
        Integer score = rs.wasNull() ? null : rawScore;
        return new Attempt(rs.getLong("attempt_id"), completed.toLocalDateTime(),
                score, rs.getInt("question_count"), rs.getString("module_code"),
                rs.getString("difficulty"));
    }
}
