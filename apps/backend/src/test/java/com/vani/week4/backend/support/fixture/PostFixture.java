package com.vani.week4.backend.support.fixture;

import com.vani.week4.backend.post.entity.Post;
import com.vani.week4.backend.user.entity.User;

public final class PostFixture {
    private PostFixture() {}

    public static Post post(User user) {
        return post("post-1", user);
    }

    public static Post post(String id, User user) {
        return Post.builder()
                .id(id)
                .user(user)
                .title("테스트 게시글")
                .build();
    }
}