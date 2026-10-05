package com.stockheatmap.local;

import com.stockheatmap.jobs.RefreshBaselinesJob;
import com.stockheatmap.jobs.RefreshCryptoJob;
import com.stockheatmap.jobs.RefreshMetadataJob;
import com.stockheatmap.jobs.RefreshStocksJob;
import com.stockheatmap.jobs.RefreshSummary;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

/** {@code --refresh=<stocks|crypto|baselines|metadata|all>}: runs refresh jobs once, writing to .data/. */
@Component
class RefreshCommand implements ApplicationRunner, ExitCodeGenerator {

  private final Map<String, Supplier<RefreshSummary>> jobs = new LinkedHashMap<>();
  private int exitCode;

  RefreshCommand(RefreshMetadataJob metadata, RefreshBaselinesJob baselines, RefreshStocksJob stocks,
      RefreshCryptoJob crypto) {
    // Order matters for "all": stocks and baselines need the S&P list from metadata.
    jobs.put("metadata", metadata::run);
    jobs.put("baselines", baselines::run);
    jobs.put("stocks", stocks::run);
    jobs.put("crypto", crypto::run);
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!args.containsOption("refresh")) {
      return;
    }
    String requested = args.getOptionValues("refresh").getFirst();
    List<String> names = requested.equals("all") ? List.copyOf(jobs.keySet()) : List.of(requested);
    for (String name : names) {
      Supplier<RefreshSummary> job = jobs.get(name);
      if (job == null) {
        System.err.println("Unknown job '" + name + "'. Use one of " + jobs.keySet() + " or all.");
        exitCode = 2;
        return;
      }
      System.out.println("Running " + name + "...");
      try {
        System.out.println("OK " + name + ": " + job.get());
      } catch (RuntimeException e) {
        System.out.println("FAIL " + name + ": " + e.getMessage());
        exitCode = 1;
      }
    }
  }

  @Override
  public int getExitCode() {
    return exitCode;
  }
}
