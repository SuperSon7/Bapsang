package com.vani.week4.backend.interaction.service;

import com.vani.week4.backend.interaction.repository.LikeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;

/**
 * 좋아요 count 캐시의 조회, 복구, 증감 규칙을 한 곳에서 관리합니다.
 */
@Slf4j
@Component
public class LikeCountCache {
    public static final String LIKE_COUNT_KEY_PREFIX = "post:like:";

    private final LikeRepository likeRepository;
    private final RedisTemplate<String, Object> likesRedisTemplate;

    public LikeCountCache(
            LikeRepository likeRepository,
            @Qualifier("likesRedisTemplate") RedisTemplate<String, Object> likesRedisTemplate
    ) {
        this.likeRepository = likeRepository;
        this.likesRedisTemplate = likesRedisTemplate;
    }

    /**
     * 캐시된 좋아요 수를 조회하고, 값이 없거나 깨졌다면 DB 값으로 복구합니다.
     */
    public int getLikeCount(String postId) {
        String redisKey = buildKey(postId);
        try {
            Integer cachedCount = getCachedCountByKey(redisKey);
            if (cachedCount != null) {
                return cachedCount;
            }
        } catch (NumberFormatException e) {
            log.warn("좋아요 캐시 파싱 실패. postId: {}", postId, e);
        }

        return restoreCountFromDatabase(postId);
    }

    /**
     * 좋아요 수를 1 증가시키고 캐시에 반영합니다.
     */
    public int increment(String postId) {
        return adjustCount(postId, 1);
    }

    /**
     * 좋아요 수를 1 감소시키고 캐시에 반영합니다.
     */
    public int decrement(String postId) {
        return adjustCount(postId, -1);
    }

    /**
     * 동기화 대상이 되는 좋아요 count 키 목록을 조회합니다.
     */
    public Set<String> findLikeCountKeys() {
        Set<String> keys = likesRedisTemplate.keys(LIKE_COUNT_KEY_PREFIX + "*");
        return keys == null ? Collections.emptySet() : keys;
    }

    /**
     * Redis key 에 저장된 count 값을 숫자로 읽습니다.
     */
    public Integer getCachedCountByKey(String redisKey) {
        Object value = likesRedisTemplate.opsForValue().get(redisKey);
        if (value == null) {
            return null;
        }
        return Integer.parseInt(value.toString());
    }

    /**
     * Redis key 에서 게시글 id 를 추출합니다.
     */
    public String extractPostId(String redisKey) {
        return redisKey.substring(LIKE_COUNT_KEY_PREFIX.length());
    }

    /**
     * 캐시 miss 나 파싱 실패를 복구한 뒤 증감 연산을 수행합니다.
     */
    private int adjustCount(String postId, int delta) {
        String redisKey = buildKey(postId);
        int currentCount;

        try {
            Integer cachedCount = getCachedCountByKey(redisKey);
            currentCount = cachedCount != null ? cachedCount : loadCountFromDatabase(postId);
        } catch (NumberFormatException e) {
            log.warn("좋아요 캐시 파싱 실패. DB 값으로 복구합니다. postId: {}", postId, e);
            currentCount = loadCountFromDatabase(postId);
        }

        // decrement 가 먼저 와도 음수 count 가 되지 않도록 하한을 고정합니다.
        int updatedCount = Math.max(0, currentCount + delta);
        likesRedisTemplate.opsForValue().set(redisKey, updatedCount);
        return updatedCount;
    }

    /**
     * DB 값을 다시 읽어 캐시에 복구한 뒤 반환합니다.
     */
    private int restoreCountFromDatabase(String postId) {
        int count = loadCountFromDatabase(postId);
        likesRedisTemplate.opsForValue().set(buildKey(postId), count);
        return count;
    }

    /**
     * 게시글의 좋아요 수를 DB에서 조회합니다.
     */
    private int loadCountFromDatabase(String postId) {
        return likeRepository.countByUserPostLikeIdPostId(postId);
    }

    /**
     * 게시글 id 에 대응하는 Redis key 를 생성합니다.
     */
    private String buildKey(String postId) {
        return LIKE_COUNT_KEY_PREFIX + postId;
    }
}
