package com.prepstart.Service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Only installed if the project has no existing UserDetailsService. Login username = User.email. */
@Service
@ConditionalOnMissingBean(UserDetailsService.class)
public class PrepStartDbUserDetailsService implements UserDetailsService {
    private final JdbcTemplate jdbc;
    public PrepStartDbUserDetailsService(JdbcTemplate jdbc) {this.jdbc=jdbc;}

    @Override public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        var matches=jdbc.query("""
                SELECT email,password_hash,status FROM `User` WHERE email=?
                """,(rs,i)->User.withUsername(rs.getString("email"))
                    .password(rs.getString("password_hash"))
                    .roles("STUDENT")
                    .disabled(!"active".equalsIgnoreCase(rs.getString("status")))
                    .build(),email);
        if (matches.size()!=1) throw new UsernameNotFoundException("PrepStart account not found");
        return matches.getFirst();
    }
}
