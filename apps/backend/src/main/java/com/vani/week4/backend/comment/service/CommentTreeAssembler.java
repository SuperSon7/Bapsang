package com.vani.week4.backend.comment.service;

import com.vani.week4.backend.comment.dto.CommentResponse;
import com.vani.week4.backend.comment.entity.Comment;
import com.vani.week4.backend.infra.S3.S3Service;
import com.vani.week4.backend.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CommentTreeAssembler {
    private static final String DELETED_COMMENT_CONTENT = "삭제된 댓글입니다.";

    private final S3Service s3Service;

    // 루트 댓글 응답에 같은 commentGroup의 대댓글 트리를 붙여 반환한다.
    CommentResponse toCommentWithReplies(Comment comment, List<Comment> replies) {
        List<CommentResponse> replyResponses = buildReplyTree(replies);

        return new CommentResponse(
                comment.getId(),
                comment.getParentId(),
                contentOf(comment),
                comment.getCommentGroup(),
                comment.getCreatedAt(),
                comment.getDepth(),
                toAuthor(comment),
                comment.getCommentStatus(),
                replyResponses,
                false,
                replies.size()
        );
    }

    CommentResponse toCommentResponse(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getParentId(),
                contentOf(comment),
                comment.getCommentGroup(),
                comment.getCreatedAt(),
                comment.getDepth(),
                toAuthor(comment),
                comment.getCommentStatus(),
                new ArrayList<>(),
                false,
                0
        );
    }

    // 조회된 대댓글 목록을 id로 색인한 뒤 parentId를 따라 자식 목록에 연결한다.
    private List<CommentResponse> buildReplyTree(List<Comment> replies) {
        Map<String, CommentResponse> commentMap = new HashMap<>();
        List<CommentResponse> firstLevelReplies = new ArrayList<>();

        for (Comment reply : replies) {
            CommentResponse response = toCommentResponse(reply);
            commentMap.put(reply.getId(), response);
        }

        for (Comment reply : replies) {
            CommentResponse response = commentMap.get(reply.getId());
            if (reply.getDepth() == 1) {
                firstLevelReplies.add(response);
            } else if (reply.getDepth() > 1) {
                CommentResponse parent = commentMap.get(reply.getParentId());
                if (parent != null) {
                    parent.replies().add(response);
                }
            }
        }

        return firstLevelReplies;
    }

    private CommentResponse.Author toAuthor(Comment comment) {
        if (comment.isDeleted()) {
            return null;
        }

        User user = comment.getUser();
        String profileImageKey = user.getProfileImageKey();
        String authorProfileUrl = null;

        if (profileImageKey != null && !profileImageKey.isBlank()) {
            authorProfileUrl = s3Service.createPresignedGetUrl(profileImageKey);
        }

        return new CommentResponse.Author(
                user.getNickname(),
                authorProfileUrl
        );
    }

    private String contentOf(Comment comment) {
        if (comment.isDeleted()) {
            return DELETED_COMMENT_CONTENT;
        }

        return comment.getContent();
    }
}
