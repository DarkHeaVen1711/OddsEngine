package com.oddsengine.repository;

import com.oddsengine.model.SportEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SportEventRepository extends JpaRepository<SportEvent, String> {
    List<SportEvent> findBySportIdAndStatus(String sportId, String status);
    List<SportEvent> findByStatus(String status);
}
