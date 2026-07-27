package com.odcc.tienda.modules.reports.adapter.config;

import com.odcc.tienda.modules.reports.application.port.in.ReportUseCases;
import com.odcc.tienda.modules.reports.application.port.out.ReportRepositoryPort;
import com.odcc.tienda.modules.reports.application.usecase.ReportService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ReportConfiguration {

    @Bean
    ReportUseCases reportUseCases(ReportRepositoryPort repository) {
        return new ReportService(repository);
    }
}
