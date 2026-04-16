package com.vani.week4.backend.post.batch;

import com.vani.week4.backend.interaction.service.LikeCountCache;
import com.vani.week4.backend.post.entity.Post;
import com.vani.week4.backend.post.repository.PostRepository;
import com.vani.week4.backend.support.fixture.PostFixture;
import com.vani.week4.backend.support.fixture.UserFixture;
import com.vani.week4.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LikeCountSynchronizer} 의 정상 및 예외 동기화 경로를 검증합니다.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class LikeCountSynchronizerTest {
    @Mock
    private LikeCountCache likeCountCache;

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private LikeCountSynchronizer likeCountSynchronizer;

    private Post post;

    @BeforeEach
    void setUp() {
        User user = UserFixture.user("user-1", "tester");
        post = PostFixture.post("post-1", user);
    }

    /**
     * 정상 key 는 게시글 엔티티의 likeCount 에 반영되어야 합니다.
     */
    @Test
    @DisplayName("동기화 시 캐시에 있는 좋아요 수를 게시글에 반영한다")
    void syncAll_UpdatesPostLikeCount() {
        String key = LikeCountCache.LIKE_COUNT_KEY_PREFIX + "post-1";
        when(likeCountCache.findLikeCountKeys()).thenReturn(Set.of(key));
        when(likeCountCache.getCachedCountByKey(key)).thenReturn(9);
        when(likeCountCache.extractPostId(key)).thenReturn("post-1");
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));

        int syncCount = likeCountSynchronizer.syncAll();

        assertThat(syncCount).isEqualTo(1);
        assertThat(post.getLikeCount()).isEqualTo(9);
    }

    /**
     * null 값, 파싱 실패, 없는 게시글은 전체 sync 를 깨지 않고 건너뛰어야 합니다.
     */
    @Test
    @DisplayName("동기화 시 null 값, 잘못된 값, 없는 게시글은 건너뛴다")
    void syncAll_SkipsInvalidEntries() {
        String nullValueKey = LikeCountCache.LIKE_COUNT_KEY_PREFIX + "post-1";
        String invalidValueKey = LikeCountCache.LIKE_COUNT_KEY_PREFIX + "post-2";
        String missingPostKey = LikeCountCache.LIKE_COUNT_KEY_PREFIX + "post-3";
        Set<String> keys = new LinkedHashSet<>();
        keys.add(nullValueKey);
        keys.add(invalidValueKey);
        keys.add(missingPostKey);

        when(likeCountCache.findLikeCountKeys()).thenReturn(keys);
        when(likeCountCache.getCachedCountByKey(nullValueKey)).thenReturn(null);
        when(likeCountCache.getCachedCountByKey(invalidValueKey)).thenThrow(new NumberFormatException("bad"));
        when(likeCountCache.getCachedCountByKey(missingPostKey)).thenReturn(3);
        when(likeCountCache.extractPostId(missingPostKey)).thenReturn("post-3");
        when(postRepository.findById("post-3")).thenReturn(Optional.empty());

        int syncCount = likeCountSynchronizer.syncAll();

        assertThat(syncCount).isZero();
        verify(postRepository).findById("post-3");
    }
}
