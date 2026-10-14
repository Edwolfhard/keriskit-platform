package com.keriskit.component;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/components")
public class KerisComponentController {

    private final KerisComponentRepository repository;

    public KerisComponentController(KerisComponentRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<KerisComponent> getComponents() {
        return repository.findAll(Sort.by("id"));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<KerisComponent> getComponent(
            @PathVariable("slug") String slug) {

        return repository.findBySlug(slug)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}