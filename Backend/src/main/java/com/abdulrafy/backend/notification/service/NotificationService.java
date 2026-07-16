package com.abdulrafy.backend.notification.service;

import com.abdulrafy.backend.auth.repository.PortfolioRepository;
import com.abdulrafy.backend.common.exception.NotFoundException;
import com.abdulrafy.backend.notification.dto.NotificationResponse;
import com.abdulrafy.backend.notification.entity.Notification;
import com.abdulrafy.backend.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final PortfolioRepository portfolioRepository;

    /**
     * Create a notification. Idempotent: if a notification with the same type and
     * referenceId already exists for this user, returns the existing one without
     * creating a duplicate.
     */
    @Transactional
    public Notification createNotification(UUID userId, String type, String title,
                                           String body, UUID referenceId,
                                           String referenceType) {
        if (referenceId != null &&
            notificationRepository.existsByUserIdAndTypeAndReferenceId(userId, type, referenceId)) {
            log.info("Duplicate notification suppressed: userId={}, type={}, referenceId={}",
                    userId, type, referenceId);
            return notificationRepository.findByUserIdAndTypeAndReferenceId(userId, type, referenceId)
                    .orElseThrow();
        }

        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .body(body)
                .referenceId(referenceId)
                .referenceType(referenceType)
                .build();

        Notification saved = notificationRepository.save(notification);
        log.info("Notification created: id={}, userId={}, type={}", saved.getId(), userId, type);
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotifications(UUID userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        return notificationRepository
                .findByUserIdOrderByReadAtAscCreatedAtDesc(userId, pageRequest)
                .map(this::toResponse);
    }

    @Transactional
    public NotificationResponse markAsRead(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new NotFoundException("Notification not found"));

        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notificationRepository.save(notification);
        }

        return toResponse(notification);
    }

    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getType(),
                n.getTitle(),
                n.getBody(),
                n.getReferenceId(),
                n.getReferenceType(),
                n.getReadAt(),
                n.getCreatedAt()
        );
    }

    public UUID resolveUserIdByPortfolioId(UUID portfolioId) {
        return portfolioRepository.findUserIdByPortfolioId(portfolioId).orElse(null);
    }

    public Optional<Notification> findByUserIdAndTypeAndReferenceId(UUID userId, String type, UUID referenceId) {
        return notificationRepository.findByUserIdAndTypeAndReferenceId(userId, type, referenceId);
    }
}
