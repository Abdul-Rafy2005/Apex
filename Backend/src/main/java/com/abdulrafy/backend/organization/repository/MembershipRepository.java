package com.abdulrafy.backend.organization.repository;

import com.abdulrafy.backend.organization.entity.Membership;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {
    List<Membership> findByOrganizationId(UUID organizationId);
    List<Membership> findByUserId(UUID userId);
    Optional<Membership> findByUserIdAndOrganizationId(UUID userId, UUID organizationId);
    boolean existsByUserIdAndOrganizationId(UUID userId, UUID organizationId);
}
