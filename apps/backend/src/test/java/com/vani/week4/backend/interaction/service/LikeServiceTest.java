package com.vani.week4.backend.interaction.service;

import com.github.f4b6a3.ulid.UlidCreator;
import com.vani.week4.backend.interaction.entity.Like;
import com.vani.week4.backend.interaction.entity.UserPostLikeId;
import com.vani.week4.backend.interaction.repository.LikeRepository;
import com.vani.week4.backend.post.entity.Post;
import com.vani.week4.backend.post.repository.PostRepository;
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
 * {@link LikeService} 가 유스케이스 조합만 담당하는지 검증합니다.
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
        user = User.createUser(
                UlidCreator.getUlid().toString(),
                "tester",
                "profile-image-key"
        );
        post = Post.builder()
                .id(POST_ID)
                .user(user)
                .title("title")
                .build();
    }

    /**
     * 기존 좋아요가 있으면 삭제 후 캐시 count 감소를 요청해야 합니다.
     */
    @Test
    @DisplayName("이미 좋아요한 게시글이면 좋아요를 취소하고 캐시 count를 감소시킨다")
    void toggleLike_RemovesLikeAndDecrementsCacheWhenLikeExists() {
        UserPostLikeId likeId = new UserPostLikeId(user.getId(), POST_ID);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(likeRepository.existsById(likeId)).thenReturn(true);

        likeService.toggleLike(user, POST_ID);

        verify(likeRepository).deleteById(likeId);
        verify(likeCountCache).decrement(POST_ID);
        verify(likeRepository, never()).save(org.mockito.ArgumentMatchers.any(Like.class));
    }

    /**
     * 기존 좋아요가 없으면 저장 후 캐시 count 증가를 요청해야 합니다.
     */
    @Test
    @DisplayName("좋아요하지 않은 게시글이면 좋아요를 추가하고 캐시 count를 증가시킨다")
    void toggleLike_SavesLikeAndIncrementsCacheWhenLikeDoesNotExist() {
        ArgumentCaptor<Like> likeCaptor = ArgumentCaptor.forClass(Like.class);

        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(likeRepository.existsById(new UserPostLikeId(user.getId(), POST_ID))).thenReturn(false);

        likeService.toggleLike(user, POST_ID);

        verify(likeRepository).save(likeCaptor.capture());
        verify(likeCountCache).increment(POST_ID);
        assertThat(likeCaptor.getValue().getUserPostLikeId())
                .isEqualTo(new UserPostLikeId(user.getId(), POST_ID));
    }
}
