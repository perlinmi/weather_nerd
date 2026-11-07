package com.weathernerd.permissions.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weathernerd.permissions.domain.BrowserRole;
import org.junit.jupiter.api.Test;

class PermissionPolicyTest {
    private final PermissionPolicy policy = new PermissionPolicy();

    @Test
    void grantsSafariAndChrome() {
        assertTrue(policy.isGranted(BrowserRole.SAFARI));
        assertTrue(policy.isGranted(BrowserRole.CHROME));
    }

    @Test
    void deniesOther() {
        assertFalse(policy.isGranted(BrowserRole.OTHER));
    }
}
