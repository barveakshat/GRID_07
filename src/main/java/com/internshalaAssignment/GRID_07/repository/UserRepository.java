package com.internshalaAssignment.GRID_07.repository;

import com.internshalaAssignment.GRID_07.domain.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByUsername(String username);
}

