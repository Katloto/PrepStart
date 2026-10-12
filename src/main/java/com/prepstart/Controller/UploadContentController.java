package com.prepstart.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UploadContentController {

    @GetMapping("/uploadcontent")
    public String uploadContent() {
        return "uploadcontent";
    }
}