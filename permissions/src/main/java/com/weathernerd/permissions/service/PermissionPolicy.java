package com.weathernerd.permissions.service;

import com.weathernerd.permissions.domain.BrowserRole;
import org.springframework.stereotype.Component;

@Component
public class PermissionPolicy {
    public boolean isGranted(BrowserRole role) {
        return role == BrowserRole.SAFARI || role == BrowserRole.CHROME;
    }
}
