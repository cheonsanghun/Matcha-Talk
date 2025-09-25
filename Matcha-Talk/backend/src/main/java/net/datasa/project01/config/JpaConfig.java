package net.datasa.project01.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "net.datasa.project01.repository")
@EntityScan(basePackages = "net.datasa.project01.domain.entity")
public class JpaConfig {
}