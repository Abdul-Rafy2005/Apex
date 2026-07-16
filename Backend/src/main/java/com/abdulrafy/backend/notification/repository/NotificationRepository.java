package com.abdulrafy.backend.notification.repository;

import com.abdulrafy.backend.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdOrderByReadAtAscCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndReadAtIsNull(UUID userId);

    Optional<Notification> findByUserIdAndTypeAndReferenceId(UUID userId, String type, UUID referenceId);

    @Query("SELECT CASE WHEN COUNT(n) > 0 THEN true ELSE false END FROM Notification n " +
           "WHERE n.userId = :userId AND n.type = :type " +
           "AND n.referenceId = :referenceId")
    boolean existsByUserIdAndTypeAndReferenceId(@Param("userId") UUID userId,
                                                 @Param("type") String type,
                                                 @Param("referenceId") UUID referenceId);
}
