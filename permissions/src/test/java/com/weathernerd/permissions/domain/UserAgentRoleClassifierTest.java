package com.weathernerd.permissions.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class UserAgentRoleClassifierTest {
    private final UserAgentRoleClassifier classifier = new UserAgentRoleClassifier();

    @ParameterizedTest
    @ValueSource(strings = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/1.0 Safari/537.36"
    })
    void classifiesGoogleChromeUserAgentsAsChrome(String userAgent) {
        assertEquals(BrowserRole.CHROME, classifier.classify(userAgent));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
    })
    void classifiesSafariUserAgentsAsSafari(String userAgent) {
        assertEquals(BrowserRole.SAFARI, classifier.classify(userAgent));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36 Edg/125.0.0.0",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36 OPR/110.0.0.0",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:124.0) Gecko/20100101 Firefox/124.0",
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/125.0.0.0 SamsungBrowser/25.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/125.0.0.0 Vivaldi/6.7 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/125.0.0.0 YaBrowser/24.6 Safari/537.36"
    })
    void classifiesKnownAlternateBrowsersAsOther(String userAgent) {
        assertEquals(BrowserRole.OTHER, classifier.classify(userAgent));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",
            "   ",
            "UnknownBrowser/1.0",
            "Mozilla/5.0 Chrome/not-a-version Safari/537.36",
            "Mozilla/5.0 Safari/605.1.15",
            "Mozilla/5.0\nChrome/125.0 Safari/537.36"
    })
    void classifiesMissingMalformedAndUnknownUserAgentsAsOther(String userAgent) {
        assertEquals(BrowserRole.OTHER, classifier.classify(userAgent));
    }
}
