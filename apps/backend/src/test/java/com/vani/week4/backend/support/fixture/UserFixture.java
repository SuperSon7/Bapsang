package com.vani.week4.backend.support.fixture;

import com.vani.week4.backend.user.entity.User;

public class UserFixture {
    private UserFixture() {}

    public static User user() {
        return User.createUser("user-1","바닐라",null);
    }
}