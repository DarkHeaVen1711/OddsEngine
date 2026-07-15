package com.oddsengine.repository;

import com.oddsengine.model.Participant;
import com.oddsengine.model.ParticipantId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ParticipantRepository extends JpaRepository<Participant, ParticipantId> {
    List<Participant> findByEventId(String eventId);
    List<Participant> findByEntityId(String entityId);
    long countByEntityId(String entityId);
}
