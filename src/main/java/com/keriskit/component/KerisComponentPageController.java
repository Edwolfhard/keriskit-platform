package com.keriskit.component;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class KerisComponentPageController {

    private final KerisComponentService service;

    public KerisComponentPageController(
            KerisComponentService service) {
        this.service = service;
    }

    @GetMapping("/components/{slug}")
    public String showComponent(
            @PathVariable("slug") String slug,
            Model model) {

        model.addAttribute(
                "component",
                KerisComponentResponse.from(
                        service.getComponent(slug)
                )
        );

        return "component-detail";
    }
}