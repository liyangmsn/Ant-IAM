package com.antiam.config;

import com.antiam.domain.CredentialType;
import com.antiam.domain.UserAccount;
import com.antiam.domain.UserCredential;
import com.antiam.repository.UserAccountRepository;
import com.antiam.repository.UserCredentialRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.security.autoconfigure.SecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Configuration
public class DefaultAdminInitializer {

    @Bean
    ApplicationRunner defaultAdminUserRunner(DefaultAdminUserSeeder seeder) {
        return args -> seeder.seed();
    }

    @Bean
    DefaultAdminUserSeeder defaultAdminUserSeeder(
        SecurityProperties security,
        UserAccountRepository users,
        UserCredentialRepository credentials,
        PasswordEncoder passwordEncoder
    ) {
        return new DefaultAdminUserSeeder(security, users, credentials, passwordEncoder);
    }

    static class DefaultAdminUserSeeder {
        private final SecurityProperties security;
        private final UserAccountRepository users;
        private final UserCredentialRepository credentials;
        private final PasswordEncoder passwordEncoder;

        DefaultAdminUserSeeder(
            SecurityProperties security,
            UserAccountRepository users,
            UserCredentialRepository credentials,
            PasswordEncoder passwordEncoder
        ) {
            this.security = security;
            this.users = users;
            this.credentials = credentials;
            this.passwordEncoder = passwordEncoder;
        }

        @Transactional
        void seed() {
            SecurityProperties.User configuredUser = security.getUser();
            UserAccount user = users.findByUsername(configuredUser.getName())
                .orElseGet(() -> users.save(new UserAccount(
                    configuredUser.getName(),
                    configuredUser.getName(),
                    null,
                    null,
                    null,
                    null
                )));

            credentials.findByUserAndType(user, CredentialType.PASSWORD)
                .orElseGet(() -> credentials.save(new UserCredential(
                    user,
                    CredentialType.PASSWORD,
                    passwordEncoder.encode(configuredUser.getPassword()),
                    false
                )));
        }
    }
}
