package com.keriskit.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class StatusController {

    private final JdbcTemplate jdbcTemplate;

    public StatusController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/api/status")
    public Map<String, String> getStatus() {
        return Map.of(
                "application", "KerisKit",
                "status", "running"
        );
    }

    @GetMapping("/api/status/database")
    public Map<String, Object> getDatabaseStatus() {
        Integer result = jdbcTemplate.queryForObject(
                "SELECT 1", Integer.class
        );

        return Map.of(
                "database", "PostgreSQL",
                "status", "connected",
                "result", result
        );
    }
}