package com.prepstart.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class QuizMeController {

    @GetMapping("/quizme")
    public String quizMe() {
        return "quizme";
    }
}