import { Duration, Stack, TimeZone } from "aws-cdk-lib";
import * as scheduler from "aws-cdk-lib/aws-scheduler";
import * as targets from "aws-cdk-lib/aws-scheduler-targets";
import { Construct } from "constructs";
import type { DataResources } from "./data";
import { makeSpringFunction, type SpringFunction } from "./lambda";
import { grantSecrets } from "./secrets";

export interface JobsProps {
  data: DataResources;
  codePath: string;
}

/** The scheduled refresh Lambdas (spec §4.1). */
export class Jobs extends Construct {
  readonly stocks: SpringFunction;
  readonly crypto: SpringFunction;

  constructor(scope: Construct, id: string, props: JobsProps) {
    super(scope, id);
    const stack = Stack.of(this);
    const { dataBucket, table } = props.data;
    const environment = { DATA_BUCKET: dataBucket.bucketName, WATCHLIST_TABLE: table.tableName };

    // --- stocks: every 5 min, Mon–Fri 09:30–16:20 New York time ---
    this.stocks = makeSpringFunction(this, "RefreshStocks", {
      name: "refresh-stocks", functionBean: "refreshStocks", timeout: Duration.seconds(60), codePath: props.codePath, environment,
    });
    dataBucket.grantPut(this.stocks.fn, "stocks.json");
    dataBucket.grantRead(this.stocks.fn, "stocks.json");
    dataBucket.grantRead(this.stocks.fn, "metadata.json");
    table.grantReadData(this.stocks.fn);
    grantSecrets(this.stocks.fn, stack, ["alpacaKeyId", "alpacaSecret"]);
    this.schedule("StocksOpen", this.stocks, { minute: "30-55/5", hour: "9" });
    this.schedule("StocksDay", this.stocks, { minute: "0/5", hour: "10-15" });
    this.schedule("StocksClose", this.stocks, { minute: "0-20/5", hour: "16" }); // data is 15 min delayed

    // --- crypto: every 15 min, all week ---
    this.crypto = makeSpringFunction(this, "RefreshCrypto", {
      name: "refresh-crypto", functionBean: "refreshCrypto", timeout: Duration.seconds(60), codePath: props.codePath, environment,
    });
    dataBucket.grantPut(this.crypto.fn, "crypto.json");
    dataBucket.grantRead(this.crypto.fn, "crypto.json");
    table.grantReadData(this.crypto.fn);
    grantSecrets(this.crypto.fn, stack, ["coingeckoKey"]);
    this.scheduleRate("Crypto", this.crypto, Duration.minutes(15));
  }

  protected schedule(id: string, target: SpringFunction, cron: { minute: string; hour: string; weekDay?: string }) {
    new scheduler.Schedule(this, id, {
      schedule: scheduler.ScheduleExpression.cron({
        minute: cron.minute, hour: cron.hour, weekDay: cron.weekDay ?? "MON-FRI", timeZone: TimeZone.AMERICA_NEW_YORK,
      }),
      target: this.invoke(target),
    });
  }

  protected scheduleRate(id: string, target: SpringFunction, every: Duration) {
    new scheduler.Schedule(this, id, {
      schedule: scheduler.ScheduleExpression.expression(`rate(${every.toMinutes()} minutes)`, TimeZone.AMERICA_NEW_YORK),
      target: this.invoke(target),
    });
  }

  private invoke(target: SpringFunction) {
    return new targets.LambdaInvoke(target.alias, {
      input: scheduler.ScheduleTargetInput.fromObject({}),
      retryAttempts: 0,
    });
  }
}
