package com.abdulrafy.backend.auth.mapper;

import com.abdulrafy.backend.auth.dto.UserResponse;
import com.abdulrafy.backend.auth.entity.User;

public final class UserMapper {

    private UserMapper() {}

    public static UserResponse toResponse(User user) {
        return new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getDisplayName(),
            user.getRole(),
            user.getCreatedAt()
        );
    }
}
