package com.prepstart.Service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Uses the EXISTING prepstart.sql tables: Quiz, Question, QuestionOption,
 * QuizAttempt and UserResponse. No new entity or SQL table is required.
 */
@Service
public class QuizMeService {

    private final JdbcTemplate jdbc;

    public QuizMeService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record OptionView(long id, String text, String letter) implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
    }

    public record QuestionView(long id, String text, List<OptionView> options) implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
    }

    public record Review(String question, String yourAnswer, String correctAnswer, boolean correct)
            implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
    }

    public record Result(int score, int total, int percentage, String typeLabel,
                         String difficultyLabel, List<Review> reviewItems) implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
    }

    public static final class QuizState implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        private final long quizId;
        private final Long topicId;
        private final String typeLabel;
        private final String difficultyLabel;
        private final List<Long> questionIds;
        private final Map<Long, Long> answers = new LinkedHashMap<>();
        private final LocalDateTime startedAt = LocalDateTime.now();
        private int index;

        public QuizState(long quizId, Long topicId, String typeLabel,
                         String difficultyLabel, List<Long> questionIds) {
            this.quizId = quizId;
            this.topicId = topicId;
            this.typeLabel = typeLabel;
            this.difficultyLabel = difficultyLabel;
            this.questionIds = List.copyOf(questionIds);
        }

        public long getQuizId() { return quizId; }
        public Long getTopicId() { return topicId; }
        public String getTypeLabel() { return typeLabel; }
        public String getDifficultyLabel() { return difficultyLabel; }
        public LocalDateTime getStartedAt() { return startedAt; }
        public int getIndex() { return index; }
        public int getTotal() { return questionIds.size(); }
        public boolean isFinished() { return index >= questionIds.size(); }
        public long getCurrentQuestionId() { return questionIds.get(index); }
        public List<Long> getQuestionIds() { return questionIds; }
        public Map<Long, Long> getAnswers() { return answers; }

        private void answer(long optionId) {
            answers.put(getCurrentQuestionId(), optionId);
            index++;
        }
    }

    private record DbOption(long id, String text, boolean correct) { }
    private record DbQuestion(long id, String text, List<DbOption> options) { }
    private record Candidate(long quizId, Long topicId, List<DbQuestion> questions) { }

    public List<String> getDifficulties() {
        List<String> levels = jdbc.queryForList(
                "SELECT DISTINCT difficulty_level FROM Quiz " +
                "WHERE difficulty_level IS NOT NULL AND TRIM(difficulty_level) <> '' ORDER BY difficulty_level",
                String.class);
        return levels.isEmpty() ? List.of("Easy", "Medium", "Hard") : levels;
    }

    public QuizState startQuiz(String quizType, String difficulty) {
        if (!Set.of("multiple", "truefalse", "mixed").contains(quizType)) {
            throw new IllegalArgumentException("Please choose a valid quiz type.");
        }
        if (difficulty == null || difficulty.isBlank() || difficulty.length() > 50) {
            throw new IllegalArgumentException("Please choose a difficulty level.");
        }
        // Only accept values actually listed in the database (or harmless defaults
        // shown when there are no records yet); never use input as SQL text.
        boolean matches = getDifficulties().stream().anyMatch(x -> x.equalsIgnoreCase(difficulty.trim()));
        if (!matches) throw new IllegalArgumentException("Please choose a listed difficulty level.");

        List<Candidate> candidates = new ArrayList<>();
        jdbc.query("SELECT quiz_id, topic_id FROM Quiz WHERE LOWER(TRIM(difficulty_level)) = ?",
                rs -> {
                    long quizId = rs.getLong("quiz_id");
                    long rawTopicId = rs.getLong("topic_id");
                    Long topicId = rs.wasNull() ? null : rawTopicId;
                    List<DbQuestion> questions = loadEligibleQuestions(quizId).stream()
                            .filter(q -> "mixed".equals(quizType) ||
                                    ("truefalse".equals(quizType) == isTrueFalse(q.options())))
                            .collect(Collectors.toCollection(ArrayList::new));
                    if (!questions.isEmpty()) candidates.add(new Candidate(quizId, topicId, questions));
                }, difficulty.trim().toLowerCase(Locale.ROOT));

        if (candidates.isEmpty()) {
            throw new IllegalArgumentException(
                    "No questions are available for this type and difficulty yet. " +
                    "Your database needs Quiz, Question and QuestionOption records.");
        }

        Collections.shuffle(candidates);
        // Prefer the quiz with enough questions rather than merging across quiz IDs:
        // QuizAttempt can reference only ONE Quiz in your existing schema.
        Comparator<Candidate> sizeComparator = Comparator.comparingInt(c -> c.questions().size());
        Comparator<Candidate> preference = "mixed".equals(quizType)
                ? Comparator.comparingInt((Candidate c) -> hasBothTypes(c.questions()) ? 1 : 0)
                    .thenComparing(sizeComparator)
                : sizeComparator;
        Candidate chosen = candidates.stream().max(preference).orElseThrow();

        List<DbQuestion> shuffled = new ArrayList<>(chosen.questions());
        Collections.shuffle(shuffled);
        List<Long> selectedIds = new ArrayList<>();
        if ("mixed".equals(quizType) && hasBothTypes(shuffled)) {
            // When available, include at least one of each kind in a mixed quiz.
            selectedIds.add(shuffled.stream().filter(q -> isTrueFalse(q.options()))
                    .findFirst().orElseThrow().id());
            selectedIds.add(shuffled.stream().filter(q -> !isTrueFalse(q.options()))
                    .findFirst().orElseThrow().id());
        }
        for (DbQuestion question : shuffled) {
            if (selectedIds.size() >= 5) break;
            if (!selectedIds.contains(question.id())) selectedIds.add(question.id());
        }
        Collections.shuffle(selectedIds);

        String label = switch (quizType) {
            case "truefalse" -> "True / False";
            case "multiple" -> "Multiple Choice";
            default -> "Mixed Questions";
        };
        return new QuizState(chosen.quizId(), chosen.topicId(), label,
                difficulty.trim(), selectedIds);
    }

    private List<DbQuestion> loadEligibleQuestions(long quizId) {
        List<DbQuestion> available = new ArrayList<>();
        jdbc.query("SELECT question_id, question_text FROM Question WHERE quiz_id = ? ORDER BY question_id",
                rs -> {
                    long id = rs.getLong("question_id");
                    String questionText = rs.getString("question_text");
                    List<DbOption> options = loadOptions(id);
                    long correctCount = options.stream().filter(DbOption::correct).count();
                    // UI is single-select. Skip incomplete or multi-correct questions.
                    if (questionText != null && !questionText.isBlank() &&
                            options.size() >= 2 && correctCount == 1 &&
                            options.stream().allMatch(o -> o.text() != null && !o.text().isBlank())) {
                        available.add(new DbQuestion(id, questionText, options));
                    }
                }, quizId);
        return available;
    }

    private List<DbOption> loadOptions(long questionId) {
        return jdbc.query("SELECT option_id, option_text, is_correct FROM QuestionOption " +
                        "WHERE question_id = ? ORDER BY option_id",
                (rs, i) -> new DbOption(rs.getLong("option_id"),
                        rs.getString("option_text"), rs.getBoolean("is_correct")), questionId);
    }

    private boolean hasBothTypes(List<DbQuestion> questions) {
        return questions.stream().anyMatch(q -> isTrueFalse(q.options())) &&
               questions.stream().anyMatch(q -> !isTrueFalse(q.options()));
    }

    private boolean isTrueFalse(List<DbOption> options) {
        if (options.size() != 2) return false;
        Set<String> labels = options.stream()
                .map(o -> o.text() == null ? "" : o.text().trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return labels.equals(Set.of("true", "false"));
    }

    public QuestionView currentQuestion(QuizState state) {
        long id = state.getCurrentQuestionId();
        List<String> questionTexts = jdbc.query("SELECT question_text FROM Question " +
                        "WHERE quiz_id = ? AND question_id = ?",
                (rs, i) -> rs.getString("question_text"), state.getQuizId(), id);
        if (questionTexts.isEmpty()) throw new IllegalStateException("Question no longer exists.");
        List<DbOption> dbOptions = loadOptions(id);
        List<OptionView> displayed = new ArrayList<>();
        for (int i = 0; i < dbOptions.size(); i++) {
            DbOption option = dbOptions.get(i);
            // Correct-answer flags never enter the displayed view.
            displayed.add(new OptionView(option.id(), option.text(), String.valueOf((char) ('A' + i))));
        }
        return new QuestionView(id, questionTexts.get(0), displayed);
    }

    public void submitAnswer(QuizState state, Long optionId) {
        if (state.isFinished()) throw new IllegalStateException("Quiz is already complete.");
        if (optionId == null || loadOptions(state.getCurrentQuestionId()).stream()
                .noneMatch(o -> o.id() == optionId)) {
            throw new IllegalArgumentException("Please select one of the listed answers.");
        }
        state.answer(optionId);
    }

    @Transactional
    public Result completeQuiz(QuizState state, Authentication authentication) {
        if (!state.isFinished()) throw new IllegalStateException("Quiz isn't finished yet.");
        List<Review> review = new ArrayList<>();
        int score = 0;
        for (long questionId : state.getQuestionIds()) {
            String questionText = jdbc.queryForObject(
                    "SELECT question_text FROM Question WHERE question_id = ? AND quiz_id = ?",
                    String.class, questionId, state.getQuizId());
            List<DbOption> options = loadOptions(questionId);
            DbOption correctOption = options.stream().filter(DbOption::correct).findFirst()
                    .orElseThrow(() -> new IllegalStateException("The question has no correct option."));
            Long selectedId = state.getAnswers().get(questionId);
            String answerText = options.stream().filter(o -> Objects.equals(o.id(), selectedId))
                    .map(DbOption::text).findFirst().orElse("Not answered");
            boolean isCorrect = correctOption.id() == (selectedId == null ? -1L : selectedId);
            if (isCorrect) score++;
            review.add(new Review(questionText, answerText, correctOption.text(), isCorrect));
        }

        Integer userId = lookupUserId(authentication);
        int finalScore = score;
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO QuizAttempt (user_id, quiz_id, score, started_at, completed_at) " +
                    "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)", Statement.RETURN_GENERATED_KEYS);
            if (userId == null) ps.setNull(1, Types.INTEGER);
            else ps.setInt(1, userId);
            ps.setLong(2, state.getQuizId());
            ps.setInt(3, finalScore);  // raw number correct, not percentage
            ps.setTimestamp(4, Timestamp.valueOf(state.getStartedAt()));
            return ps;
        }, keys);
        if (keys.getKey() == null) throw new IllegalStateException("Could not save quiz attempt.");
        long attemptId = keys.getKey().longValue();
        // Current schema has no selected_option_id. We record completion against
        // each question; exact selected answers stay in the HTTP session only.
        for (long questionId : state.getQuestionIds()) {
            jdbc.update("INSERT INTO UserResponse " +
                            "(user_id, topic_id, attempt_id, question_id, completion_percentage) " +
                            "VALUES (?, ?, ?, ?, ?)",
                    userId, state.getTopicId(), attemptId, questionId, new BigDecimal("100.00"));
        }
        int percentage = (int) Math.round(score * 100.0 / state.getTotal());
        return new Result(score, state.getTotal(), percentage, state.getTypeLabel(),
                state.getDifficultyLabel(), List.copyOf(review));
    }

    private Integer lookupUserId(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() ||
                auth.getName() == null || "anonymousUser".equals(auth.getName())) return null;
        List<Integer> matches = jdbc.query("SELECT user_id FROM User WHERE email = ? LIMIT 1",
                (rs, i) -> rs.getInt("user_id"), auth.getName());
        return matches.isEmpty() ? null : matches.get(0);
    }
}
