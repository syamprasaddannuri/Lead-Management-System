package com.leadmanagement.lms.identity;

import com.leadmanagement.lms.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Explicit peer groups for shared lead visibility (admin + super admin). */
@RestController
@RequestMapping("/api/admin/peer-groups")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
public class PeerGroupController {

    private final UserService userService;
    private final CurrentUser currentUser;

    public PeerGroupController(UserService userService, CurrentUser currentUser) {
        this.userService = userService;
        this.currentUser = currentUser;
    }

    public record PeerGroupRequest(@NotBlank String name) {}

    @GetMapping
    public List<UserService.PeerGroupView> list() {
        return userService.listPeerGroups(currentUser.email());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserService.PeerGroupView create(@Valid @RequestBody PeerGroupRequest req) {
        return userService.createPeerGroup(currentUser.email(), req.name());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        userService.deletePeerGroup(currentUser.email(), id);
    }
}
