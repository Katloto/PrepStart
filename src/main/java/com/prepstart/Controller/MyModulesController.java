package com.prepstart.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MyModulesController {

    @GetMapping("/mymodules")
    public String myModules() {
        return "mymodules";
    }
}