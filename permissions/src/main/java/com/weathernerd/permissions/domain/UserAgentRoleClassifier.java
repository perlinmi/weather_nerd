package com.weathernerd.permissions.domain;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class UserAgentRoleClassifier {
    private static final List<String> OTHER_BROWSER_TOKENS = List.of(
            "Edg",
            "EdgA",
            "EdgiOS",
            "Edge",
            "OPR",
            "Opera",
            "Firefox",
            "FxiOS",
            "SamsungBrowser",
            "Vivaldi",
            "YaBrowser");

    private static final Pattern CHROME_TOKEN = versionedToken("Chrome");
    private static final Pattern SAFARI_VERSION_TOKEN = versionedToken("Version");
    private static final Pattern SAFARI_TOKEN = versionedToken("Safari");
    private static final List<Pattern> OTHER_BROWSER_PATTERNS = OTHER_BROWSER_TOKENS.stream()
            .map(UserAgentRoleClassifier::versionedToken)
            .toList();

    public BrowserRole classify(String userAgent) {
        if (userAgent == null || userAgent.isBlank() || containsControlCharacter(userAgent)) {
            return BrowserRole.OTHER;
        }

        if (OTHER_BROWSER_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(userAgent).find())) {
            return BrowserRole.OTHER;
        }

        if (CHROME_TOKEN.matcher(userAgent).find()) {
            return BrowserRole.CHROME;
        }

        if (SAFARI_VERSION_TOKEN.matcher(userAgent).find()
                && SAFARI_TOKEN.matcher(userAgent).find()) {
            return BrowserRole.SAFARI;
        }

        return BrowserRole.OTHER;
    }

    private static Pattern versionedToken(String token) {
        return Pattern.compile(
                "(?i)(?:^|[\\s(;])" + Pattern.quote(token) + "/\\d+(?:\\.\\d+)*(?=$|[\\s;)])");
    }

    private static boolean containsControlCharacter(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }
}
