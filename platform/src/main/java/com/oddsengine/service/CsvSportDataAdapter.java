package com.oddsengine.service;

import com.oddsengine.model.Participant;
import com.oddsengine.model.SportEntity;
import com.oddsengine.model.SportEvent;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class CsvSportDataAdapter implements SportDataAdapter {
    private String csvFilePath = "";

    public String getCsvFilePath() { return csvFilePath; }
    public void setCsvFilePath(String csvFilePath) { this.csvFilePath = csvFilePath; }

    @Override
    public List<EventWrapper> fetchRecentMatches(String sportId, Long since) {
        List<EventWrapper> events = new ArrayList<>();
        if (csvFilePath == null || csvFilePath.isEmpty()) {
            return events;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(csvFilePath))) {
            String line;
            boolean isHeader = true;
            while ((line = br.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }
                String[] parts = line.split(",");
                if (parts.length < 9) continue;

                String id = parts[0].trim();
                String spId = parts[1].trim();
                if (!spId.equalsIgnoreCase(sportId)) continue;

                long timestamp = Long.parseLong(parts[2].trim());
                if (timestamp < since) continue;

                String venue = parts[3].trim();
                String homeTeamName = parts[4].trim();
                String awayTeamName = parts[5].trim();
                int homeGoals = Integer.parseInt(parts[6].trim());
                int awayGoals = Integer.parseInt(parts[7].trim());
                String status = parts[8].trim();

                SportEvent event = new SportEvent();
                event.setId(id);
                event.setSportId(spId);
                event.setTimestamp(timestamp);
                event.setVenue(venue);
                event.setStatus(status);
                event.setMetadataJson(String.format("{\"fthg\":%d,\"ftag\":%d}", homeGoals, awayGoals));

                String homeEntityId = homeTeamName.toLowerCase().replace(" ", "_");
                String awayEntityId = awayTeamName.toLowerCase().replace(" ", "_");

                SportEntity homeEntity = new SportEntity();
                homeEntity.setId(homeEntityId);
                homeEntity.setName(homeTeamName);
                homeEntity.setSportId(spId);
                homeEntity.setEntityType("team");
                homeEntity.setMetadataJson("{}");

                SportEntity awayEntity = new SportEntity();
                awayEntity.setId(awayEntityId);
                awayEntity.setName(awayTeamName);
                awayEntity.setSportId(spId);
                awayEntity.setEntityType("team");
                awayEntity.setMetadataJson("{}");

                Participant homePart = new Participant();
                homePart.setEventId(id);
                homePart.setEntityId(homeEntityId);
                homePart.setResultDataJson(String.format("{\"goals\":%d}", homeGoals));
                homePart.setFinishRank(homeGoals > awayGoals ? 1 : (homeGoals < awayGoals ? 2 : 1));

                Participant awayPart = new Participant();
                awayPart.setEventId(id);
                awayPart.setEntityId(awayEntityId);
                awayPart.setResultDataJson(String.format("{\"goals\":%d}", awayGoals));
                awayPart.setFinishRank(awayGoals > homeGoals ? 1 : (awayGoals < homeGoals ? 2 : 1));

                events.add(new EventWrapper(
                    event,
                    Arrays.asList(homePart, awayPart),
                    Arrays.asList(homeEntity, awayEntity)
                ));
            }
        } catch (Exception e) {
            throw new RuntimeException("Error parsing CSV match ingestion data: " + e.getMessage(), e);
        }
        return events;
    }
}
