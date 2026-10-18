package org.jaldrishti.adapter.gemini;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NumericGuardTest {

    @Test
    void convertsDevanagariDigitsToAscii() {
        String nepaliWithDigits = "समय ०९:१६ देखि ०९:३६ सम्म, काम १ पुरा गर्नुहोस्";
        String normalized = NumericGuard.normalizeDevanagariDigits(nepaliWithDigits);

        assertThat(normalized).isEqualTo("समय 09:16 देखि 09:36 सम्म, काम 1 पुरा गर्नुहोस्");
    }

    @Test
    void passesWhenAllExpectedTokensArePresent() {
        String nepali = "जलदृष्टि चेतावनी - Betrawati: ०९:१६ देखि ०९:३६ बीच बाढी। कार्य: CLOSE_INTAKE १०:०० भित्र।";
        NumericGuard.GuardVerification result = NumericGuard.verify(
                nepali,
                List.of("09:16", "09:36", "10:00"),
                List.of("CLOSE_INTAKE")
        );

        assertThat(result.passed()).isTrue();
        assertThat(result.reason()).isEqualTo("Verification passed");
    }

    @Test
    void failsWhenRequiredArrivalTimeIsMissing() {
        String text = "जलदृष्टि चेतावनी - Betrawati: ०९:१६ बीच बाढी। कार्य: CLOSE_INTAKE";
        NumericGuard.GuardVerification result = NumericGuard.verify(
                text,
                List.of("09:16", "09:36"), // 09:36 missing!
                List.of("CLOSE_INTAKE")
        );

        assertThat(result.passed()).isFalse();
        assertThat(result.reason()).contains("Missing required time from payload: 09:36");
    }

    @Test
    void failsWhenHallucinatedTimeIsIntroduced() {
        String text = "जलदृष्टि चेतावनी - Betrawati: ०९:१६ देखि ०९:३६ बीच बाढी। अर्को बाढी ११:४५ मा आउँछ। कार्य: CLOSE_INTAKE";
        NumericGuard.GuardVerification result = NumericGuard.verify(
                text,
                List.of("09:16", "09:36"),
                List.of("CLOSE_INTAKE")
        );

        assertThat(result.passed()).isFalse();
        assertThat(result.reason()).contains("Unrecognized/hallucinated time in text: 11:45");
    }

    @Test
    void failsWhenActionCodeIsMissing() {
        String text = "जलदृष्टि चेतावनी - Betrawati: ०९:१६ देखि ०९:३६ बीच बाढी।";
        NumericGuard.GuardVerification result = NumericGuard.verify(
                text,
                List.of("09:16", "09:36"),
                List.of("CLOSE_INTAKE")
        );

        assertThat(result.passed()).isFalse();
        assertThat(result.reason()).contains("Missing required action code from payload: CLOSE_INTAKE");
    }
}
