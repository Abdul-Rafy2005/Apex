package com.abdulrafy.backend.organization.repository;

import com.abdulrafy.backend.organization.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
}
