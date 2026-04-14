package com.vani.week4.backend.comment.service;

import com.vani.week4.backend.comment.dto.CommentCreateRequest;
import com.vani.week4.backend.comment.dto.CommentResponse;
import com.vani.week4.backend.comment.dto.CommentUpdateRequest;
import com.vani.week4.backend.comment.dto.CommentUpdateResponse;
import com.vani.week4.backend.comment.entity.Comment;
import com.vani.week4.backend.comment.entity.CommentStatus;
import com.vani.week4.backend.comment.repository.CommentRepository;
import com.vani.week4.backend.global.dto.SliceResponse;
import com.vani.week4.backend.global.exception.CommentNotFoundException;
import com.vani.week4.backend.global.exception.InvalidCommentException;
import com.vani.week4.backend.global.exception.MaxDepthExceededException;
import com.vani.week4.backend.global.exception.PostNotFoundException;
import com.vani.week4.backend.global.exception.UserAccessDeniedException;
import com.vani.week4.backend.infra.S3.S3Service;
import com.vani.week4.backend.post.entity.Post;
import com.vani.week4.backend.post.repository.PostRepository;
import com.vani.week4.backend.support.fixture.CommentFixture;
import com.vani.week4.backend.support.fixture.PostFixture;
import com.vani.week4.backend.support.fixture.UserFixture;
import com.vani.week4.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

