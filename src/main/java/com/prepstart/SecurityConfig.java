
package com.prepstart;

import com.prepstart.Service.CustomOAuth2UserService;
import com.prepstart.Service.CustomOidcUserService;
import com.prepstart.Service.CustomUserDetailsService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomOidcUserService customOidcUserService;

    public SecurityConfig(
            CustomUserDetailsService customUserDetailsService,
            CustomOAuth2UserService customOAuth2UserService,
            CustomOidcUserService customOidcUserService) {

        this.customUserDetailsService = customUserDetailsService;
        this.customOAuth2UserService = customOAuth2UserService;
        this.customOidcUserService = customOidcUserService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, PrepStartRoleRouting roleRouting) throws Exception {

        http
                // PUBLIC AND PROTECTED PAGES
                .authorizeHttpRequests(auth -> auth

                        // Public landing page
                        .requestMatchers("/").permitAll()

                        // Static resources
                        .requestMatchers(
                                "/style.css",
                                "/images/**",
                                "/css/**",
                                "/js/**",
                                "/fonts/**",
                                "/favicon.ico"
                        ).permitAll()

                        // Public authentication pages
                        .requestMatchers(
                                "/login",
                                "/register",
                                "/verify",
                                "/forgot-password",
                                "/reset-password",
                                "/error"
                        ).permitAll()

                        // OAuth2 authorization and login callback routes
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()

                        // Admin users may access only the admin section.
                        // Role is resolved from the database, not a hardcoded role ID.
                        .requestMatchers("/admin", "/admin/**")
                        .access((authentication, context) -> new AuthorizationDecision(
                                roleRouting.isRole(authentication.get(), "ADMIN")))

                        // The remainder of the protected application is for students.
                        // Admins cannot open /home, /dashboard, /modules, etc. by URL.
                        .anyRequest().access((authentication, context) -> new AuthorizationDecision(
                                roleRouting.isRole(authentication.get(), "STUDENT")))
                )

                // NORMAL EMAIL AND PASSWORD LOGIN
                .formLogin(form -> form

                        // Custom login page
                        .loginPage("/login")

                        // Login form submission
                        .loginProcessingUrl("/login")

                        // Redirect according to the role stored in PrepStart
                        .successHandler(roleRouting)

                        // Failed login returns to login
                        .failureUrl("/login?error=true")

                        .permitAll()
                )

                // GOOGLE AND MICROSOFT LOGIN
                .oauth2Login(oauth -> oauth

                        .loginPage("/login")

                        // Redirect Google/Microsoft sign-ins according to role
                        .successHandler(roleRouting)

                        .userInfoEndpoint(userInfo -> userInfo

                                // Google OAuth
                                .userService(customOAuth2UserService)

                                // Microsoft OIDC
                                .oidcUserService(customOidcUserService)
                        )
                )

                // REMEMBER ME
                .rememberMe(remember -> remember

                        .key("prepstart-remember-me-key-2026")

                        // Remember login for 14 days
                        .tokenValiditySeconds(14 * 24 * 60 * 60)

                        .rememberMeParameter("remember-me")

                        .userDetailsService(customUserDetailsService)
                )

                // SESSION MANAGEMENT
                .sessionManagement(session -> session

                        // Expired sessions return to login
                        .invalidSessionUrl("/login?expired=true")

                        // One active session per user
                        .maximumSessions(1)

                        // New login can replace previous session
                        .maxSessionsPreventsLogin(false)
                )

                // LOGOUT
                .logout(logout -> logout

                        .logoutUrl("/logout")

                        .logoutSuccessUrl("/login?logout=true")

                        .deleteCookies("JSESSIONID", "remember-me")

                        .permitAll()
                )

                // Preserving your existing CSRF setting
                .csrf(csrf -> csrf.disable());

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
