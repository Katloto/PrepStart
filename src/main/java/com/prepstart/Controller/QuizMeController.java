package com.prepstart.controller;

import com.prepstart.service.QuizMeService;
import com.prepstart.service.QuizMeService.QuizState;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/quiz-me")
public class QuizMeController {

    private static final String QUIZ_KEY = "prepStartQuizMeCurrent";
    private static final String RESULT_KEY = "prepStartQuizMeResult";
    private final QuizMeService service;

    public QuizMeController(QuizMeService service) {
        this.service = service;
    }

    @GetMapping
    public String setup(Model model) {
        model.addAttribute("difficulties", service.getDifficulties());
        return "QuizMe";
    }

    @PostMapping("/start")
    public String start(@RequestParam("quizType") String quizType,
                        @RequestParam("difficulty") String difficulty,
                        HttpSession session, RedirectAttributes redirect) {
        try {
            QuizState quiz = service.startQuiz(quizType, difficulty);
            session.setAttribute(QUIZ_KEY, quiz);
            session.removeAttribute(RESULT_KEY);
            return "redirect:/quiz-me/play";
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/quiz-me";
        }
    }

    @GetMapping("/play")
    public String play(HttpSession session, Model model) {
        QuizState quiz = (QuizState) session.getAttribute(QUIZ_KEY);
        if (quiz == null) return "redirect:/quiz-me";
        if (quiz.isFinished()) return "redirect:/quiz-me/results";

        model.addAttribute("question", service.currentQuestion(quiz));
        model.addAttribute("questionNumber", quiz.getIndex() + 1);
        model.addAttribute("totalQuestions", quiz.getTotal());
        model.addAttribute("progressPercent", (int) ((quiz.getIndex() + 1) * 100.0 / quiz.getTotal()));
        model.addAttribute("quizType", quiz.getTypeLabel());
        model.addAttribute("difficulty", quiz.getDifficultyLabel());
        model.addAttribute("lastQuestion", quiz.getIndex() + 1 == quiz.getTotal());
        return "QuizMePlay";
    }

    @PostMapping("/answer")
    public String answer(@RequestParam(value = "answer", required = false) Long answer,
                         HttpSession session, Authentication authentication,
                         RedirectAttributes redirect) {
        QuizState quiz = (QuizState) session.getAttribute(QUIZ_KEY);
        if (quiz == null) return "redirect:/quiz-me";
        if (quiz.isFinished()) return "redirect:/quiz-me/results";
        try {
            service.submitAnswer(quiz, answer);
            if (quiz.isFinished()) {
                var result = service.completeQuiz(quiz, authentication);
                session.setAttribute(RESULT_KEY, result);
                session.removeAttribute(QUIZ_KEY);
                return "redirect:/quiz-me/results";
            }
            return "redirect:/quiz-me/play";
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/quiz-me/play";
        }
    }

    @GetMapping("/results")
    public String results(HttpSession session, Model model) {
        Object result = session.getAttribute(RESULT_KEY);
        if (result == null) return "redirect:/quiz-me";
        model.addAttribute("result", result);
        return "QuizMeResult";
    }
}
