package io.gnomon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GnomonApplication {

  public static void main(String[] args) {
    SpringApplication.run(GnomonApplication.class, args);
  }
}
