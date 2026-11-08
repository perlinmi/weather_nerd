package com.weathernerd.permissions.service;

import com.weathernerd.permissions.domain.BrowserRole;
import org.springframework.stereotype.Component;

@Component
public class PermissionPolicy {
    public boolean isGranted(BrowserRole role, String endpoint) {
        if (endpoint == null) {
            return false;
        }

        return switch (endpoint) {
            case "forecast" -> role == BrowserRole.SAFARI || role == BrowserRole.CHROME;
            case "nearby" -> role == BrowserRole.CHROME;
            default -> false;
        };
    }
}
