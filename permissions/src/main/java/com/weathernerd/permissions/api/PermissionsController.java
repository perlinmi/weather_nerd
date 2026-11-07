package com.weathernerd.permissions.api;

import com.weathernerd.permissions.service.PermissionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/permissions")
public class PermissionsController {
    private final PermissionService permissionService;

    public PermissionsController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @PostMapping("/is_granted")
    public ResponseEntity<?> isGranted(
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        if (permissionService.isGranted(userAgent)) {
            return ResponseEntity.ok().build();
        }

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PermissionDeniedResponse("Permission denied"));
    }

    public record PermissionDeniedResponse(String error) {
    }
}
