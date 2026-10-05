package com.stockheatmap.integrations.secrets;

/** Looks up an API key. Implementations: .env locally, SSM Parameter Store on AWS. */
@FunctionalInterface
public interface SecretsProvider {

  String get(SecretName name);
}
