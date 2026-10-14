package com.keriskit.component;

public record KerisComponentResponse(
        Long id,
        String slug,
        String name,
        String description
) {

    public static KerisComponentResponse from(
            KerisComponent component) {

        return new KerisComponentResponse(
                component.getId(),
                component.getSlug(),
                component.getName(),
                component.getDescription()
        );
    }
}