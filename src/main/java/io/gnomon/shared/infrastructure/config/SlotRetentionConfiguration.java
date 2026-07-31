package io.gnomon.shared.infrastructure.config;

import io.gnomon.shared.domain.model.SlotRetentionPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class SlotRetentionConfiguration {

  @Bean
  SlotRetentionPolicy slotRetentionPolicy(SlotRetentionProperties properties) {
    return properties.toPolicy();
  }
}
