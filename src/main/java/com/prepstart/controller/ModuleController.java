package com.prepstart.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ModuleController {

    @GetMapping("/modules")
    public String showModules() {
        return "MyModules";
    }

    @GetMapping("/modules/NADF511")
    public String showModuleDetails() {
        return "ModuleDetails";
    }

}