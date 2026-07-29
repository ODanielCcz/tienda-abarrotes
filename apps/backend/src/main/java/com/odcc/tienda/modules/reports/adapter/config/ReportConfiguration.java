package com.odcc.tienda.modules.reports.adapter.config;

import com.odcc.tienda.modules.reports.application.port.in.ReportUseCases;
import com.odcc.tienda.modules.reports.application.port.out.ReportRepositoryPort;
import com.odcc.tienda.modules.reports.application.usecase.ReportService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class ReportConfiguration {

    @Bean
    ReportUseCases reportUseCases(ReportRepositoryPort repository, Clock clock) {
        return new ReportService(repository, clock);
    }
}
