package com.pradeep.finance.transaction;

import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class MerchantNormalizer {
    public String normalize(String description) {
        if (description == null || description.isBlank()) return "Unknown merchant";
        String value = description.toUpperCase(Locale.ROOT)
                .replaceAll("\\b(?:APL ?PAY|APPLE PAY|GOOGLE PAY|DEBIT|CREDIT|PURCHASE|WITHDRAWAL)\\b", " ")
                .replaceAll("\\b(?:REF|CONFIRMATION|TRACE|ID|#)\\s*[A-Z0-9-]+", " ")
                .replaceAll("\\b\\d{4,}\\b", " ")
                .replaceAll("\\b(?:MERCHANDISE|WEB|ONLINE)\\b", " ")
                .replaceAll("[^A-Z ]", " ").replaceAll("\\s+", " ").trim();
        if (value.contains("PATEL BROTHERS")) return "Patel Brothers";
        if (value.contains("ASIAN HALAL MEAT")) return "Asian Halal Meat";
        if (value.contains("WALMART")) return "Walmart";
        if (value.contains("COSTCO GAS")) return "Costco Gas";
        if (value.contains("UBER EATS")) return "Uber Eats";
        if (value.contains("FRONTIER")) return "Frontier";
        if (value.contains("PLANET FITNESS")) return "Planet Fitness";
        if (value.contains("T MOBILE")) return "T-Mobile";
        if (value.contains("ROBINHOOD")) return "Robinhood";
        if (value.contains("BOTTLE KING")) return "Bottle King";
        return titleCase(value.length() > 48 ? value.substring(0, 48).trim() : value);
    }

    public String key(String description) { return normalize(description).toLowerCase(Locale.ROOT); }

    private String titleCase(String value) {
        StringBuilder result = new StringBuilder();
        for (String word : value.toLowerCase(Locale.ROOT).split(" ")) {
            if (!word.isBlank()) result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
        }
        return result.toString().trim();
    }
}
