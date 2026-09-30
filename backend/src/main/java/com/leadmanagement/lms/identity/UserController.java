package com.leadmanagement.lms.identity;

import com.leadmanagement.lms.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserService userService;
    private final CurrentUser currentUser;

    public UserController(UserService userService, CurrentUser currentUser) {
        this.userService = userService;
        this.currentUser = currentUser;
    }

    public record CreateStaffRequest(
            @NotBlank @Email String email,
            String firstName,
            String lastName,
            String password,        // optional; generated + emailed if blank
            @NotBlank String role,
            String managerId) {}    // optional; reporting line

    public record SetManagerRequest(String managerId) {}
    public record SetCompanyRequest(String companyId) {}
    public record SetPeerGroupRequest(String peerGroupId) {}
    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {}

    /** Current authenticated user. */
    @GetMapping("/me")
    public UserService.UserView me() {
        return UserService.toView(currentUser.get());
    }

    /** Change your own password. Any signed-in user. */
    @PostMapping("/me/password")
    public Map<String, Object> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        userService.changePassword(currentUser.email(), req.currentPassword(), req.newPassword());
        return Map.of("status", "ok");
    }

    /** Add a staff member (admin / sales / marketing). SUPER_ADMIN or ADMIN. */
    @PostMapping("/admin/users")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public UserService.UserView createStaff(@Valid @RequestBody CreateStaffRequest req) {
        boolean isSuperAdmin = currentUser.get().getRoles().stream()
                .anyMatch(r -> r.getName().equals("SUPER_ADMIN"));
        return userService.createStaff(req.email(), req.firstName(), req.lastName(),
                req.password(), req.role(), req.managerId(), isSuperAdmin);
    }

    /** Staff list scoped to the caller's hierarchy. Super admin sees all; others see their chain + subtree. */
    @GetMapping("/admin/users")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public List<UserService.UserView> list() {
        var u = currentUser.get();
        boolean isSuper = u.getRoles().stream().anyMatch(r -> r.getName().equals("SUPER_ADMIN"));
        return userService.list(u.getId(), isSuper);
    }

    /** Set or clear a user's manager (reporting line). SUPER_ADMIN or ADMIN. */
    @PatchMapping("/admin/users/{id}/manager")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public UserService.UserView setManager(@PathVariable UUID id, @RequestBody SetManagerRequest req) {
        return userService.setManager(id, req.managerId());
    }

    /** Assign or clear a user's company grouping. Super admin only. */
    @PatchMapping("/admin/users/{id}/company")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public UserService.UserView setCompany(@PathVariable UUID id, @RequestBody SetCompanyRequest req) {
        return userService.setCompany(id, req.companyId());
    }

    /**
     * Assign or clear a user's peer group (explicit peer lead sharing).
     * Super admin: anyone. Admin: self + people who report to them.
     */
    @PatchMapping("/admin/users/{id}/peer-group")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public UserService.UserView setPeerGroup(@PathVariable UUID id, @RequestBody SetPeerGroupRequest req) {
        return userService.setPeerGroup(currentUser.email(), id, req.peerGroupId());
    }

    /** Remove (deactivate) a staff member. Super admin removes anyone; others only their reports. */
    @DeleteMapping("/admin/users/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','SALES_MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeUser(@PathVariable UUID id) {
        userService.removeUser(currentUser.email(), id);
    }
}
