package com.odcc.tienda;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final DockerImageName POSTGRES_IMAGE =
        DockerImageName
            .parse("tienda-postgres:18.4-pgaudit18.0")
            .asCompatibleSubstituteFor("postgres");

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(POSTGRES_IMAGE)
            .withDatabaseName("tienda_abarrotes_test")
            .withUsername("tienda_test_owner")
            .withPassword("tienda_test_password")
            .withCommand(
                "postgres",
                "-c",
                "shared_preload_libraries=pgaudit",
                "-c",
                "pgaudit.log=role,ddl",
                "-c",
                "pgaudit.log_statement=off",
                "-c",
                "pgaudit.log_parameter=off"
            );
	}
}
