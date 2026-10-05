package com.stockheatmap.integrations.secrets;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;

/**
 * AWS: keys are SecureString parameters in SSM Parameter Store. Each key is fetched (decrypted) the
 * first time it is needed, then cached for the life of the Lambda container. Nothing is fetched at
 * startup, so SnapStart snapshots never contain a key, and each Lambda asks only for the parameters
 * its IAM role allows.
 */
public class SsmSecretsProvider implements SecretsProvider {

  private final Supplier<SsmClient> clientFactory;
  private final Map<SecretName, String> cache = new ConcurrentHashMap<>();
  private volatile SsmClient client;

  public SsmSecretsProvider(Supplier<SsmClient> clientFactory) {
    this.clientFactory = clientFactory;
  }

  @Override
  public String get(SecretName name) {
    return cache.computeIfAbsent(name, this::fetch);
  }

  private String fetch(SecretName name) {
    try {
      return client()
          .getParameter(GetParameterRequest.builder().name(name.ssmName()).withDecryption(true).build())
          .parameter()
          .value();
    } catch (ParameterNotFoundException e) {
      throw new IllegalStateException("Missing secret: " + name.ssmName(), e);
    }
  }

  private SsmClient client() {
    if (client == null) {
      synchronized (this) {
        if (client == null) {
          client = clientFactory.get();
        }
      }
    }
    return client;
  }
}
