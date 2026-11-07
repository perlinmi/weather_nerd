package com.weathernerd.permissions.service;

import com.weathernerd.permissions.domain.UserAgentRoleClassifier;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {
    private final UserAgentRoleClassifier classifier;
    private final PermissionPolicy policy;

    public PermissionService(UserAgentRoleClassifier classifier, PermissionPolicy policy) {
        this.classifier = classifier;
        this.policy = policy;
    }

    public boolean isGranted(String userAgent) {
        return policy.isGranted(classifier.classify(userAgent));
    }
}
