package com.abdulrafy.backend.auth.controller;

import com.abdulrafy.backend.auth.dto.UserProfileResponse;
import com.abdulrafy.backend.auth.service.AuthService;
import com.abdulrafy.backend.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User profile management")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated user's profile and portfolio summary")
    public ResponseEntity<UserProfileResponse> getMe(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(authService.getProfile(user.id()));
    }
}
