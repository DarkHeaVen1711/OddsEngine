package com.oddsengine.repository;

import com.oddsengine.model.SportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SportEntityRepository extends JpaRepository<SportEntity, String> {
}
