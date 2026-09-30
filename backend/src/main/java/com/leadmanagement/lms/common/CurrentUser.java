package com.leadmanagement.lms.common;

import com.leadmanagement.lms.identity.User;
import com.leadmanagement.lms.identity.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    private final UserRepository userRepository;

    public CurrentUser(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** The authenticated user's email (JWT subject), or null if anonymous. */
    public String email() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || "anonymousUser".equals(auth.getName())) return null;
        return auth.getName();
    }

    public User get() {
        String email = email();
        if (email == null) throw ApiException.unauthorized("Not authenticated");
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> ApiException.unauthorized("Not authenticated"));
    }
}
