import type { AppConfig } from "@sh/shared";
import { CfnOutput, Duration, RemovalPolicy, Stack } from "aws-cdk-lib";
import * as cloudfront from "aws-cdk-lib/aws-cloudfront";
import * as origins from "aws-cdk-lib/aws-cloudfront-origins";
import * as s3 from "aws-cdk-lib/aws-s3";
import * as s3deploy from "aws-cdk-lib/aws-s3-deployment";
import { Construct } from "constructs";
import path from "node:path";

export interface SiteProps {
  /** Vite build output (web/dist): index.html plus content-hashed files in assets/. */
  webDistPath: string;
}

/**
 * The React app on CloudFront (spec §4.3). The bucket stays private: CloudFront reads it through
 * Origin Access Control, and browsers only ever talk to CloudFront over HTTPS.
 */
export class Site extends Construct {
  readonly bucket: s3.Bucket;
  readonly distribution: cloudfront.Distribution;
  /** https://<id>.cloudfront.net — no trailing slash, matching window.location.origin. */
  readonly url: string;

  constructor(scope: Construct, id: string, private readonly props: SiteProps) {
    super(scope, id);
    this.bucket = new s3.Bucket(this, "Bucket", {
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      enforceSSL: true,
      removalPolicy: RemovalPolicy.DESTROY,
      autoDeleteObjects: true, // only build output lives here; it is re-uploaded on every deploy
    });
    this.distribution = new cloudfront.Distribution(this, "Distribution", {
      comment: "stock-heatmap site",
      defaultRootObject: "index.html",
      defaultBehavior: {
        origin: origins.S3BucketOrigin.withOriginAccessControl(this.bucket),
        viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
        cachePolicy: cloudfront.CachePolicy.CACHING_OPTIMIZED,
      },
      // S3 answers 403 for a missing key (CloudFront may not list the bucket); serve the app instead.
      errorResponses: [403, 404].map((httpStatus) => ({
        httpStatus, responseHttpStatus: 200, responsePagePath: "/index.html", ttl: Duration.seconds(0),
      })),
    });
    this.url = `https://${this.distribution.distributionDomainName}`;
    new CfnOutput(Stack.of(this), "SiteUrl", { value: this.url });
  }

  /** Uploads the built app and the runtime config.json (which needs the API and Cognito values). */
  deploy(config: AppConfig): void {
    // Hashed file names change whenever their content does, so browsers may cache them forever.
    new s3deploy.BucketDeployment(this, "DeployAssets", {
      sources: [s3deploy.Source.asset(path.join(this.props.webDistPath, "assets"))],
      destinationBucket: this.bucket,
      destinationKeyPrefix: "assets/",
      cacheControl: [s3deploy.CacheControl.fromString("public, max-age=31536000, immutable")],
      prune: false, // keep the previous build's files for tabs that are still open
    });
    // index.html and config.json keep their names, so browsers must re-check them on every visit.
    new s3deploy.BucketDeployment(this, "DeployEntry", {
      sources: [
        s3deploy.Source.asset(this.props.webDistPath, { exclude: ["assets", "assets/**"] }),
        s3deploy.Source.jsonData("config.json", config),
      ],
      destinationBucket: this.bucket,
      cacheControl: [s3deploy.CacheControl.noCache()],
      prune: false,
      distribution: this.distribution,
      distributionPaths: ["/index.html", "/config.json"],
    });
  }
}
