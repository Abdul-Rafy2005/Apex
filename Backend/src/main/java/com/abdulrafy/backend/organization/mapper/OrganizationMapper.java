package com.abdulrafy.backend.organization.mapper;

import com.abdulrafy.backend.organization.dto.OrganizationResponse;
import com.abdulrafy.backend.organization.entity.Organization;

public final class OrganizationMapper {

    private OrganizationMapper() {}

    public static OrganizationResponse toResponse(Organization org) {
        return new OrganizationResponse(
            org.getId(),
            org.getName(),
            org.getType(),
            org.getCreatedBy().getId(),
            org.getCreatedAt()
        );
    }
}
