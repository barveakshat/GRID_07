package com.internshalaAssignment.GRID_07.repository;

import com.internshalaAssignment.GRID_07.domain.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

	boolean existsByPostIdAndUserId(Long postId, Long userId);

	long countByPostId(Long postId);
}

