package com.oddsengine.repository;

import com.oddsengine.model.ContextFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContextFeatureRepository extends JpaRepository<ContextFeature, String> {
    List<ContextFeature> findByEntityId(String entityId);
    List<ContextFeature> findByEntityIdAndEventId(String entityId, String eventId);
    List<ContextFeature> findByEntityIdAndFeatureName(String entityId, String featureName);
}
