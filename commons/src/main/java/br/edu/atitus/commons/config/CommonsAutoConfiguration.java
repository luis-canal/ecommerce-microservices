package br.edu.atitus.commons.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(ServletCommonsConfiguration.class)
public class CommonsAutoConfiguration {
}