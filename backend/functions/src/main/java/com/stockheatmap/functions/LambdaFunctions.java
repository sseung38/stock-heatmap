package com.stockheatmap.functions;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.stockheatmap.jobs.ApiResult;
import com.stockheatmap.jobs.MarketDataService;
import com.stockheatmap.jobs.RefreshBaselinesJob;
import com.stockheatmap.jobs.RefreshCryptoJob;
import com.stockheatmap.jobs.RefreshMetadataJob;
import com.stockheatmap.jobs.RefreshStocksJob;
import com.stockheatmap.jobs.RefreshSummary;
import com.stockheatmap.jobs.WatchlistService;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * One bean per Lambda. The bean name is what each Lambda's spring_cloud_function_definition
 * selects. Scheduled jobs ignore their input (the scheduler sends "{}"); an exception propagates
 * so Lambda records the run as failed and the CloudWatch alarm can fire.
 */
@Configuration(proxyBeanMethods = false)
class LambdaFunctions {

  @Bean
  Function<Map<String, Object>, RefreshSummary> refreshStocks(RefreshStocksJob job) {
    return event -> job.run();
  }

  @Bean
  Function<Map<String, Object>, RefreshSummary> refreshCrypto(RefreshCryptoJob job) {
    return event -> job.run();
  }

  @Bean
  Function<Map<String, Object>, RefreshSummary> refreshBaselines(RefreshBaselinesJob job) {
    return event -> job.run();
  }

  @Bean
  Function<Map<String, Object>, RefreshSummary> refreshMetadata(RefreshMetadataJob job) {
    return event -> job.run();
  }

  /** GET /market-data (JWT-authorized by API Gateway before this runs). */
  @Bean
  Function<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> getMarketData(MarketDataService service, JsonMapper mapper) {
    return event -> ApiResponses.json(200, service.load(), ApiResponses.header(event.getHeaders(), "accept-encoding"), mapper);
  }

  /**
   * GET / PUT /watchlist. The user is the JWT's "sub" claim, which API Gateway's authorizer has
   * already verified, so one user can never read or write another user's list.
   */
  @Bean
  Function<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> watchlist(WatchlistService service, JsonMapper mapper) {
    return event -> {
      String acceptEncoding = ApiResponses.header(event.getHeaders(), "accept-encoding");
      String userId = subject(event);
      if (userId == null) {
        return ApiResponses.json(401, Map.of("error", "Unauthorized", "invalid", List.of()), acceptEncoding, mapper);
      }
      ApiResult result = service.handle(event.getRequestContext().getHttp().getMethod(), userId, body(event));
      return ApiResponses.json(result.status(), result.body(), acceptEncoding, mapper);
    };
  }

  private static String subject(APIGatewayV2HTTPEvent event) {
    var context = event.getRequestContext();
    if (context == null || context.getAuthorizer() == null || context.getAuthorizer().getJwt() == null) return null;
    Map<String, String> claims = context.getAuthorizer().getJwt().getClaims();
    return claims == null ? null : claims.get("sub");
  }

  /** API Gateway base64-encodes bodies it doesn't consider text. */
  private static String body(APIGatewayV2HTTPEvent event) {
    String body = event.getBody();
    if (body == null || !event.getIsBase64Encoded()) return body;
    return new String(Base64.getDecoder().decode(body), StandardCharsets.UTF_8);
  }
}
