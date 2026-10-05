package com.stockheatmap.jobs;

/** What a refresh wrote; returned by the Lambda so it shows up in invocation results and logs. */
public record RefreshSummary(String file, int entries, double freshRatio) {}
