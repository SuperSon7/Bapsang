package com.vani.week4.backend.interaction.service;

import com.vani.week4.backend.interaction.repository.LikeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LikeCountCache} 의 캐시 복구 및 증감 규칙을 검증합니다.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class LikeCountCacheTest {
    private static final String POST_ID = "post-1";
    private static final String REDIS_KEY = LikeCountCache.LIKE_COUNT_KEY_PREFIX + POST_ID;

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private RedisTemplate<String, Object> likesRedisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private LikeCountCache likeCountCache;

    @BeforeEach
    void setUp() {
        likeCountCache = new LikeCountCache(likeRepository, likesRedisTemplate);
        when(likesRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    /**
     * 캐시 값이 없으면 DB count 를 읽어 Redis 에 복구해야 합니다.
     */
    @Test
    @DisplayName("좋아요 수 조회 시 캐시 미스면 DB 값을 캐시에 복구한다")
    void getLikeCount_RestoresCountFromDatabaseOnCacheMiss() {
        when(valueOperations.get(REDIS_KEY)).thenReturn(null);
        when(likeRepository.countByUserPostLikeIdPostId(POST_ID)).thenReturn(7);

        int likeCount = likeCountCache.getLikeCount(POST_ID);

        assertThat(likeCount).isEqualTo(7);
        verify(valueOperations).set(REDIS_KEY, 7);
    }

    /**
     * 증가 연산은 캐시 miss 상태에서도 DB 기준 count 에서 이어져야 합니다.
     */
    @Test
    @DisplayName("좋아요 추가 시 캐시 미스면 DB count 기준으로 증가한다")
    void increment_UsesDatabaseCountWhenCacheMisses() {
        when(valueOperations.get(REDIS_KEY)).thenReturn(null);
        when(likeRepository.countByUserPostLikeIdPostId(POST_ID)).thenReturn(4);

        int updatedLikeCount = likeCountCache.increment(POST_ID);

        assertThat(updatedLikeCount).isEqualTo(5);
        verify(valueOperations).set(REDIS_KEY, 5);
    }

    /**
     * 감소 연산은 잘못된 음수 count 를 만들지 않아야 합니다.
     */
    @Test
    @DisplayName("좋아요 취소 시 count는 0 아래로 내려가지 않는다")
    void decrement_DoesNotGoBelowZero() {
        when(valueOperations.get(REDIS_KEY)).thenReturn(0);

        int updatedLikeCount = likeCountCache.decrement(POST_ID);

        assertThat(updatedLikeCount).isEqualTo(0);
        verify(valueOperations).set(REDIS_KEY, 0);
    }
}
