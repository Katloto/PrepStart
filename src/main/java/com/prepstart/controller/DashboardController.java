
package com.prepstart.controller;

import com.prepstart.Service.ProgressDashboardService;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@Controller
public class DashboardController {

    private final ProgressDashboardService progressService;
    private final JdbcTemplate jdbc;

    public DashboardController(
            ProgressDashboardService progressService,
            JdbcTemplate jdbc) {
        this.progressService = progressService;
        this.jdbc = jdbc;
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {

        if (authentication == null ||
                !authentication.isAuthenticated() ||
                "anonymousUser".equals(authentication.getName())) {
            return "redirect:/login";
        }

        String email = authentication.getName();

        if (authentication.getPrincipal() instanceof OAuth2User oauth) {
            String oauthEmail = oauth.getAttribute("email");

            if (oauthEmail == null || oauthEmail.isBlank()) {
                oauthEmail = oauth.getAttribute("preferred_username");
            }

            if (oauthEmail != null && !oauthEmail.isBlank()) {
                email = oauthEmail;
            }
        }

        // Loads student, summary, modules and quiz statistics.
        Map<String, Object> data = progressService.dashboard(email);
        model.addAllAttributes(data);

        Integer userId = jdbc.queryForObject(
                "SELECT user_id FROM `User` WHERE email = ?",
                Integer.class,
                email
        );

        // Count the signed-in user's registered modules.
        Integer courseCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM Enrollment WHERE user_id = ?",
                Integer.class,
                userId
        );

        // Count materials available in the user's modules.
        Integer materialCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM Content c
                WHERE EXISTS (
                    SELECT 1 FROM Enrollment e
                    WHERE e.module_id = c.module_id
                    AND e.user_id = ?
                )
                """,
                Integer.class,
                userId
        );

        model.addAttribute("courseCount", courseCount);
        model.addAttribute("studyMaterialCount", materialCount);

        @SuppressWarnings("unchecked")
        Map<String, Object> summary =
                (Map<String, Object>) data.get("summary");

        model.addAttribute("quizCount", summary.get("quizCount"));
        model.addAttribute("averageScore", summary.get("averageScore"));
        model.addAttribute("overallProgress", summary.get("overallProgress"));
        model.addAttribute("modulesCompleted", summary.get("modulesCompleted"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> modules =
                (List<Map<String, Object>>) data.get("modules");

        model.addAttribute(
                "progressHighlights",
                modules.stream().limit(3).toList()
        );

        // Recently opened resources.
        List<Map<String, Object>> recentMaterials = jdbc.query("""
                SELECT c.content_id, c.title,
                       COALESCE(NULLIF(c.file_type, ''),
                                NULLIF(c.content_type, ''), 'FILE') AS kind,
                       m.code AS module_code
                FROM ContentInteraction ci
                JOIN Content c ON c.content_id = ci.content_id
                JOIN `Module` m ON m.module_id = c.module_id
                WHERE ci.user_id = ?
                  AND ci.interaction_type = 'view'
                  AND EXISTS (
                      SELECT 1 FROM Enrollment e
                      WHERE e.module_id = c.module_id
                        AND e.user_id = ?
                  )
                  AND c.file_url IS NOT NULL
                  AND TRIM(c.file_url) <> ''
                GROUP BY c.content_id, c.title,
                         c.file_type, c.content_type, m.code
                ORDER BY MAX(ci.created_at) DESC
                LIMIT 4
                """,
                (rs, row) -> Map.<String, Object>of(
                        "id", rs.getInt("content_id"),
                        "title", rs.getString("title"),
                        "kind", rs.getString("kind"),
                        "moduleCode", rs.getString("module_code")
                ),
                userId, userId
        );

        boolean hasViewedMaterials = !recentMaterials.isEmpty();

        if (!hasViewedMaterials) {
            recentMaterials = jdbc.query("""
                    SELECT c.content_id, c.title,
                           COALESCE(NULLIF(c.file_type, ''),
                                    NULLIF(c.content_type, ''), 'FILE') AS kind,
                           m.code AS module_code
                    FROM Content c
                    JOIN `Module` m ON m.module_id = c.module_id
                    WHERE EXISTS (
                        SELECT 1 FROM Enrollment e
                        WHERE e.module_id = c.module_id
                          AND e.user_id = ?
                    )
                    AND c.file_url IS NOT NULL
                    AND TRIM(c.file_url) <> ''
                    ORDER BY c.created_at DESC, c.content_id DESC
                    LIMIT 4
                    """,
                    (rs, row) -> Map.<String, Object>of(
                            "id", rs.getInt("content_id"),
                            "title", rs.getString("title"),
                            "kind", rs.getString("kind"),
                            "moduleCode", rs.getString("module_code")
                    ),
                    userId
            );
        }

        model.addAttribute("recentMaterials", recentMaterials);
        model.addAttribute("materialsAreViewed", hasViewedMaterials);

        // Recent resource-opening activity.
        List<Map<String, Object>> activities = jdbc.query("""
                SELECT c.content_id, c.title, ci.created_at,
                       COALESCE(m.code, 'General') AS module_code
                FROM ContentInteraction ci
                JOIN Content c ON c.content_id = ci.content_id
                LEFT JOIN `Module` m ON m.module_id = c.module_id
                WHERE ci.user_id = ?
                  AND ci.interaction_type = 'view'
                ORDER BY ci.created_at DESC, ci.interaction_id DESC
                LIMIT 5
                """,
                (rs, row) -> {
                    Timestamp when = rs.getTimestamp("created_at");

                    return Map.<String, Object>of(
                            "title", "Opened " + rs.getString("title"),
                            "detail", "Material - " + rs.getString("module_code"),
                            "link", "/resources/open/" + rs.getInt("content_id"),
                            "when", when == null ? "" : when.toString()
                    );
                },
                userId
        );

        model.addAttribute("recentActivities", activities);

        return "dashboard";
    }
}
