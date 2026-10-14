package com.keriskit.component;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/components")
public class KerisComponentController {

    private final KerisComponentService service;

    public KerisComponentController(KerisComponentService service) {
        this.service = service;
    }

    @GetMapping
    public List<KerisComponentResponse> getComponents() {
        return service.getComponents()
                .stream()
                .map(KerisComponentResponse::from)
                .toList();
    }

    @GetMapping("/{slug}")
    public KerisComponentResponse getComponent(
            @PathVariable("slug") String slug) {
        return KerisComponentResponse.from(
                service.getComponent(slug)
        );
    }
}