package com.stockheatmap.integrations.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;

class SecretsProviderTest {

  @Test
  void envProviderReadsEnvironmentVariables() {
    var env = new MockEnvironment().withProperty("ALPACA_KEY_ID", "abc");

    assertThat(new EnvSecretsProvider(env).get(SecretName.ALPACA_KEY_ID)).isEqualTo("abc");
  }

  @Test
  void envProviderNamesTheMissingSecret() {
    var env = new MockEnvironment().withProperty("FINNHUB_KEY", " ");

    assertThatThrownBy(() -> new EnvSecretsProvider(env).get(SecretName.FINNHUB_KEY))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Missing secret: FINNHUB_KEY");
  }

  @Test
  void ssmProviderDecryptsOnFirstUseAndCaches() {
    SsmClient ssm = mock(SsmClient.class);
    when(ssm.getParameter(any(GetParameterRequest.class))).thenReturn(
        GetParameterResponse.builder().parameter(Parameter.builder().value("secret-value").build()).build());
    AtomicInteger clientsCreated = new AtomicInteger();
    var provider = new SsmSecretsProvider(() -> {
      clientsCreated.incrementAndGet();
      return ssm;
    });

    assertThat(clientsCreated).hasValue(0); // nothing happens at construction (SnapStart snapshot stays key-free)
    assertThat(provider.get(SecretName.ALPACA_SECRET)).isEqualTo("secret-value");
    assertThat(provider.get(SecretName.ALPACA_SECRET)).isEqualTo("secret-value");

    ArgumentCaptor<GetParameterRequest> request = ArgumentCaptor.forClass(GetParameterRequest.class);
    verify(ssm, times(1)).getParameter(request.capture());
    assertThat(request.getValue().name()).isEqualTo("/stock-heatmap/alpaca-secret");
    assertThat(request.getValue().withDecryption()).isTrue();
    assertThat(clientsCreated).hasValue(1);
  }

  @Test
  void ssmProviderNamesTheMissingParameter() {
    SsmClient ssm = mock(SsmClient.class);
    when(ssm.getParameter(any(GetParameterRequest.class))).thenThrow(ParameterNotFoundException.builder().build());

    assertThatThrownBy(() -> new SsmSecretsProvider(() -> ssm).get(SecretName.COINGECKO_KEY))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Missing secret: /stock-heatmap/coingecko-key");
  }
}
