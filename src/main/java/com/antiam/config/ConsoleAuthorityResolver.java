package com.antiam.config;

import com.antiam.repository.UserAccountRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * 根据用户直接角色和用户组角色解析控制台权限。IAM 管理员拥有全部控制台权限点。
 */
@Component
@RequiredArgsConstructor
public class ConsoleAuthorityResolver {

    private static final String WRITE_SUFFIX = ":write";
    private static final String READ_SUFFIX = ":read";

    private final UserAccountRepository users;

    public ConsoleAccess resolve(String username) {
        if (users.findEffectiveRoleCodesByUsername(username).contains(SecurityAuthorities.IAM_ADMIN_ROLE)) {
            return new ConsoleAccess(true, new LinkedHashSet<>(ConsolePermission.codes()));
        }
        Set<String> granted = users.findEffectivePermissionCodesByUsername(username);
        Set<String> permissions = new LinkedHashSet<>();
        for (String code : ConsolePermission.codes()) {
            if (granted.contains(code) || (code.endsWith(READ_SUFFIX) && granted.contains(writeCodeOf(code)))) {
                permissions.add(code);
            }
        }
        return new ConsoleAccess(false, permissions);
    }

    public List<GrantedAuthority> authorities(String username) {
        ConsoleAccess access = resolve(username);
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (access.superAdmin()) {
            authorities.add(new SimpleGrantedAuthority(SecurityAuthorities.IAM_ADMIN_AUTHORITY));
        }
        access.permissions().forEach(code -> authorities.add(new SimpleGrantedAuthority(code)));
        return authorities;
    }

    private static String writeCodeOf(String readCode) {
        return readCode.substring(0, readCode.length() - READ_SUFFIX.length()) + WRITE_SUFFIX;
    }

    public record ConsoleAccess(boolean superAdmin, Set<String> permissions) {
    }
}
