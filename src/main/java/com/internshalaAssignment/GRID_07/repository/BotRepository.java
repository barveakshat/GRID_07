package com.internshalaAssignment.GRID_07.repository;

import com.internshalaAssignment.GRID_07.domain.entity.Bot;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BotRepository extends JpaRepository<Bot, Long> {

	Optional<Bot> findByName(String name);
}

