package com.carddemo.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "carddemo")
public record LegacyDataProperties(String legacyDataDir, boolean seedUsers) {}
