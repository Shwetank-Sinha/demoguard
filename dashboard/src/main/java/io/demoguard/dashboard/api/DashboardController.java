package io.demoguard.dashboard.api;

import io.demoguard.dashboard.api.DashboardDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class DashboardController {
    private final KubernetesDashboardService service;

    public DashboardController(KubernetesDashboardService service) { this.service = service; }

    @GetMapping("/namespaces")
    public List<NamespaceDto> namespaces() { return service.namespaces(); }

    @GetMapping("/demopolicies")
    public List<DemoPolicyDto> policies(@RequestParam String namespace) { return service.policies(namespace); }

    @GetMapping("/demoreadinesses")
    public List<DemoReadinessDto> readinesses(@RequestParam String namespace) { return service.readinesses(namespace); }

    @GetMapping("/demopolicies/{namespace}/{name}/preflight")
    public PreflightDto preflight(@PathVariable String namespace, @PathVariable String name) {
        return service.preflight(namespace, name);
    }

    @PostMapping("/demopolicies/{namespace}/{name}/refresh")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RefreshResponse refresh(@PathVariable String namespace, @PathVariable String name) {
        return service.refresh(namespace, name);
    }
}
