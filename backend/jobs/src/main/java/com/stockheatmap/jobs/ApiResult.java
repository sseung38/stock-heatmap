package com.stockheatmap.jobs;

/** An HTTP status and the object to send as the JSON body. */
public record ApiResult(int status, Object body) {}
