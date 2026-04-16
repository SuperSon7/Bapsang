package com.vani.week4.backend.support.fixture;

import com.vani.week4.backend.user.entity.User;

public class UserFixture {
    private UserFixture() {}

    public static User user() {
        return user("user-1", "바닐라", null);
    }

    public static User user(String id, String nickname) {
        return user(id, nickname, null);
    }

    public static User user(String id, String nickname, String profileImageKey) {
        return User.createUser(id, nickname, profileImageKey);
    }
}
