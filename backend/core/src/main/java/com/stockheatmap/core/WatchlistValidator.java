package com.stockheatmap.core;

import com.stockheatmap.core.ValidationResult.Invalid;
import com.stockheatmap.core.ValidationResult.Valid;
import com.stockheatmap.core.model.SymbolsFile;
import com.stockheatmap.core.model.WatchlistItem;
import com.stockheatmap.core.model.WatchlistItem.CryptoItem;
import com.stockheatmap.core.model.WatchlistItem.StockItem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/** Validates and normalizes a watchlist sent by the browser (spec §5.2). */
public final class WatchlistValidator {

  public static final int MAX_ITEMS = 100;

  static final String INVALID_BODY = "Invalid request body";
  static final String TOO_MANY = "Watchlist can hold at most " + MAX_ITEMS + " items";
  static final String UNKNOWN_OR_DUPLICATE = "Unknown or duplicate items";

  private WatchlistValidator() {}

  public static ValidationResult validate(JsonNode body, SymbolsFile symbols) {
    if (body == null || !body.isObject() || body.get("items") == null || !body.get("items").isArray()) {
      return new Invalid(INVALID_BODY, List.of());
    }
    List<WatchlistItem> items = new ArrayList<>();
    for (JsonNode node : body.get("items")) {
      Optional<WatchlistItem> item = parseItem(node);
      if (item.isEmpty()) {
        return new Invalid(INVALID_BODY, List.of());
      }
      items.add(item.get());
    }
    if (items.size() > MAX_ITEMS) {
      return new Invalid(TOO_MANY, List.of());
    }

    Set<String> validStocks = new HashSet<>(symbols.validStockSymbols());
    Set<String> validCoins = new HashSet<>(symbols.validCoinIds());
    Set<WatchlistItem> seen = new HashSet<>();
    List<WatchlistItem> offenders = new ArrayList<>();
    for (WatchlistItem item : items) {
      boolean known =
          switch (item) {
            case StockItem s -> validStocks.contains(s.symbol());
            case CryptoItem c -> validCoins.contains(c.id());
          };
      if (!known || !seen.add(identity(item))) {
        offenders.add(item);
      }
    }
    return offenders.isEmpty() ? new Valid(items) : new Invalid(UNKNOWN_OR_DUPLICATE, offenders);
  }

  /** Two crypto items with the same id are duplicates even if their display symbols differ. */
  private static WatchlistItem identity(WatchlistItem item) {
    return item instanceof CryptoItem c ? new CryptoItem(c.id(), "") : item;
  }

  private static Optional<WatchlistItem> parseItem(JsonNode node) {
    if (!node.isObject()) {
      return Optional.empty();
    }
    Optional<String> type = text(node, "type");
    if (type.isEmpty()) {
      return Optional.empty();
    }
    return switch (type.get()) {
      case "stock" -> text(node, "symbol").map(s -> new StockItem(s.toUpperCase(Locale.ROOT)));
      case "crypto" -> text(node, "id").flatMap(id -> text(node, "symbol")
          .map(sym -> new CryptoItem(id.toLowerCase(Locale.ROOT), sym.toUpperCase(Locale.ROOT))));
      default -> Optional.empty();
    };
  }

  /** The trimmed string value of a field, or empty when it is missing, not a string, or blank. */
  private static Optional<String> text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || !value.isString()) {
      return Optional.empty();
    }
    String trimmed = value.asString().trim();
    return trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed);
  }
}
