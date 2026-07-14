package com.abdulrafy.backend.organization.mapper;

import com.abdulrafy.backend.organization.dto.MembershipResponse;
import com.abdulrafy.backend.organization.entity.Membership;

public final class MembershipMapper {

    private MembershipMapper() {}

    public static MembershipResponse toResponse(Membership membership) {
        return new MembershipResponse(
            membership.getId(),
            membership.getUser().getId(),
            membership.getUser().getEmail(),
            membership.getUser().getDisplayName(),
            membership.getRole(),
            membership.getCreatedAt()
        );
    }
}
