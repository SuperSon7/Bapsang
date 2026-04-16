package com.vani.week4.backend.interaction.service;

import com.vani.week4.backend.global.ErrorCode;
import com.vani.week4.backend.global.exception.PostNotFoundException;
import com.vani.week4.backend.interaction.entity.Like;
import com.vani.week4.backend.interaction.entity.UserPostLikeId;
import com.vani.week4.backend.interaction.repository.LikeRepository;
import com.vani.week4.backend.post.entity.Post;
import com.vani.week4.backend.post.repository.PostRepository;
import com.vani.week4.backend.user.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좋아요 관련 로직을 처리하는 클래스
 * Redis를 사용하여 좋아요 수를 캐싱, 스케줄러로 DB와 동기화
 * @author vani
 * @since 10/15/25
 */
@Slf4j
@Service
public class LikeService {
    private final LikeRepository likeRepository;
    private final PostRepository postRepository;
    private final LikeCountCache likeCountCache;

    protected LikeService(
            LikeRepository likeRepository,
            PostRepository postRepository,
            LikeCountCache likeCountCache
        ) {
        this.likeRepository = likeRepository;
        this.postRepository = postRepository;
        this.likeCountCache = likeCountCache;
    }

    /**
     * 게시글의 좋아요를 토글
     * 이미 좋아요 했다면 취소, 좋아요하지 않았다면 좋아요합니다.
     * 수는 Redis에 캐싱, 스캐줄러를 통해 DB와 동기화
     * */
    @Transactional
    public void toggleLike(User user, String postId){

        String userId = user.getId();
        UserPostLikeId likeId = new UserPostLikeId(userId, postId);

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
        log.info("좋아요 처리중 {} ", userId);

        if (likeRepository.existsById(likeId)){
            likeRepository.deleteById(likeId);
            likeCountCache.decrement(postId);
        } else {
            likeRepository.save(new Like(user, post));
            likeCountCache.increment(postId);
        }
    }

    /**
     * Redis에서 좋아요수를 조회하고 없다면 DB에서 로드합니다.
     */
    @Transactional
    public Integer getLikeCount(String postId){
        return likeCountCache.getLikeCount(postId);
    }
}
