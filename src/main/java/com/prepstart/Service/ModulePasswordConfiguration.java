package com.prepstart.Service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Only supplies BCrypt when another part of PrepStart does not supply a PasswordEncoder. */
@Configuration
public class ModulePasswordConfiguration {
    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    public PasswordEncoder prepStartPasswordEncoder() { return new BCryptPasswordEncoder(); }
}
