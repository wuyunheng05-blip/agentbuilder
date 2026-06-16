package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.spi.SensitiveDataMasker;

import java.util.List;
import java.util.regex.Pattern;

public class RegexSensitiveDataMasker implements SensitiveDataMasker {

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("1[3-9]\\d{9}"),
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
            Pattern.compile("\\d{17}[\\dXx]"),
            Pattern.compile("\\d{16,19}"),
            Pattern.compile("\\b[A-Za-z0-9]{32,}\\b"));

    @Override
    public String mask(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        String masked = input;
        for (Pattern pattern : PATTERNS) {
            masked = pattern.matcher(masked).replaceAll("[MASKED]");
        }
        return masked;
    }
}
