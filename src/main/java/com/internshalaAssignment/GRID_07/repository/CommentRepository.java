package com.internshalaAssignment.GRID_07.repository;

import com.internshalaAssignment.GRID_07.domain.entity.Comment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {

	Optional<Comment> findByIdAndPostId(Long id, Long postId);

	long countByPostId(Long postId);
}

