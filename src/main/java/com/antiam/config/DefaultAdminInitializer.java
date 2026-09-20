package com.antiam.config;

import com.antiam.domain.CredentialType;
import com.antiam.domain.UserAccount;
import com.antiam.domain.UserCredential;
import com.antiam.repository.UserAccountRepository;
import com.antiam.repository.UserCredentialRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Configuration
public class DefaultAdminInitializer {

    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_ADMIN_PASSWORD = "admin123456";

    @Bean
    ApplicationRunner defaultAdminUserRunner(DefaultAdminUserSeeder seeder) {
        return args -> seeder.seed();
    }

    @Bean
    DefaultAdminUserSeeder defaultAdminUserSeeder(
        UserAccountRepository users,
        UserCredentialRepository credentials,
        PasswordEncoder passwordEncoder
    ) {
        return new DefaultAdminUserSeeder(users, credentials, passwordEncoder);
    }

    static class DefaultAdminUserSeeder {
        private final UserAccountRepository users;
        private final UserCredentialRepository credentials;
        private final PasswordEncoder passwordEncoder;

        DefaultAdminUserSeeder(
            UserAccountRepository users,
            UserCredentialRepository credentials,
            PasswordEncoder passwordEncoder
        ) {
            this.users = users;
            this.credentials = credentials;
            this.passwordEncoder = passwordEncoder;
        }

        @Transactional
        void seed() {
            if (users.count() > 0) {
                return;
            }
            UserAccount user = users.save(new UserAccount(
                DEFAULT_ADMIN_USERNAME,
                DEFAULT_ADMIN_USERNAME,
                null,
                null,
                null,
                null
            ));
            credentials.save(new UserCredential(
                user,
                CredentialType.PASSWORD,
                passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD),
                false
            ));
        }
    }
}
