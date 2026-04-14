package com.vani.week4.backend.comment.repository;

import com.vani.week4.backend.comment.entity.Comment;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author vani
 * @since 10/15/25
 */
public interface CommentRepository extends JpaRepository<Comment,String> {
    // 우선 생성+아이디로 내림차순
    // 루트댓글만 가져옴 : parentId IS NULL
    // 유저의 정보도 필요하기 때문에 N+1 방지 위해서 패치 조인
    @Query( "SELECT c FROM Comment c " +
            "JOIN FETCH c.user " +
            "WHERE c.post.id = :postId " +
            "AND c.parentId IS NULL " +
            "AND (:cursorCreatedAt IS NULL OR " +
            "c.createdAt < :cursorCreatedAt OR " +
            "(c.createdAt = :cursorCreatedAt AND c.id < :cursorId)) " +
            "ORDER BY c.createdAt DESC, c.id DESC")
    Slice<Comment> findTopLevelComments(
            @Param("postId") String postId,
            @Param("cursorCreatedAt")LocalDateTime cursorCreatedAt,
            @Param("cursorId") String CursorId,
            Pageable pageable
            );

    // 현재 페이지의 루트 댓글들에 속한 대댓글을 한 번에 가져옴
    @Query( "SELECT c FROM Comment c " +
            "JOIN FETCH c.user " +
            "WHERE c.commentGroup IN :commentGroups " +
            "AND c.parentId IS NOT NULL " +
            "ORDER BY c.commentGroup ASC, c.createdAt ASC")
    List<Comment> findRepliesByCommentGroupIn(@Param("commentGroups") List<String> commentGroups);
}
