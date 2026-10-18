package org.jaldrishti.application.port.out;

import org.jaldrishti.domain.ArrivalBand;
import org.jaldrishti.domain.DispatchedAction;
import org.jaldrishti.domain.DutyHolder;
import org.jaldrishti.domain.DutyRule;
import org.jaldrishti.domain.Incident;
import org.jaldrishti.domain.Language;

/**
 * Outbound port for Gemini-powered message localisation.
 * Translates and phrases action directives into Nepali, Hindi, or English.
 */
public interface GeminiMessageLocaliserPort {

    /**
     * Localises an action directive for a duty holder.
     *
     * @param action       Dispatched action
     * @param holder       Target duty holder
     * @param incident     Active incident
     * @param band         Arrival band for the duty holder's site (if available)
     * @param rule         Associated duty rule
     * @param language     Target language
     * @param isNonRain    Whether this is a non-rain / debris flood
     * @return Formatted message text
     */
    String localise(
            DispatchedAction action,
            DutyHolder holder,
            Incident incident,
            ArrivalBand band,
            DutyRule rule,
            Language language,
            boolean isNonRain
    );
}
