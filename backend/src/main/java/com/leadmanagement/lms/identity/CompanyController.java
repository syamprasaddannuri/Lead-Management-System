package com.leadmanagement.lms.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Super-admin grouping of admins into companies that share lead visibility. */
@RestController
@RequestMapping("/api/admin/companies")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class CompanyController {

    private final UserService userService;

    public CompanyController(UserService userService) { this.userService = userService; }

    public record CompanyRequest(@NotBlank String name) {}

    @GetMapping
    public List<UserService.CompanyView> list() { return userService.listCompanies(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserService.CompanyView create(@Valid @RequestBody CompanyRequest req) {
        return userService.createCompany(req.name());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { userService.deleteCompany(id); }
}
