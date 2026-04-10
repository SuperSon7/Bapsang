package com.vani.week4.backend.comment.service;

import com.vani.week4.backend.comment.dto.CommentCreateRequest;
import com.vani.week4.backend.comment.dto.CommentResponse;
import com.vani.week4.backend.comment.entity.Comment;
import com.vani.week4.backend.comment.repository.CommentRepository;
import com.vani.week4.backend.global.exception.InvalidCommentException;
import com.vani.week4.backend.global.exception.PostNotFoundException;
import com.vani.week4.backend.infra.S3.S3Service;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class CommentServiceTest {
    @Mock
    private CommentRepository commentRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private S3Service s3Service;

    @InjectMocks
    private CommentService commentService;

    private User user;
    private Post post;

    @BeforeEach
    void setUp() {
        user = UserFixture.user();
        post = PostFixture.post(user);

    }

    @Test
    @DisplayName("부모 댓글이 없으면 루트 댓글을 생성한다.")
    void createComment_withoutParent_createsRootComment() {
        // given
        CommentCreateRequest request = new CommentCreateRequest(
                "댓글입니다",
                Optional.empty()
        );

        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));

        // when
        CommentResponse response = commentService.createComment(post.getId(), user, request);

        // then
        assertThat(response.commentId()).isNotNull();
        assertThat(response.content()).isEqualTo("댓글입니다");
        assertThat(response.depth()).isEqualTo(0);
        assertThat(response.parentId()).isNull();
        assertThat(response.author().nickname()).isEqualTo("바닐라");

        verify(commentRepository).save(any(Comment.class));
        assertThat(post.getCommentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("게시글이 없으면 댓글 생성 시 예외가 발생한다.")
    void createComment_whenPostDoesNotExist_throwsPostNotFoundException() {
        //given
        CommentCreateRequest request = new CommentCreateRequest(
                "없는 게시글에 댓글 달기",
                Optional.empty()
        );
        when(postRepository.findById("non-post-id")).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> commentService.createComment("non-post-id", user, request)
        ).isInstanceOf(PostNotFoundException.class);

        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    @DisplayName("같은 게시글의 댓글을 부모로 지정하면 부모보다 depth가 1 증가하고 같은 commentGroup을 사용한다.")
    void createComment_withParentComment_createsReplyWithNextDepthAndSameCommentGroup() {
        // given
        Comment parentComment = Comment.builder()
                .id("parent-comment-id")
                .user(user)
                .post(post)
                .parentId(null)
                .depth(0)
                .commentGroup("parent-comment-id")
                .content("부모 댓글")
                .build();

        CommentCreateRequest request = new CommentCreateRequest(
                "대댓글 달기",
                Optional.of(parentComment.getId())
        );

        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(commentRepository.findById(parentComment.getId())).thenReturn(Optional.of(parentComment));

        //when
        CommentResponse response = commentService.createComment(post.getId(), user, request);

        //then
        assertThat(response.parentId()).isEqualTo(parentComment.getId());
        assertThat(response.depth()).isEqualTo(1);
        assertThat(response.commentGroup()).isEqualTo(parentComment.getCommentGroup());

        verify(commentRepository).save(any(Comment.class));
    }

    @Test
    @DisplayName("다른 게시글의 댓글을 부모로 지정하면 예외가 발생한다.")
    void createComment_withParentCommentFromOtherPost_throwsInvalidCommentException() {
        // given
        Post otherPost = PostFixture.post("post-2", user);

        Comment parentComment = Comment.builder()
                .id("parent-comment-id")
                .user(user)
                .post(otherPost)
                .parentId(null)
                .depth(0)
                .commentGroup("parent-comment-id")
                .content("부모 댓글")
                .build();

        CommentCreateRequest request = new CommentCreateRequest(
                "대댓글 달기",
                Optional.of(parentComment.getId())
        );

        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(commentRepository.findById(parentComment.getId())).thenReturn(Optional.of(parentComment));


        //when & then
        assertThatThrownBy(() -> commentService.createComment(post.getId(), user, request)
        ).isInstanceOf(InvalidCommentException.class);

        verify(commentRepository, never()).save(any(Comment.class));
    }

    @DisplayName("댓글 생성 시 HTML을 escape 처리한다.")
    void createComment_escapesHtmlContent() {
    }

    @DisplayName("본인의 댓글을 수정할 수 있다.")
    void updateComment_byOwner_updatesContent() {
    }

    @DisplayName("다른 사람의 댓글을 수정하면 예외가 발생한다.")
    void updateComment_byOtherUser_throwsUnauthorizedException() {
    }

    @DisplayName("본인의 댓글을 삭제할 수 있다.")
    void deleteComment_byOwner_deletesComment() {

    }

    @DisplayName("다른 사람의 댓글을 삭제하면 예외가 발생한다.")
    void deleteComment_byOtherUser_throwsUnauthorizedException() {

    }
}
