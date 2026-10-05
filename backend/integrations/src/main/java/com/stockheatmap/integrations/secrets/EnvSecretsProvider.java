package com.stockheatmap.integrations.secrets;

import org.springframework.core.env.Environment;

/** Local runs: keys come from Spring's Environment, which imports the git-ignored .env file. */
public class EnvSecretsProvider implements SecretsProvider {

  private final Environment environment;

  public EnvSecretsProvider(Environment environment) {
    this.environment = environment;
  }

  @Override
  public String get(SecretName name) {
    String value = environment.getProperty(name.envName());
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Missing secret: " + name.envName());
    }
    return value.trim();
  }
}
