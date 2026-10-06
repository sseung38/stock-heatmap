import { CfnOutput, Duration, Stack } from "aws-cdk-lib";
import * as apigw from "aws-cdk-lib/aws-apigatewayv2";
import { HttpJwtAuthorizer } from "aws-cdk-lib/aws-apigatewayv2-authorizers";
import { HttpLambdaIntegration } from "aws-cdk-lib/aws-apigatewayv2-integrations";
import * as cognito from "aws-cdk-lib/aws-cognito";
import { Construct } from "constructs";
import type { DataResources } from "./data";
import { makeSpringFunction, type SpringFunction } from "./lambda";

export interface ApiProps {
  data: DataResources;
  codePath: string;
  /** Origins allowed to sign in and call the API (the site, and the Vite dev server). */
  siteUrls: string[];
}

/**
 * Login and the HTTP API (spec §4.2, §5). Cognito issues JWTs; API Gateway checks them before a
 * Lambda ever runs, so the functions never see an unauthenticated request.
 */
export class Api extends Construct {
  readonly httpApi: apigw.HttpApi;
  readonly authorizer: HttpJwtAuthorizer;
  readonly userPool: cognito.UserPool;
  readonly client: cognito.UserPoolClient;
  readonly marketData: SpringFunction;
  readonly watchlist: SpringFunction;

  constructor(scope: Construct, id: string, props: ApiProps) {
    super(scope, id);
    const stack = Stack.of(this);
    const { dataBucket, table } = props.data;

    // --- Cognito: one user (you), created by an admin command; no public sign-up ---
    this.userPool = new cognito.UserPool(this, "Users", {
      userPoolName: "stock-heatmap-users",
      selfSignUpEnabled: false,
      signInAliases: { email: true },
      autoVerify: { email: true },
      mfa: cognito.Mfa.OPTIONAL,
      mfaSecondFactor: { otp: true, sms: false },
      accountRecovery: cognito.AccountRecovery.EMAIL_ONLY,
      featurePlan: cognito.FeaturePlan.LITE,
    });
    this.userPool.addDomain("Domain", { cognitoDomain: { domainPrefix: `stock-heatmap-${stack.account}` } });
    // A browser app can't keep a secret, so the client has none and uses the code flow (with PKCE).
    this.client = this.userPool.addClient("Web", {
      generateSecret: false,
      preventUserExistenceErrors: true,
      supportedIdentityProviders: [cognito.UserPoolClientIdentityProvider.COGNITO],
      oAuth: {
        flows: { authorizationCodeGrant: true },
        scopes: [cognito.OAuthScope.OPENID, cognito.OAuthScope.EMAIL],
        callbackUrls: props.siteUrls,
        logoutUrls: props.siteUrls,
      },
      accessTokenValidity: Duration.hours(1),
      idTokenValidity: Duration.hours(1),
      refreshTokenValidity: Duration.days(30),
    });
    const issuer = this.userPool.userPoolProviderUrl; // https://cognito-idp.<region>.amazonaws.com/<poolId>

    // --- HTTP API with a JWT authorizer ---
    this.httpApi = new apigw.HttpApi(this, "Http", {
      apiName: "stock-heatmap",
      corsPreflight: {
        allowOrigins: props.siteUrls,
        allowHeaders: ["authorization", "content-type"],
        allowMethods: [apigw.CorsHttpMethod.GET, apigw.CorsHttpMethod.PUT],
        maxAge: Duration.hours(1),
      },
    });
    this.authorizer = new HttpJwtAuthorizer("Jwt", issuer, { jwtAudience: [this.client.userPoolClientId] });

    const environment = { DATA_BUCKET: dataBucket.bucketName, WATCHLIST_TABLE: table.tableName };
    this.marketData = makeSpringFunction(this, "GetMarketData", {
      name: "get-market-data", functionBean: "getMarketData", timeout: Duration.seconds(10), codePath: props.codePath, environment,
    });
    for (const key of ["stocks.json", "crypto.json", "baselines.json", "metadata.json"]) dataBucket.grantRead(this.marketData.fn, key);
    this.route("/market-data", apigw.HttpMethod.GET, this.marketData);

    // Each user's list, keyed by their Cognito "sub"; symbols.json validates new items.
    this.watchlist = makeSpringFunction(this, "Watchlist", {
      name: "watchlist", functionBean: "watchlist", timeout: Duration.seconds(10), codePath: props.codePath, environment,
    });
    table.grantReadWriteData(this.watchlist.fn);
    dataBucket.grantRead(this.watchlist.fn, "symbols.json");
    this.route("/watchlist", apigw.HttpMethod.GET, this.watchlist);
    this.route("/watchlist", apigw.HttpMethod.PUT, this.watchlist);

    new CfnOutput(stack, "ApiUrl", { value: this.httpApi.apiEndpoint });
    new CfnOutput(stack, "WebConfig", {
      value: stack.toJsonString({ apiUrl: this.httpApi.apiEndpoint, auth: { authority: issuer, clientId: this.client.userPoolClientId } }),
    });
    new CfnOutput(stack, "CreateUserCommand", {
      value: `aws cognito-idp admin-create-user --user-pool-id ${this.userPool.userPoolId} --username <EMAIL> --user-attributes Name=email,Value=<EMAIL> Name=email_verified,Value=true`,
    });
  }

  route(path: string, method: apigw.HttpMethod, target: SpringFunction) {
    this.httpApi.addRoutes({
      path,
      methods: [method],
      integration: new HttpLambdaIntegration(`${target.fn.node.id}Integration${method}`, target.alias),
      authorizer: this.authorizer,
    });
  }
}
