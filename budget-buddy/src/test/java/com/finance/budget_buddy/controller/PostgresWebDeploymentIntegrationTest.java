package com.finance.budget_buddy.controller;

import org.junit.jupiter.api.Tag;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs the same JWT/isolation/import contract against real PostgreSQL in CI. */
@Tag("postgres")
@Testcontainers
class PostgresWebDeploymentIntegrationTest extends WebDeploymentIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry props) {
        props.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        props.add("spring.datasource.username", POSTGRES::getUsername);
        props.add("spring.datasource.password", POSTGRES::getPassword);
        props.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        props.add("spring.datasource.hikari.connection-init-sql", () -> "SET lock_timeout = '5s'");
    }
}
