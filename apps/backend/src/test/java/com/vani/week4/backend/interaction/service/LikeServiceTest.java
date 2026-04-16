package com.vani.week4.backend.interaction.service;

import com.vani.week4.backend.interaction.entity.Like;
import com.vani.week4.backend.interaction.entity.UserPostLikeId;
import com.vani.week4.backend.interaction.repository.LikeRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LikeService} 가 명시적 like/unlike 유스케이스만 조합하는지 검증합니다.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class LikeServiceTest {
    private static final String POST_ID = "post-1";

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private LikeCountCache likeCountCache;

    @InjectMocks
    private LikeService likeService;

    private User user;
    private Post post;

    @BeforeEach
    void setUp() {
        user = UserFixture.user("user-1", "tester");
        post = PostFixture.post(POST_ID, user);
    }

    /**
     * 좋아요가 없으면 저장 후 캐시 count 증가를 요청해야 합니다.
     */
    @Test
    @DisplayName("좋아요하지 않은 게시글이면 좋아요를 추가하고 캐시 count를 증가시킨다")
    void likePost_SavesLikeAndIncrementsCacheWhenLikeDoesNotExist() {
        ArgumentCaptor<Like> likeCaptor = ArgumentCaptor.forClass(Like.class);
        UserPostLikeId likeId = new UserPostLikeId(user.getId(), POST_ID);

        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(likeRepository.existsById(likeId)).thenReturn(false);

        likeService.likePost(user, POST_ID);

        verify(likeRepository).save(likeCaptor.capture());
        verify(likeCountCache).increment(POST_ID);
        assertThat(likeCaptor.getValue().getUserPostLikeId())
                .isEqualTo(likeId);
    }

    /**
     * 이미 좋아요한 상태에서 같은 요청이 다시 와도 no-op 이어야 합니다.
     */
    @Test
    @DisplayName("이미 좋아요한 게시글에 다시 좋아요 요청이 오면 아무것도 하지 않는다")
    void likePost_DoesNothingWhenLikeAlreadyExists() {
        UserPostLikeId likeId = new UserPostLikeId(user.getId(), POST_ID);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(likeRepository.existsById(likeId)).thenReturn(true);

        likeService.likePost(user, POST_ID);

        verify(likeRepository, never()).save(org.mockito.ArgumentMatchers.any(Like.class));
        verify(likeRepository, never()).deleteById(likeId);
        verify(likeCountCache, never()).increment(POST_ID);
        verify(likeCountCache, never()).decrement(POST_ID);
    }

    /**
     * 기존 좋아요가 있으면 삭제 후 캐시 count 감소를 요청해야 합니다.
     */
    @Test
    @DisplayName("이미 좋아요한 게시글이면 좋아요를 취소하고 캐시 count를 감소시킨다")
    void unlikePost_RemovesLikeAndDecrementsCacheWhenLikeExists() {
        UserPostLikeId likeId = new UserPostLikeId(user.getId(), POST_ID);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(likeRepository.existsById(likeId)).thenReturn(true);

        likeService.unlikePost(user, POST_ID);

        verify(likeRepository).deleteById(likeId);
        verify(likeCountCache).decrement(POST_ID);
        verify(likeRepository, never()).save(org.mockito.ArgumentMatchers.any(Like.class));
    }

    /**
     * 좋아요가 없는 상태에서 같은 취소 요청이 다시 와도 no-op 이어야 합니다.
     */
    @Test
    @DisplayName("좋아요하지 않은 게시글에 다시 취소 요청이 오면 아무것도 하지 않는다")
    void unlikePost_DoesNothingWhenLikeDoesNotExist() {
        UserPostLikeId likeId = new UserPostLikeId(user.getId(), POST_ID);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(likeRepository.existsById(likeId)).thenReturn(false);

        likeService.unlikePost(user, POST_ID);

        verify(likeRepository, never()).save(org.mockito.ArgumentMatchers.any(Like.class));
        verify(likeRepository, never()).deleteById(likeId);
        verify(likeCountCache, never()).increment(POST_ID);
        verify(likeCountCache, never()).decrement(POST_ID);
    }
}
