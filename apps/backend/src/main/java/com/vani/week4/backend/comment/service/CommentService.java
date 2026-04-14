package com.vani.week4.backend.comment.service;

import com.github.f4b6a3.ulid.UlidCreator;
import com.vani.week4.backend.comment.dto.CommentCreateRequest;
import com.vani.week4.backend.comment.dto.CommentResponse;
import com.vani.week4.backend.comment.dto.CommentUpdateRequest;
import com.vani.week4.backend.comment.dto.CommentUpdateResponse;
import com.vani.week4.backend.comment.entity.Comment;
import com.vani.week4.backend.comment.repository.CommentRepository;
import com.vani.week4.backend.global.ErrorCode;
import com.vani.week4.backend.global.dto.SliceResponse;
import com.vani.week4.backend.global.exception.*;
import com.vani.week4.backend.post.entity.Post;
import com.vani.week4.backend.post.repository.PostRepository;
import com.vani.week4.backend.user.entity.User;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author vani
 * @since 10/15/25
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class CommentService {
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final CommentTreeAssembler commentTreeAssembler;

    /**
     * 댓글 조회 메서드,커서 기반 페이징
     * */
    public SliceResponse<CommentResponse> getComments(
            String postId,
            String cursorId,
            LocalDateTime cursorCreatedAt,
            int size ) {

        postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

        // 정렬된 최상위 댓글을 먼저 조회하고, 현재 페이지 루트 댓글의 대댓글만 별도로 일괄 조회한다.
        Pageable pageable = PageRequest.of(0, size);
        Slice<Comment> topLevelComments = commentRepository.findTopLevelComments(postId, cursorCreatedAt, cursorId, pageable);
        List<Comment> rootComments = topLevelComments.getContent();
        Map<String, List<Comment>> repliesByCommentGroup = findRepliesByCommentGroup(rootComments);

        // 루트 댓글마다 대댓글을 다시 조회하지 않고, commentGroup 기준으로 미리 묶어둔 결과를 조립한다.
        List<CommentResponse> responses = rootComments.stream()
                .map(comment -> commentTreeAssembler.toCommentWithReplies(
                        comment,
                        repliesByCommentGroup.getOrDefault(comment.getCommentGroup(), List.of())
                ))
                .toList();

        // 다음 커서 생성, 더보기 버튼
        SliceResponse.Cursor nextCursor = null;
        if (topLevelComments.hasNext() && !responses.isEmpty()) {
            Comment lastComment = topLevelComments.getContent()
                    .get(topLevelComments.getContent().size() - 1);
            nextCursor = new SliceResponse.Cursor(
                    lastComment.getId(),
                    lastComment.getCreatedAt()
            );
        }

        return new SliceResponse<CommentResponse>(responses, nextCursor, topLevelComments.hasNext());
    }

    private Map<String, List<Comment>> findRepliesByCommentGroup(List<Comment> rootComments) {
        if (rootComments.isEmpty()) {
            return Map.of();
        }

        // 현재 페이지에 포함된 루트 댓글 그룹만 대상으로 조회해서 루트 수만큼 쿼리하는 N+1을 피한다.
        List<String> commentGroups = rootComments.stream()
                .map(Comment::getCommentGroup)
                .toList();

        return commentRepository.findRepliesByCommentGroupIn(commentGroups).stream()
                .collect(Collectors.groupingBy(Comment::getCommentGroup));
    }

    /**
     * 댓글 생성 메서드
     */
    @Transactional
    public CommentResponse createComment(String postId, User user, CommentCreateRequest request){

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

        String commentId = UlidCreator.getUlid().toString();

        String safeContent = HtmlUtils.htmlEscape(request.content());

        // 대댓글인지 검증
        Comment parent = null;
        if (request.parentId().isPresent()){
            parent = commentRepository.findById(request.parentId().get())
                    .orElseThrow(() -> new CommentNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

            //악의적인 오류 유발 댓글 방지
            if(!parent.getPost().getId().equals(post.getId())){
                throw new InvalidCommentException(ErrorCode.INVALID_INPUT);
            }

            //댓글 깊이 제한
            //TODO 깊이 제한을 어디까지 할것인지 확인 필요
            if(parent.getDepth() >= 3) {
                throw new MaxDepthExceededException(ErrorCode.INVALID_INPUT);
            }
        }

        Comment comment = Comment.builder()
                .id(commentId)
                .user(user)
                .post(post)
                .parentId(request.parentId().orElse(null))
                .depth(parent == null ? 0 : parent.getDepth() + 1)
                .commentGroup(parent == null ? commentId : parent.getCommentGroup())
                .content(safeContent)
                .build();

        commentRepository.save(comment);
        //TODO Count로직 개선하기
        post.incrementCommentCount();

        return commentTreeAssembler.toCommentResponse(comment);
    }

    /**
     * 댓글 수정 메서드
     * */
    @Transactional
    public CommentUpdateResponse updateComment(String postId, String commentId,User user, CommentUpdateRequest request){

        postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!comment.getPost().getId().equals(postId)) {
            throw new InvalidCommentException(ErrorCode.INVALID_INPUT);
        }

        if (comment.isDeleted()) {
            throw new InvalidCommentException(ErrorCode.INVALID_INPUT);
        }

        if (!comment.getUser().getId().equals(user.getId())) {
            throw new UserAccessDeniedException(ErrorCode.FORBIDDEN);
        }

        comment.updateContent(HtmlUtils.htmlEscape(request.content()));

        commentRepository.save(comment);

        return new CommentUpdateResponse(
                comment.getId(),
                comment.getContent(),
                comment.getUpdatedAt()
        );
    }

    /**
     * 댓글 삭제 메서드
     * TODO (Soft delete), enduser에게 삭제됨 표시
     * */
    @Transactional
    public void deleteComment(String postId, String commentId, User user){

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!comment.getPost().getId().equals(postId)) {
            throw new InvalidCommentException(ErrorCode.INVALID_INPUT);
        }

        if (!comment.getUser().getId().equals(user.getId())) {
            throw new UserAccessDeniedException(ErrorCode.FORBIDDEN);
        }
        if (!comment.isDeleted()) {
            comment.softDelete();
            comment.getPost().decreaseCommentCount();
        }
    }

}
