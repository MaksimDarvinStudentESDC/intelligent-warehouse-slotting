package com.warehouse.slotting.dto.auth;

import com.warehouse.slotting.entity.User;
import com.warehouse.slotting.entity.UserRole;

public record UserResponse(
        Long id,
        String login,
        String fullName,
        UserRole role
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getLogin(),
                user.getFullName(),
                user.getRole()
        );
    }
}