package com.vani.week4.backend.interaction;

import com.vani.week4.backend.global.CurrentUser;
import com.vani.week4.backend.interaction.service.LikeService;
import com.vani.week4.backend.user.entity.User;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * @author vani
 * @since 10/15/25
 */
@RestController
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
@RequestMapping("api/v1/posts/{postId}/likes")
public class LikeController {
    private final LikeService likeService;

    /**
     * 게시글에 좋아요를 추가합니다.
     */
    @PutMapping
    public ResponseEntity<Void> likePost(
            @CurrentUser User user,
            @PathVariable String postId) {
        likeService.likePost(user, postId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 게시글 좋아요를 취소합니다.
     */
    @DeleteMapping
    public ResponseEntity<Void> unlikePost(
            @CurrentUser User user,
            @PathVariable String postId) {
        likeService.unlikePost(user, postId);
        return ResponseEntity.noContent().build();
    }
}