import java.util.List;
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

    private CommentService commentService;

    private User user;
    private Post post;
    private Comment comment;

    @BeforeEach
    void setUp() {
        user = UserFixture.user();
        post = PostFixture.post(user);
        comment = CommentFixture.rootComment(post, user);
        commentService = new CommentService(
                commentRepository,
                postRepository,
                new CommentTreeAssembler(s3Service)
        );

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
        assertThat(response.status()).isEqualTo(CommentStatus.ACTIVE);

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

    @Test
    @DisplayName("존재하지 않는 댓글을 부모로 지정하면 예외가 발생한다.")
    void createComment_withMissingParentComment_throwsCommentNotFoundException() {
        // given
        CommentCreateRequest request = new CommentCreateRequest(
                "없는 부모 댓글에 대댓글 달기",
                Optional.of("missing-parent-comment-id")
        );

        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(commentRepository.findById("missing-parent-comment-id")).thenReturn(Optional.empty());

        // when & then
        // TODO: 컨트롤러/API 테스트에서는 이 예외가 기대한 HTTP 상태와 에러 바디로 변환되는지도 확인한다.
        assertThatThrownBy(() -> commentService.createComment(post.getId(), user, request))
                .isInstanceOf(CommentNotFoundException.class);

        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    @DisplayName("허용 깊이를 초과하는 대댓글 생성은 예외가 발생한다.")
    void createComment_whenParentDepthExceedsLimit_throwsMaxDepthExceededException() {
        // given
        Comment deepParentComment = comment(
                "deep-parent-comment-id",
                "parent-comment-id",
                3,
                comment.getCommentGroup(),
                "깊은 댓글"
        );
        CommentCreateRequest request = new CommentCreateRequest(
                "깊이 제한 초과 대댓글",
                Optional.of(deepParentComment.getId())
        );

        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(commentRepository.findById(deepParentComment.getId())).thenReturn(Optional.of(deepParentComment));

        // when & then
        // TODO: 댓글 최대 depth 정책이 확정되면 경계값(depth 2 허용, depth 3 차단)을 함께 고정한다.
        assertThatThrownBy(() -> commentService.createComment(post.getId(), user, request))
                .isInstanceOf(MaxDepthExceededException.class);

        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    @DisplayName("댓글 생성 시 HTML을 escape 처리한다.")
    void createComment_escapesHtmlContent() {
        //given
        CommentCreateRequest request = new CommentCreateRequest(
                "<script>alert('xss')</script>",
                Optional.empty()
        );

        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));

        //when
        CommentResponse response = commentService.createComment(post.getId(), user, request);

        //then
        assertThat(response.content()).isEqualTo("&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;");
        assertThat(response.content()).doesNotContain("<script>");
    }

    @Test
    @DisplayName("본인의 댓글을 수정할 수 있다.")
    void updateComment_byOwner_updatesContent() {
        //given
        CommentUpdateRequest request = new CommentUpdateRequest("수정된 댓글");

        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(commentRepository.findById(comment.getId())).thenReturn(Optional.of(comment));

        // when
        CommentUpdateResponse response =
                commentService.updateComment(post.getId(), comment.getId(), user, request);

        // then
        assertThat(response.id()).isEqualTo(comment.getId());
        assertThat(response.content()).isEqualTo("수정된 댓글");
        assertThat(comment.getContent()).isEqualTo("수정된 댓글");

    }

    @Test
    @DisplayName("다른 사람의 댓글을 수정하면 예외가 발생한다.")
    void updateComment_byOtherUser_throwsUserAccessDeniedException() {
        //given
        User otherUser = UserFixture.user("otherUser1", "바나나");
        Comment otherComment = CommentFixture.rootComment(post, otherUser);
        CommentUpdateRequest request = new CommentUpdateRequest("수정된 댓글");

        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(commentRepository.findById(otherComment.getId())).thenReturn(Optional.of(otherComment));

        //when & then
        assertThatThrownBy(() -> commentService.updateComment(post.getId(), otherComment.getId(), user, request)
        ).isInstanceOf(UserAccessDeniedException.class);

        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    @DisplayName("삭제된 댓글은 수정할 수 없다.")
    void updateComment_whenDeleted_throwsInvalidCommentException() {
        //given
        CommentUpdateRequest request = new CommentUpdateRequest("수정된 댓글");
        comment.softDelete();

        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(commentRepository.findById(comment.getId())).thenReturn(Optional.of(comment));

        //when & then
        assertThatThrownBy(() -> commentService.updateComment(post.getId(), comment.getId(), user, request)
        ).isInstanceOf(InvalidCommentException.class);

        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    @DisplayName("루트 댓글과 대댓글을 트리 구조로 조회한다.")
    void getComments_returnsRepliesAsTree() {
        //given
        Comment reply = comment("comment-2", comment.getId(), 1, comment.getCommentGroup(), "대댓글");
        Comment nestedReply = comment("comment-3", reply.getId(), 2, comment.getCommentGroup(), "대대댓글");

        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(commentRepository.findTopLevelComments(post.getId(), null, null, PageRequest.of(0, 10)))
                .thenReturn(new SliceImpl<>(List.of(comment), PageRequest.of(0, 10), false));
        when(commentRepository.findRepliesByCommentGroupIn(List.of(comment.getCommentGroup())))
                .thenReturn(List.of(reply, nestedReply));

        //when
        SliceResponse<CommentResponse> response = commentService.getComments(post.getId(), null, null, 10);

        //then
        CommentResponse rootResponse = response.items().getFirst();
        assertThat(rootResponse.commentId()).isEqualTo(comment.getId());
        assertThat(rootResponse.replies()).hasSize(1);
        assertThat(rootResponse.replyCount()).isEqualTo(2);

        CommentResponse replyResponse = rootResponse.replies().getFirst();
        assertThat(replyResponse.commentId()).isEqualTo(reply.getId());
        assertThat(replyResponse.parentId()).isEqualTo(comment.getId());
        assertThat(replyResponse.replies()).hasSize(1);
        assertThat(replyResponse.replies().getFirst().commentId()).isEqualTo(nestedReply.getId());
    }

    @Test
    @DisplayName("루트 댓글이 없으면 대댓글 조회를 하지 않는다.")
    void getComments_withoutRootComments_doesNotFetchReplies() {
        // given
        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(commentRepository.findTopLevelComments(post.getId(), null, null, PageRequest.of(0, 10)))
                .thenReturn(new SliceImpl<>(List.of(), PageRequest.of(0, 10), false));

        // when
        SliceResponse<CommentResponse> response = commentService.getComments(post.getId(), null, null, 10);

        // then
        assertThat(response.items()).isEmpty();
        verify(commentRepository, never()).findRepliesByCommentGroupIn(anyList());
    }

    @Test
    @DisplayName("여러 루트 댓글의 대댓글을 한 번에 조회해 각 루트 댓글에 조립한다.")
    void getComments_fetchesRepliesForCurrentPageInSingleQuery() {
        // given
        Comment nextRootComment = comment("comment-4", null, 0, "comment-4", "두 번째 루트 댓글");
        Comment firstRootReply = comment("comment-2", comment.getId(), 1, comment.getCommentGroup(), "첫 번째 루트의 대댓글");
        Comment secondRootReply = comment("comment-5", nextRootComment.getId(), 1, nextRootComment.getCommentGroup(), "두 번째 루트의 대댓글");
        List<String> commentGroups = List.of(comment.getCommentGroup(), nextRootComment.getCommentGroup());

        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(commentRepository.findTopLevelComments(post.getId(), null, null, PageRequest.of(0, 10)))
                .thenReturn(new SliceImpl<>(List.of(comment, nextRootComment), PageRequest.of(0, 10), false));
        when(commentRepository.findRepliesByCommentGroupIn(commentGroups))
                .thenReturn(List.of(firstRootReply, secondRootReply));

        // when
        SliceResponse<CommentResponse> response = commentService.getComments(post.getId(), null, null, 10);

        // then
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).replies()).extracting(CommentResponse::commentId)
                .containsExactly(firstRootReply.getId());
        assertThat(response.items().get(1).replies()).extracting(CommentResponse::commentId)
                .containsExactly(secondRootReply.getId());

        verify(commentRepository).findRepliesByCommentGroupIn(commentGroups);
    }

    @Test
    @DisplayName("다음 페이지가 있으면 마지막 댓글 기준으로 다음 커서를 응답한다.")
    void getComments_whenHasNext_setsNextCursor() {
        //given
        Comment nextCursorComment = comment("comment-2", null, 0, "comment-2", "두 번째 댓글");

        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(commentRepository.findTopLevelComments(post.getId(), null, null, PageRequest.of(0, 2)))
                .thenReturn(new SliceImpl<>(List.of(comment, nextCursorComment), PageRequest.of(0, 2), true));
        when(commentRepository.findRepliesByCommentGroupIn(List.of(comment.getCommentGroup(), nextCursorComment.getCommentGroup())))
                .thenReturn(List.of());

        //when
        SliceResponse<CommentResponse> response = commentService.getComments(post.getId(), null, null, 2);

        //then
        assertThat(response.hasMore()).isTrue();
        assertThat(response.nextCursor()).isNotNull();
        assertThat(response.nextCursor().id()).isEqualTo(nextCursorComment.getId());
        assertThat(response.nextCursor().createdAt()).isEqualTo(nextCursorComment.getCreatedAt());
    }

    @Test
    @DisplayName("본인의 댓글을 소프트 삭제하고 활성 댓글 수를 줄인다.")
    void deleteComment_byOwner_softDeletesAndDecreasesActiveCommentCount() {
        //given
        post.incrementCommentCount();
        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(commentRepository.findById(comment.getId())).thenReturn(Optional.of(comment));

        //when
        commentService.deleteComment(post.getId(), comment.getId(), user);

        //then
        assertThat(comment.getCommentStatus()).isEqualTo(CommentStatus.DELETED);
        assertThat(comment.getDeletedAt()).isNotNull();
        assertThat(post.getCommentCount()).isEqualTo(0);
        verify(commentRepository, never()).delete(any(Comment.class));
    }

    @Test
    @DisplayName("이미 삭제된 댓글은 다시 댓글 수를 줄이지 않는다.")
    void deleteComment_whenAlreadyDeleted_doesNotDecreaseCountAgain() {
        //given
        comment.softDelete();
        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(commentRepository.findById(comment.getId())).thenReturn(Optional.of(comment));

        //when
        commentService.deleteComment(post.getId(), comment.getId(), user);

        //then
        assertThat(post.getCommentCount()).isEqualTo(0);
        verify(commentRepository, never()).delete(any(Comment.class));
    }

    @Test
    @DisplayName("삭제된 댓글은 내용과 작성자를 마스킹하고 삭제 상태를 응답한다.")
    void getComments_withDeletedComment_masksContentAndAuthor() {
        //given
        comment.softDelete();
        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(commentRepository.findTopLevelComments(post.getId(), null, null, PageRequest.of(0, 10)))
                .thenReturn(new SliceImpl<>(List.of(comment), PageRequest.of(0, 10), false));
        when(commentRepository.findRepliesByCommentGroupIn(List.of(comment.getCommentGroup()))).thenReturn(List.of());

        //when
        SliceResponse<CommentResponse> response = commentService.getComments(post.getId(), null, null, 10);

        //then
        CommentResponse deletedComment = response.items().getFirst();
        assertThat(deletedComment.content()).isEqualTo("삭제된 댓글입니다.");
        assertThat(deletedComment.author()).isNull();
        assertThat(deletedComment.status()).isEqualTo(CommentStatus.DELETED);
    }

    private Comment comment(String id, String parentId, int depth, String commentGroup, String content) {
        return Comment.builder()
                .id(id)
                .post(post)
                .user(user)
                .parentId(parentId)
                .depth(depth)
                .commentGroup(commentGroup)
                .content(content)
                .build();
    }

}
