package com.vani.week4.backend.support.fixture;

import com.vani.week4.backend.comment.entity.Comment;
import com.vani.week4.backend.post.entity.Post;
import com.vani.week4.backend.user.entity.User;

public final class CommentFixture {
    private CommentFixture() {}

    public static Comment rootComment(Post post, User user) {
        return Comment.builder()
                .id("comment-1")
                .post(post)
                .user(user)
                .parentId(null)
                .depth(0)
                .commentGroup("comment-1")
                .content("댓글")
                .build();
    }
}
