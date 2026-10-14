package com.keriskit.component;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KerisComponentRepository
        extends JpaRepository<KerisComponent, Long> {

    Optional<KerisComponent> findBySlug(String slug);
}