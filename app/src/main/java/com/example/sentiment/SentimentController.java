package com.example.sentiment;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class SentimentController {

    @GetMapping("/sentiment")
    public Map<String, String> sentiment(@RequestParam String text) {
        String t = text.toLowerCase();

        // ultra-simple mock "AI" sentiment
        boolean positive = t.contains("good") || t.contains("love") || t.contains("great") || t.contains("like");
        boolean negative = t.contains("bad") || t.contains("hate") || t.contains("terrible") || t.contains("awful");

        String result = "neutral";
        if (positive && !negative) result = "positive";
        if (negative && !positive) result = "negative";

        return Map.of("sentiment", result);
    }
}
