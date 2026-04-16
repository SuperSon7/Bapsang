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
     * 게시글에 좋아요를 추가하고 캐시 count 를 증가시킵니다.
     */
    @Transactional
    public void likePost(User user, String postId){
        UserPostLikeId likeId = new UserPostLikeId(user.getId(), postId);
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
        log.info("좋아요 추가 처리중 {}", user.getId());

        if (likeRepository.existsById(likeId)) {
            return;
        }

        likeRepository.save(new Like(user, post));
        likeCountCache.increment(postId);
    }

    /**
     * 게시글의 좋아요를 취소하고 캐시 count 를 감소시킵니다.
     */
    @Transactional
    public void unlikePost(User user, String postId){
        UserPostLikeId likeId = new UserPostLikeId(user.getId(), postId);
        postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
        log.info("좋아요 취소 처리중 {}", user.getId());

        if (!likeRepository.existsById(likeId)) {
            return;
        }

        likeRepository.deleteById(likeId);
        likeCountCache.decrement(postId);
    }

    /**
     * Redis에서 좋아요수를 조회하고 없다면 DB에서 로드합니다.
     */
    @Transactional
    public Integer getLikeCount(String postId){
        return likeCountCache.getLikeCount(postId);
    }
}
