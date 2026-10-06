import { Match } from "aws-cdk-lib/assertions";
import { describe, expect, it } from "vitest";
import { actionsOf, statementsFor, synth } from "./helpers";

const template = synth();

describe("login (Cognito)", () => {
  it("lets only the admin create users", () => {
    template.hasResourceProperties("AWS::Cognito::UserPool", {
      AdminCreateUserConfig: { AllowAdminCreateUserOnly: true },
      UsernameAttributes: ["email"],
      MfaConfiguration: "OPTIONAL",
      EnabledMfas: ["SOFTWARE_TOKEN_MFA"],
      UserPoolTier: "LITE",
    });
  });

  it("uses a public browser client with the code flow", () => {
    template.hasResourceProperties("AWS::Cognito::UserPoolClient", {
      GenerateSecret: false,
      AllowedOAuthFlows: ["code"],
      AllowedOAuthScopes: Match.arrayWith(["openid", "email"]),
      CallbackURLs: Match.arrayWith(["http://localhost:5173"]),
      LogoutURLs: Match.arrayWith(["http://localhost:5173"]),
    });
  });
});

describe("HTTP API", () => {
  it("requires a JWT on GET /market-data", () => {
    template.hasResourceProperties("AWS::ApiGatewayV2::Route", { RouteKey: "GET /market-data", AuthorizationType: "JWT" });
    template.hasResourceProperties("AWS::ApiGatewayV2::Authorizer", { AuthorizerType: "JWT" });
  });

  it("allows CORS only from the site", () => {
    template.hasResourceProperties("AWS::ApiGatewayV2::Api", {
      CorsConfiguration: Match.objectLike({ AllowOrigins: ["http://localhost:5173"], AllowHeaders: ["authorization", "content-type"] }),
    });
  });

  it("routes to the live alias of get-market-data", () => {
    const integrations = Object.values(template.findResources("AWS::ApiGatewayV2::Integration"));
    const uri = JSON.stringify(integrations.map((i) => i.Properties.IntegrationUri));
    const aliases = Object.entries(template.findResources("AWS::Lambda::Alias")).map(([id]) => id);

    expect(aliases.some((id) => uri.includes(`"${id}"`))).toBe(true);
    template.hasResourceProperties("AWS::Lambda::Function", {
      FunctionName: "stock-heatmap-get-market-data",
      Timeout: 10,
      Environment: { Variables: Match.objectLike({ spring_cloud_function_definition: "getMarketData" }) },
    });
  });

  it("lets get-market-data only read the four data files", () => {
    const statements = statementsFor(template, "stock-heatmap-get-market-data");
    const actions = statements.flatMap(actionsOf);
    const resources = JSON.stringify(statements.map((s) => s.Resource));

    expect(actions.every((a) => a.startsWith("s3:GetObject") || a.startsWith("s3:GetBucket") || a.startsWith("s3:List"))).toBe(true);
    for (const key of ["stocks.json", "crypto.json", "baselines.json", "metadata.json"]) expect(resources).toContain(`/${key}`);
    expect(resources).not.toContain("symbols.json");
    expect(actions.some((a) => a.startsWith("ssm:") || a.startsWith("dynamodb:") || a.startsWith("s3:Put"))).toBe(false);
  });

  it("outputs the API URL, the web config, and how to create the user", () => {
    const outputs = template.toJSON().Outputs as Record<string, unknown>;
    expect(Object.keys(outputs)).toEqual(expect.arrayContaining(["ApiUrl", "WebConfig", "CreateUserCommand"]));
  });
});
