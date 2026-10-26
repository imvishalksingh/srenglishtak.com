package com.readiq.backend.api;
import com.readiq.backend.service.ReportingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final ReportingService service;
    public AdminController(ReportingService service){this.service=service;}
    @GetMapping("/stats")public Object stats(){return service.stats();}
    @GetMapping("/users")public Object users(){return service.users();}
    @GetMapping("/users/{userId}/progress")public Object progress(@PathVariable UUID userId){return service.userProgress(userId);}
}
