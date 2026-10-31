package com.readiq.backend.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile("northflank")
public class NorthflankRuntimeConfiguration {
  @Bean
  public static BeanFactoryPostProcessor checkRuntimeDatabaseUrl(Environment environment) {
    return factory -> {
      String url;
      try { url = environment.getRequiredProperty("spring.datasource.url"); }
      catch (IllegalArgumentException | IllegalStateException invalid) { throw missingUrl(); }
      if (!url.startsWith("jdbc:postgresql://") || url.contains("${") || url.contains("\""))
        throw missingUrl();
      // Hibernate reports the JDBC URL during startup. Keep passwords out before it initializes.
      if (java.util.regex.Pattern.compile("(?i)[?&](user|password)=").matcher(url).find()
          || url.substring("jdbc:postgresql://".length()).split("/", 2)[0].contains("@"))
        throw new IllegalStateException("Keep credentials out of DB_URL; set DB_USER and DB_PASSWORD separately");
    };
  }

  private static IllegalStateException missingUrl() {
    return new IllegalStateException("Set a valid JDBC DB_URL in the deployment runtime environment, not build arguments");
  }
}
