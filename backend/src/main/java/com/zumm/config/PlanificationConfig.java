package com.zumm.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Active la planification Spring, requise par la relecture a chaud de
 * {@code ConfigZumm.ini} (US-025, {@code ConfigurationMetier#relire}).
 */
@Configuration
@EnableScheduling
public class PlanificationConfig {
}
