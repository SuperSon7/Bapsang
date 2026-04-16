package com.vani.week4.backend.post.batch;

import com.vani.week4.backend.interaction.service.LikeCountCache;
import com.vani.week4.backend.post.entity.Post;
import com.vani.week4.backend.post.repository.PostRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Redis 에 쌓인 좋아요 count 를 게시글 엔티티에 반영하는 동기화 컴포넌트입니다.
 */
@Slf4j
@Component
public class LikeCountSynchronizer {
    private final LikeCountCache likeCountCache;
    private final PostRepository postRepository;

    public LikeCountSynchronizer(LikeCountCache likeCountCache, PostRepository postRepository) {
        this.likeCountCache = likeCountCache;
        this.postRepository = postRepository;
    }

    /**
     * 현재 캐시된 좋아요 수를 순회하며 DB에 반영한 건수를 반환합니다.
     */
    @Transactional
    public int syncAll() {
        int syncCount = 0;

        for (String key : likeCountCache.findLikeCountKeys()) {
            try {
                Integer likeCount = likeCountCache.getCachedCountByKey(key);
                if (likeCount == null) {
                    continue;
                }

                String postId = likeCountCache.extractPostId(key);
                // 삭제된 게시글 키가 남아 있을 수 있어, 없는 엔티티는 건너뜁니다.
                Post post = postRepository.findById(postId).orElse(null);
                if (post == null) {
                    log.warn("좋아요 수 동기화 대상 게시글이 없습니다. postId: {}", postId);
                    continue;
                }

                post.updateLikeCount(likeCount);
                syncCount++;
            } catch (NumberFormatException e) {
                log.error("Redis 값을 숫자로 변환 실패 - key: {}", key, e);
            } catch (Exception e) {
                log.error("좋아요 수 동기화 실패 - key: {}, error: {}", key, e.getMessage());
            }
        }

        return syncCount;
    }
}
