package com.antiam.config;

import com.antiam.domain.CredentialType;
import com.antiam.domain.Permission;
import com.antiam.domain.Role;
import com.antiam.domain.UserAccount;
import com.antiam.domain.UserCredential;
import com.antiam.repository.PermissionRepository;
import com.antiam.repository.RoleRepository;
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
        RoleRepository roles,
        PermissionRepository permissions,
        PasswordEncoder passwordEncoder
    ) {
        return new DefaultAdminUserSeeder(users, credentials, roles, permissions, passwordEncoder);
    }

    static class DefaultAdminUserSeeder {
        private final UserAccountRepository users;
        private final UserCredentialRepository credentials;
        private final RoleRepository roles;
        private final PermissionRepository permissions;
        private final PasswordEncoder passwordEncoder;

        DefaultAdminUserSeeder(
            UserAccountRepository users,
            UserCredentialRepository credentials,
            RoleRepository roles,
            PermissionRepository permissions,
            PasswordEncoder passwordEncoder
        ) {
            this.users = users;
            this.credentials = credentials;
            this.roles = roles;
            this.permissions = permissions;
            this.passwordEncoder = passwordEncoder;
        }

        @Transactional
        void seed() {
            seedConsolePermissions();
            UserAccount user = users.findByUsername(DEFAULT_ADMIN_USERNAME).orElse(null);
            if (user == null) {
                if (users.count() > 0) {
                    return;
                }
                user = users.save(new UserAccount(
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
            Role adminRole = roles.findByCode(SecurityAuthorities.IAM_ADMIN_ROLE)
                .orElseGet(() -> roles.save(new Role(SecurityAuthorities.IAM_ADMIN_ROLE, "IAM 管理员", "访问 IAM 后台管理能力")));
            user.grant(adminRole);
        }

        private void seedConsolePermissions() {
            for (ConsolePermission consolePermission : ConsolePermission.values()) {
                if (permissions.findByCode(consolePermission.code()).isEmpty()) {
                    permissions.save(new Permission(
                        consolePermission.code(),
                        consolePermission.displayName(),
                        consolePermission.description()));
                }
            }
        }
    }
}
