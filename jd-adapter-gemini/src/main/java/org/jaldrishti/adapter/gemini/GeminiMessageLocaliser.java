package org.jaldrishti.adapter.gemini;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import org.jaldrishti.application.port.out.GeminiMessageLocaliserPort;
import org.jaldrishti.domain.ArrivalBand;
import org.jaldrishti.domain.DispatchedAction;
import org.jaldrishti.domain.DutyHolder;
import org.jaldrishti.domain.DutyRule;
import org.jaldrishti.domain.Incident;
import org.jaldrishti.domain.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Gemini-powered message localiser with strict NumericGuard verification and
 * deterministic template fallback.
 */
public class GeminiMessageLocaliser implements GeminiMessageLocaliserPort {

    private static final Logger log = LoggerFactory.getLogger(GeminiMessageLocaliser.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.of("Asia/Kathmandu"));

    private final ReviewedTemplateEngine templateEngine;
    private final String modelId;
    private final String location;
    private final boolean vertexEnabled;

    public GeminiMessageLocaliser(ReviewedTemplateEngine templateEngine, String modelId, String location, boolean vertexEnabled) {
        this.templateEngine = templateEngine != null ? templateEngine : new ReviewedTemplateEngine();
        this.modelId = modelId != null ? modelId : "gemini-3.6-flash";
        this.location = location != null ? location : "asia-south1";
        this.vertexEnabled = vertexEnabled;
    }

    @Override
    public String localise(
            DispatchedAction action,
            DutyHolder holder,
            Incident incident,
            ArrivalBand band,
            DutyRule rule,
            Language language,
            boolean isNonRain
    ) {
        String siteName = band != null ? band.siteName() : (holder.siteId() != null ? holder.siteId() : "Corridor");
        String etaEarliest = band != null ? TIME_FMT.format(band.earliestArrival()) : null;
        String etaLatest = band != null ? TIME_FMT.format(band.latestArrival()) : null;
        String deadlineStr = action.deadlineAt() != null ? TIME_FMT.format(action.deadlineAt()) : "NOW";
        String actionCode = rule.actionCode();
        String ref = action.id().toString().substring(0, 8);

        List<String> expectedTimes = new ArrayList<>();
        if (etaEarliest != null) expectedTimes.add(etaEarliest);
        if (etaLatest != null) expectedTimes.add(etaLatest);
        if (action.deadlineAt() != null) expectedTimes.add(deadlineStr);

        List<String> expectedCodes = new ArrayList<>();
        if (actionCode != null) expectedCodes.add(actionCode);

        if (vertexEnabled) {
            try {
                String candidate = callGeminiVertex(action, holder, incident, band, rule, language, isNonRain,
                        siteName, etaEarliest, etaLatest, deadlineStr, actionCode, ref);

                NumericGuard.GuardVerification verification = NumericGuard.verify(candidate, expectedTimes, expectedCodes);
                if (verification.passed()) {
                    log.info("NumericGuard PASSED for action {} to holder {}", action.id(), holder.id());
                    return verification.normalizedText();
                } else {
                    log.warn("NumericGuard FAILED for action {}: {}. Falling back to reviewed template.",
                            action.id(), verification.reason());
                }
            } catch (Exception ex) {
                log.warn("Gemini localisation failed or timed out for action {}: {}. Falling back to template.",
                        action.id(), ex.getMessage());
            }
        }

        // Deterministic template fallback
        return templateEngine.format(
                incident.level(),
                language != null ? language : holder.language(),
                siteName,
                etaEarliest,
                etaLatest,
                actionCode,
                deadlineStr,
                ref,
                isNonRain
        );
    }

    private String callGeminiVertex(
            DispatchedAction action,
            DutyHolder holder,
            Incident incident,
            ArrivalBand band,
            DutyRule rule,
            Language language,
            boolean isNonRain,
            String siteName,
            String etaEarliest,
            String etaLatest,
            String deadlineStr,
            String actionCode,
            String ref
    ) throws Exception {
        Client client = Client.builder()
                .vertexAI(true)
                .location(location)
                .build();

        String prompt = String.format(
                "You format emergency flood directives for duty holders along the Bhote Koshi - Trishuli - Narayani river corridor in Nepal.\n" +
                "Language: %s\n" +
                "Directives must be concise, authoritative, and strictly preserve all numbers, times, and codes verbatim.\n" +
                "Payload:\n" +
                "- Incident Level: %s\n" +
                "- Site: %s\n" +
                "- Earliest Arrival: %s\n" +
                "- Latest Arrival: %s\n" +
                "- Action Code: %s\n" +
                "- Action Description: %s\n" +
                "- Deadline: %s\n" +
                "- Non-rain debris flood: %s\n" +
                "- Ref: %s\n\n" +
                "Keep under 480 characters. Output only the final message text.",
                language, incident.level(), siteName,
                etaEarliest != null ? etaEarliest : "N/A",
                etaLatest != null ? etaLatest : "N/A",
                actionCode, rule.actionTextEn(), deadlineStr, isNonRain, ref
        );

        GenerateContentConfig config = GenerateContentConfig.builder()
                .temperature(0.0f)
                .build();

        GenerateContentResponse response = client.models.generateContent(modelId, prompt, config);
        return response.text();
    }
}
