package com.springai.controller;

import com.springai.dto.TripPlan;
import com.springai.governance.GovernedCalls;
import com.springai.service.TripPlannerService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Structured-output demos. They call the same model, so they pass through the same governance. */
@RestController
@RequestMapping("/api/trips")
public class TripPlannerController {

    private final TripPlannerService tripPlannerService;
    private final GovernedCalls governed;

    public TripPlannerController(TripPlannerService tripPlannerService, GovernedCalls governed) {
        this.tripPlannerService = tripPlannerService;
        this.governed = governed;
    }

    @GetMapping("/plan-trip")
    public TripPlan getTripPlans(@RequestParam @NotBlank @Size(max = ApiLimits.MAX_TEXT) String message,
                                 Authentication authentication) {
        return governed.run(authentication, "trips.plan", List.of(message),
                inputs -> tripPlannerService.getTripPlan(inputs.get(0)));
    }

    @GetMapping("/trip-spots")
    public List<String> getTripSpots(@RequestParam @NotBlank @Size(max = ApiLimits.MAX_TEXT) String message,
                                     Authentication authentication) {
        return governed.run(authentication, "trips.spots", List.of(message),
                inputs -> tripPlannerService.getBestSpots(inputs.get(0)));
    }

    @GetMapping("/trip-guide")
    public Map<String, Object> getTripGuide(@RequestParam @NotBlank @Size(max = ApiLimits.MAX_TEXT) String message,
                                            Authentication authentication) {
        return governed.run(authentication, "trips.guide", List.of(message),
                inputs -> tripPlannerService.getTripGuide(inputs.get(0)));
    }

    @GetMapping("/complete-trip-plan")
    public List<TripPlan> getCompleteTripPlan(@RequestParam @NotBlank @Size(max = ApiLimits.MAX_TEXT) String message,
                                              Authentication authentication) {
        return governed.run(authentication, "trips.complete", List.of(message),
                inputs -> tripPlannerService.getCompleteTrip(inputs.get(0)));
    }
}
