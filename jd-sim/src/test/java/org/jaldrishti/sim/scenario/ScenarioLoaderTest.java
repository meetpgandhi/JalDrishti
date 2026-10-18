package org.jaldrishti.sim.scenario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioLoaderTest {

    private final ScenarioLoader loader = new ScenarioLoader();

    @Test
    @DisplayName("Loads S1 aug26-as-happened.yaml successfully")
    void loadsAug26AsHappenedScenario() throws IOException {
        File file = new File("../scenarios/aug26-as-happened.yaml");
        if (!file.exists()) {
            file = new File("scenarios/aug26-as-happened.yaml");
        }

        Scenario scenario = loader.load(file);

        assertThat(scenario.scenario()).isEqualTo("aug26-as-happened");
        assertThat(scenario.corridor()).isEqualTo("bhote-trishuli-narayani");
        assertThat(scenario.seed()).isEqualTo(42);
        assertThat(scenario.stations()).containsKey("RSW");
        assertThat(scenario.stations()).containsKey("SYP");
        assertThat(scenario.stations()).containsKey("BTW");
        assertThat(scenario.stations()).containsKey("MLK");
        assertThat(scenario.stations()).containsKey("DVG");

        // Verify pinned reading for RSW
        Scenario.StationConfig rsw = scenario.stations().get("RSW");
        assertThat(rsw.pinned()).isNotEmpty();
        assertThat(rsw.pinned().get(0).t()).isEqualTo("08:40");
        assertThat(rsw.pinned().get(0).level()).isEqualTo(1.62);
        assertThat(rsw.pinned().get(0).tag()).isEqualTo("P");

        // Verify bookmarks
        assertThat(scenario.bookmarks()).isNotEmpty();
        assertThat(scenario.bookmarks()).anyMatch(b -> b.at().equals("08:53"));
    }
}
