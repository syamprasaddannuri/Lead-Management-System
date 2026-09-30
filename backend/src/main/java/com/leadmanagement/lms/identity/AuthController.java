package com.leadmanagement.lms.identity;

import com.leadmanagement.lms.common.ApiException;
import com.leadmanagement.lms.config.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final UserService userService;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt, UserService userService) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.userService = userService;
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
    public record ForgotPasswordRequest(@NotBlank @Email String email) {}

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest req) {
        User user = users.findByEmailIgnoreCase(req.email().trim())
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        if (!user.isActive() || !encoder.matches(req.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Invalid email or password");
        }
        List<String> roles = user.getRoles().stream().map(Role::getName).toList();
        String token = jwt.generate(user.getEmail(), roles);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", token);
        body.put("email", user.getEmail());
        body.put("firstName", user.getFirstName() == null ? "" : user.getFirstName());
        body.put("lastName", user.getLastName() == null ? "" : user.getLastName());
        body.put("roles", roles);
        body.put("mustChangePassword", user.isMustChangePassword());
        return body;
    }

    /**
     * Always returns the same success payload (does not reveal whether the email exists).
     * Stage: resets to 123456. Prod: emails a random temp password.
     */
    @PostMapping("/forgot-password")
    public Map<String, Object> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        userService.forgotPassword(req.email());
        return Map.of(
                "status", "ok",
                "message", "If an account exists for that email, a temporary password has been issued. "
                        + "Check your email, then sign in and set a new password."
        );
    }
}
