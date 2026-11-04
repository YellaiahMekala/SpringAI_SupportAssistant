package com.springai.governance;

import com.springai.TestPostgresConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.springai.testsupport.TestFixtures.newSubject;
import static com.springai.testsupport.TestFixtures.token;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Without {@code app.metrics.public-scrape=true} the metrics endpoint is not open to anonymous callers. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfig.class)
@ActiveProfiles("demo")
@Testcontainers(disabledWithoutDocker = true)
class PrometheusScrapeClosedByDefaultTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void anonymousCallersAreRefused() throws Exception {
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
    }

    @Test
    void aSignedInCustomerIsNotLetInEither() throws Exception {
        mvc.perform(get("/actuator/prometheus").with(token(newSubject(), "customer")))
                .andExpect(status().is4xxClientError());
    }
}
