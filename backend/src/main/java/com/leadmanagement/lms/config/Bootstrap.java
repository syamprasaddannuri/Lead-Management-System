package com.leadmanagement.lms.config;

import com.leadmanagement.lms.identity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Seeds permissions, roles, and the root super-admin on first boot. Idempotent. */
@Component
public class Bootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(Bootstrap.class);

    private final PermissionRepository permissions;
    private final RoleRepository roles;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String superAdminEmail;
    private final String superAdminPassword;

    public Bootstrap(PermissionRepository permissions, RoleRepository roles, UserRepository users,
                     PasswordEncoder encoder,
                     @Value("${app.superadmin.email}") String superAdminEmail,
                     @Value("${app.superadmin.password}") String superAdminPassword) {
        this.permissions = permissions;
        this.roles = roles;
        this.users = users;
        this.encoder = encoder;
        this.superAdminEmail = superAdminEmail;
        this.superAdminPassword = superAdminPassword;
    }

    // Permission catalog
    static final String USER_MANAGE = "USER_MANAGE";
    static final String ROLE_MANAGE = "ROLE_MANAGE";
    static final String LEAD_READ_ALL = "LEAD_READ_ALL";
    static final String LEAD_READ_OWN = "LEAD_READ_OWN";
    static final String LEAD_WRITE = "LEAD_WRITE";
    static final String LEAD_ASSIGN = "LEAD_ASSIGN";
    static final String PIPELINE_CONFIG = "PIPELINE_CONFIG";
    static final String CAMPAIGN_MANAGE = "CAMPAIGN_MANAGE";
    static final String EMAIL_SEND = "EMAIL_SEND";

    @Override
    @Transactional
    public void run(String... args) {
        Map<String, Permission> perms = new HashMap<>();
        for (String p : List.of(USER_MANAGE, ROLE_MANAGE, LEAD_READ_ALL, LEAD_READ_OWN,
                LEAD_WRITE, LEAD_ASSIGN, PIPELINE_CONFIG, CAMPAIGN_MANAGE, EMAIL_SEND)) {
            perms.put(p, permissions.findByName(p).orElseGet(() -> permissions.save(new Permission(p))));
        }

        upsertRole("SUPER_ADMIN", perms, perms.keySet());
        upsertRole("ADMIN", perms, Set.of(USER_MANAGE, LEAD_READ_ALL, LEAD_WRITE, LEAD_ASSIGN, EMAIL_SEND));
        upsertRole("SALES_MANAGER", perms, Set.of(LEAD_READ_ALL, LEAD_WRITE, LEAD_ASSIGN, PIPELINE_CONFIG, EMAIL_SEND));
        upsertRole("SALES_REP", perms, Set.of(LEAD_READ_OWN, LEAD_WRITE, EMAIL_SEND));
        upsertRole("MARKETING", perms, Set.of(LEAD_READ_ALL, CAMPAIGN_MANAGE, EMAIL_SEND));

        if (!users.existsByEmailIgnoreCase(superAdminEmail)) {
            User root = new User();
            root.setEmail(superAdminEmail.toLowerCase().trim());
            root.setPasswordHash(encoder.encode(superAdminPassword));
            root.setFirstName("LMS");
            root.setLastName("Root");
            root.setActive(true);
            root.setRoles(Set.of(roles.findByName("SUPER_ADMIN").orElseThrow()));
            users.save(root);
            log.info("Seeded SUPER_ADMIN: {}", superAdminEmail);
        }
    }

    private void upsertRole(String name, Map<String, Permission> perms, Set<String> permNames) {
        Role role = roles.findByName(name).orElseGet(() -> new Role(name));
        Set<Permission> set = new HashSet<>();
        for (String p : permNames) set.add(perms.get(p));
        role.setPermissions(set);
        roles.save(role);
    }
}
