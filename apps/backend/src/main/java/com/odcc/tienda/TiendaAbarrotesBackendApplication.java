package com.odcc.tienda;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TiendaAbarrotesBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(TiendaAbarrotesBackendApplication.class, args);
	}

}
