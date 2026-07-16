package com.abdulrafy.backend.notification.controller;

import com.abdulrafy.backend.common.security.AuthenticatedUser;
import com.abdulrafy.backend.notification.dto.NotificationResponse;
import com.abdulrafy.backend.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notification management")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "Get paginated notification history")
    public ResponseEntity<Page<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(notificationService.getNotifications(user.id(), page, size));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark a notification as read")
    public ResponseEntity<NotificationResponse> markAsRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(notificationService.markAsRead(id, user.id()));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get count of unread notifications")
    public ResponseEntity<Map<String, Long>> unreadCount(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(Map.of("count", notificationService.countUnread(user.id())));
    }
}
