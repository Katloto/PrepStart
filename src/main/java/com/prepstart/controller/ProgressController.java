package com.prepstart.controller;

import com.prepstart.model.Progress;
import com.prepstart.model.QuizAttempt;
import com.prepstart.repository.ProgressRepository;
import com.prepstart.repository.QuizAttemptRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class ProgressController {

    private final ProgressRepository progressRepository;
    private final QuizAttemptRepository quizAttemptRepository;

    public ProgressController(ProgressRepository progressRepository,
                              QuizAttemptRepository quizAttemptRepository) {
        this.progressRepository = progressRepository;
        this.quizAttemptRepository = quizAttemptRepository;
    }

    @GetMapping("/progress")
    public String progress(Model model) {

        Integer userId = 1;

        List<Progress> progressList =
                progressRepository.findByUserId(userId);

        List<QuizAttempt> quizAttempts =
                quizAttemptRepository.findByUserId(userId);

        int overallProgress = 0;

        if (!progressList.isEmpty()) {

            int total = 0;

            for (Progress progress : progressList) {
                if (progress.getCompletionPercentage() != null) {
                    total += progress.getCompletionPercentage();
                }
            }

            overallProgress = total / progressList.size();
        }

        int quizCount = quizAttempts.size();

        int averageScore = 0;

        if (!quizAttempts.isEmpty()) {

            int totalScore = 0;
            int scoredAttempts = 0;

            for (QuizAttempt attempt : quizAttempts) {

                if (attempt.getScore() != null) {
                    totalScore += attempt.getScore();
                    scoredAttempts++;
                }
            }

            if (scoredAttempts > 0) {
                averageScore = totalScore / scoredAttempts;
            }
        }

        model.addAttribute("overallProgress", overallProgress);
        model.addAttribute("quizCount", quizCount);
        model.addAttribute("averageScore", averageScore);

        return "progress";
    }
}