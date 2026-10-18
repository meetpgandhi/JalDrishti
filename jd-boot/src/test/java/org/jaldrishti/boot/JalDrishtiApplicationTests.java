package org.jaldrishti.boot;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

/**
 * Smoke test: verifies the Spring Boot application context loads without errors.
 *
 * <p>Uses the {@code sim} profile so no live Pub/Sub or production database connection
 * is required.
 *
 * <p>This is a Spring Boot context load test — it does NOT start a server
 * (webEnvironment = NONE). It will fail fast if any bean definition or wiring is broken.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.flyway.enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
        }
)
@ActiveProfiles("sim")
class JalDrishtiApplicationTests {

    @MockBean
    private DSLContext dslContext;

    @MockBean
    private DataSource dataSource;

    @Test
    void contextLoads() {
        // If this test passes, the application context assembled correctly.
        // All beans (domain rules, application services, adapter stubs, REST controllers) wired.
    }
}
