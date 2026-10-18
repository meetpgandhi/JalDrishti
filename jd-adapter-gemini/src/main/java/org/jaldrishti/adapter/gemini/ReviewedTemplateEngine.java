package org.jaldrishti.adapter.gemini;

import org.jaldrishti.domain.IncidentLevel;
import org.jaldrishti.domain.Language;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Loads reviewed message templates and formats them deterministically.
 * Serves as the guaranteed safety fallback whenever Gemini fails or NumericGuard triggers.
 */
public class ReviewedTemplateEngine {

    private final Properties properties = new Properties();

    public ReviewedTemplateEngine() {
        try (InputStream in = getClass().getResourceAsStream("/templates/messages.properties")) {
            if (in != null) {
                properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            // Log and fallback to hardcoded strings
        }
    }

    public String format(
            IncidentLevel level,
            Language language,
            String siteName,
            String etaEarliest,
            String etaLatest,
            String action,
            String deadline,
            String ref,
            boolean nonRain
    ) {
        String langKey = language != null ? language.name().toUpperCase() : "EN";
        if (!langKey.equals("NE") && !langKey.equals("HI")) {
            langKey = "EN";
        }

        String templateKey = (level != null ? level.name() : "WATCH") + "_" + langKey;
        String raw = properties.getProperty(templateKey);

        if (raw == null) {
            // Hardcoded safe fallbacks
            if (level == IncidentLevel.WARNING) {
                raw = "JALDRISHTI WARNING - {siteName}: sudden flood expected {etaEarliest}–{etaLatest}. Action: {action1} by {deadline1}. Ref {ref}";
            } else {
                raw = "JALDRISHTI WATCH - {siteName}: possible sudden flood. Earliest arrival {etaEarliest}. Action: {action1} by {deadline1}. Ref {ref}";
            }
        }

        String formatted = raw
                .replace("{siteName}", siteName != null ? siteName : "Corridor")
                .replace("{etaEarliest}", etaEarliest != null ? etaEarliest : "TBD")
                .replace("{etaLatest}", etaLatest != null ? etaLatest : "TBD")
                .replace("{action1}", action != null ? action : "Standby")
                .replace("{deadline1}", deadline != null ? deadline : "NOW")
                .replace("{ref}", ref != null ? ref : "");

        if (nonRain && level == IncidentLevel.WARNING) {
            String nonRainKey = "NON_RAIN_" + langKey;
            String nonRainNote = properties.getProperty(nonRainKey, " NOTE: Debris flood.");
            formatted = formatted + " " + nonRainNote;
        }

        return formatted.trim();
    }
}
