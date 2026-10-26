package com.readiq.backend.dto;

import static com.readiq.backend.api.Input.uuid;

import java.util.*;

/**
 * Flat IDs preserve Flutter/dashboard JSON; storage locations are redacted by the catalog service.
 */
public record BookResponse(
    UUID id,
    String title,
    String author,
    String description,
    String category,
    String format_type,
    String file_url,
    String cover_url,
    Boolean is_visible,
    UUID uploaded_by,
    String created_at,
    int price_paise,
    String currency,
    String android_product_id) {
  public static BookResponse from(Map<String, Object> row) {
    return new BookResponse(
        uuid(row.get("id")),
        s(row, "title"),
        s(row, "author"),
        s(row, "description"),
        s(row, "category"),
        s(row, "format_type"),
        s(row, "file_url"),
        s(row, "cover_url"),
        (Boolean) row.get("is_visible"),
        row.get("uploaded_by") == null ? null : uuid(row.get("uploaded_by")),
        s(row, "created_at"),
        ((Number) row.getOrDefault("price_paise", 0)).intValue(),
        s(row, "currency"),
        s(row, "android_product_id"));
  }

  private static String s(Map<String, Object> row, String key) {
    return Objects.toString(row.get(key), null);
  }
}
