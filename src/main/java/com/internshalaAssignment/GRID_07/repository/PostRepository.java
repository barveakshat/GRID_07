package com.internshalaAssignment.GRID_07.repository;

import com.internshalaAssignment.GRID_07.domain.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {
}

