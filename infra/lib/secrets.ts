import { Stack } from "aws-cdk-lib";
import * as iam from "aws-cdk-lib/aws-iam";

/** SSM parameter names, matching SecretName.java. Values are uploaded once with `aws ssm put-parameter`. */
export const SSM_PARAMS = {
  alpacaKeyId: "stock-heatmap/alpaca-key-id",
  alpacaSecret: "stock-heatmap/alpaca-secret",
  finnhubKey: "stock-heatmap/finnhub-key",
  coingeckoKey: "stock-heatmap/coingecko-key",
} as const;

export type SecretParam = keyof typeof SSM_PARAMS;

/** Lets a function decrypt exactly these parameters (SsmSecretsProvider calls GetParameter per key). */
export function grantSecrets(grantee: iam.IGrantable, stack: Stack, names: SecretParam[]): void {
  grantee.grantPrincipal.addToPrincipalPolicy(
    new iam.PolicyStatement({
      actions: ["ssm:GetParameter"],
      resources: names.map((n) => stack.formatArn({ service: "ssm", resource: "parameter", resourceName: SSM_PARAMS[n] })),
    }),
  );
}
