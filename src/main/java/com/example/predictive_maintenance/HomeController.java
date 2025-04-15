package com.example.predictive_maintenance;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


public class HomeController {
    @GetMapping("/")
    public String home() {
        return "hello world";
    }
}