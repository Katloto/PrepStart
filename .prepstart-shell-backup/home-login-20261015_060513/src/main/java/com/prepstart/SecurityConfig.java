
package com.prepstart;

import com.prepstart.Service.CustomOAuth2UserService;
import com.prepstart.Service.CustomOidcUserService;
import com.prepstart.Service.CustomUserDetailsService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
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
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

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

                        // Administrator pages
                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        // All other pages require login
                        .anyRequest().authenticated()
                )

                // NORMAL EMAIL AND PASSWORD LOGIN
                .formLogin(form -> form

                        // Custom login page
                        .loginPage("/login")

                        // Login form submission
                        .loginProcessingUrl("/login")

                        // Successful login goes to Dashboard
                        .defaultSuccessUrl("/dashboard", true)

                        // Failed login returns to login
                        .failureUrl("/login?error=true")

                        .permitAll()
                )

                // GOOGLE AND MICROSOFT LOGIN
                .oauth2Login(oauth -> oauth

                        .loginPage("/login")

                        // Successful OAuth login goes to Dashboard
                        .defaultSuccessUrl("/dashboard", true)

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
