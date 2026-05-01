package com.legalcase.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
    // This class enables automatic timestamping for @CreatedDate and @LastModifiedDate
    // No additional code needed - the annotation does all the work
}