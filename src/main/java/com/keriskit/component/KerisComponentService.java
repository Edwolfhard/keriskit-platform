package com.keriskit.component;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class KerisComponentService {

    private final KerisComponentRepository repository;

    public KerisComponentService(KerisComponentRepository repository) {
        this.repository = repository;
    }

    public List<KerisComponent> getComponents() {
        return repository.findAll(Sort.by("id"));
    }

    public KerisComponent getComponent(String slug) {
        return repository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Component not found"
                ));
    }
}