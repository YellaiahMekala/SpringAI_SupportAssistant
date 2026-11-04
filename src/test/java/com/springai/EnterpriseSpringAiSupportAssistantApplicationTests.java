package com.springai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The application context needs PostgreSQL (Flyway runs at startup). Skipped when Docker is unavailable. */
@SpringBootTest
@Import(TestPostgresConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class EnterpriseSpringAiSupportAssistantApplicationTests {

	@Test
	void contextLoads() {
	}

}
