import { Match } from "aws-cdk-lib/assertions";
import { expect, it } from "vitest";
import { synth } from "./helpers";

const template = synth();

it("serves the site over HTTPS only", () => {
  template.hasResourceProperties("AWS::CloudFront::Distribution", {
    DistributionConfig: Match.objectLike({
      DefaultRootObject: "index.html",
      DefaultCacheBehavior: Match.objectLike({ ViewerProtocolPolicy: "redirect-to-https" }),
    }),
  });
});

it("answers unknown paths with the app (single-page app routing)", () => {
  template.hasResourceProperties("AWS::CloudFront::Distribution", {
    DistributionConfig: Match.objectLike({
      CustomErrorResponses: Match.arrayWith([
        Match.objectLike({ ErrorCode: 403, ResponseCode: 200, ResponsePagePath: "/index.html", ErrorCachingMinTTL: 0 }),
        Match.objectLike({ ErrorCode: 404, ResponseCode: 200, ResponsePagePath: "/index.html", ErrorCachingMinTTL: 0 }),
      ]),
    }),
  });
});

it("keeps the site bucket private behind Origin Access Control", () => {
  template.resourceCountIs("AWS::CloudFront::OriginAccessControl", 1);
  const buckets = Object.values(template.findResources("AWS::S3::Bucket"));
  expect(buckets.length).toBe(2); // data + site
  for (const bucket of buckets) {
    expect(bucket.Properties.PublicAccessBlockConfiguration).toEqual({
      BlockPublicAcls: true, BlockPublicPolicy: true, IgnorePublicAcls: true, RestrictPublicBuckets: true,
    });
  }
});

it("lets Cognito and the API accept the CloudFront address and localhost", () => {
  const client = Object.values(template.findResources("AWS::Cognito::UserPoolClient"))[0]!;
  const callbacks = JSON.stringify(client.Properties.CallbackURLs);
  const api = Object.values(template.findResources("AWS::ApiGatewayV2::Api"))[0]!;
  const origins = JSON.stringify(api.Properties.CorsConfiguration.AllowOrigins);

  for (const value of [callbacks, origins]) {
    expect(value).toContain("http://localhost:5173");
    expect(value).toMatch(/"https:\/\/",\{"Fn::GetAtt":\["Site\w+","DomainName"\]\}/);
  }
});

it("publishes config.json and the app, and outputs the site URL", () => {
  expect(Object.keys(template.findResources("Custom::CDKBucketDeployment")).length).toBe(2);
  expect(Object.keys(template.toJSON().Outputs)).toContain("SiteUrl");
});
