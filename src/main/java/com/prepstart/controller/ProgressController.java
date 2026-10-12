package com.prepstart.controller;

import com.prepstart.Service.ProgressDashboardService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Map;

/** One progress controller only: replace your existing ProgressController.java. */
@Controller
public class ProgressController {

    private final ProgressDashboardService dashboardService;

    public ProgressController(ProgressDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/progress")
    public String showProgress(Model model, Principal principal) {
        if (principal == null) return "redirect:/login";
        Map<String, Object> dashboard = dashboardService.dashboard(emailOf(principal));
        model.addAllAttributes(dashboard);
        return "progress";
    }

    /** JSON time series, scoped to the authenticated student, never to a supplied user ID. */
    @ResponseBody
    @GetMapping("/progress/chart")
    public ProgressDashboardService.ChartData chart(
            @RequestParam(name = "range", defaultValue = "week") String range,
            Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to see your progress");
        }
        return dashboardService.chart(emailOf(principal), range);
    }

    private String emailOf(Principal principal) {
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof OAuth2User oauth) {
            String email = oauth.getAttribute("email");
            if (email != null && !email.isBlank()) return email;
        }
        return principal.getName();
    }
}
