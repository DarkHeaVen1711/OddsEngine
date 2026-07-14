package com.oddsengine.repository;

import com.oddsengine.model.PredictionRecord;
import com.oddsengine.model.PredictionRecordId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PredictionRecordRepository extends JpaRepository<PredictionRecord, PredictionRecordId> {
    List<PredictionRecord> findByEventId(String eventId);
}
