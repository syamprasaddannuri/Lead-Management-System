package com.leadmanagement.lms.email;

import com.leadmanagement.lms.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/email-templates")
public class EmailTemplateController {

    private static final String SELECT = "hasAnyRole('SUPER_ADMIN','ADMIN','SALES_MANAGER','SALES_REP','MARKETING')";
    private static final String MANAGE = "hasAnyRole('SUPER_ADMIN','ADMIN','SALES_MANAGER')";

    private final EmailTemplateService service;
    private final CurrentUser currentUser;

    public EmailTemplateController(EmailTemplateService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    public record TemplateRequest(@NotBlank String name, @NotBlank String subject, @NotBlank String body,
                                  String stage, Boolean active) {}

    /**
     * Active templates for the lead email composer.
     * Filtered by org visibility: SELF | UP | DOWN | PEERS (+ system templates for everyone).
     */
    @GetMapping
    @PreAuthorize(SELECT)
    public List<EmailTemplateService.TemplateView> active() {
        return service.listActive(currentUser.get());
    }

    /** All templates including inactive, for the manage page (managers+), same visibility sphere. */
    @GetMapping("/manage")
    @PreAuthorize(MANAGE)
    public List<EmailTemplateService.TemplateView> all() {
        return service.listAll(currentUser.get());
    }

    @PostMapping
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    public EmailTemplateService.TemplateView create(@Valid @RequestBody TemplateRequest req) {
        return service.create(req.name(), req.subject(), req.body(), req.stage(), req.active(), currentUser.get());
    }

    @PutMapping("/{id}")
    @PreAuthorize(MANAGE)
    public EmailTemplateService.TemplateView update(@PathVariable UUID id, @Valid @RequestBody TemplateRequest req) {
        return service.update(id, req.name(), req.subject(), req.body(), req.stage(), req.active(), currentUser.get());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id, currentUser.get());
    }
}
