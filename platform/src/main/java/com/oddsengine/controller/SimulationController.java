package com.oddsengine.controller;

import com.oddsengine.service.SimulationService;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/simulations")
public class SimulationController {
    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping("/season/{leagueId}")
    public Map<String, String> startSimulation(@PathVariable String leagueId,
                                               @RequestParam(defaultValue = "1000") int nSimulations) {
        String jobId = simulationService.startSimulation(leagueId, nSimulations);
        return Collections.singletonMap("jobId", jobId);
    }

    @GetMapping("/{jobId}")
    public SimulationService.SimulationJob getSimulationStatus(@PathVariable String jobId) {
        SimulationService.SimulationJob job = simulationService.getJob(jobId);
        if (job == null) {
            throw new IllegalArgumentException("Simulation job not found: " + jobId);
        }
        return job;
    }
}
