package com.stockheatmap.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Whether the US stock market was open when stocks.json was written. */
public record Market(@JsonProperty("isOpen") boolean isOpen) {}
