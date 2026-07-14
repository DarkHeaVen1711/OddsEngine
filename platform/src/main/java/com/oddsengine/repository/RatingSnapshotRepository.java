package com.oddsengine.repository;

import com.oddsengine.model.RatingSnapshot;
import com.oddsengine.model.RatingSnapshotId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface RatingSnapshotRepository extends JpaRepository<RatingSnapshot, RatingSnapshotId> {
    List<RatingSnapshot> findByEntityIdAndSportIdAndModelNameOrderByAsOfTimestampAsc(String entityId, String sportId, String modelName);

    @Query(value = "SELECT * FROM rating_snapshots WHERE entity_id = ?1 AND sport_id = ?2 AND model_name = ?3 ORDER BY as_of_timestamp DESC LIMIT 1", nativeQuery = true)
    Optional<RatingSnapshot> findLatestRating(String entityId, String sportId, String modelName);

    @Query(value = "SELECT r1.* FROM rating_snapshots r1 JOIN (SELECT entity_id, MAX(as_of_timestamp) as max_ts FROM rating_snapshots WHERE sport_id = ?1 AND model_name = ?2 GROUP BY entity_id) r2 ON r1.entity_id = r2.entity_id AND r1.as_of_timestamp = r2.max_ts ORDER BY r1.rating DESC", nativeQuery = true)
    List<RatingSnapshot> getLeaderboard(String sportId, String modelName);
}
