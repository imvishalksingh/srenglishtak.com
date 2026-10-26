package com.readiq.backend.service;

import static com.readiq.backend.api.Input.*;

import com.readiq.backend.api.*;
import com.readiq.backend.data.Database;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
  private record Spec(String fields, String required, String order) {}

  private static final Map<String, Spec> SPECS =
      Map.of(
          "books",
              new Spec(
                  "title author description category format_type file_url cover_url is_visible"
                      + " price_paise currency android_product_id",
                  "title",
                  "created_at desc,id"),
          "grammar_units",
              new Spec(
                  "title description unit_order image_url", "title unit_order", "unit_order,id"),
          "grammar_lessons",
              new Spec(
                  "title content_type content_data lesson_order",
                  "title lesson_order",
                  "lesson_order,id"),
          "cbse_categories",
              new Spec("name description icon_url order_index", "name", "order_index,id"),
          "cbse_materials",
              new Spec(
                  "material_type title year description file_url thumbnail_url",
                  "title file_url",
                  "created_at desc,id"),
          "vocabulary",
              new Spec(
                  "word meaning example_sentence category", "word meaning", "created_at desc,id"),
          "daily_tips",
              new Spec("title content author image_url", "title content", "created_at desc,id"));
  private final Database db;
  private final StorageService storage;

  public CatalogService(Database db, StorageService storage) {
    this.db = db;
    this.storage = storage;
  }

  public List<Map<String, Object>> list(String table, String foreign, UUID parent) {
    Spec spec = spec(table);
    return db.list(
        "select * from "
            + table
            + (foreign == null ? "" : " where " + foreign + "=?")
            + " order by "
            + spec.order,
        foreign == null ? new Object[] {} : new Object[] {parent});
  }

  public List<Map<String, Object>> books(boolean admin) {
    var rows =
        db.list(
            "select * from books"
                + (admin ? "" : " where is_visible=true")
                + " order by created_at desc,id");
    if (!admin) rows.forEach(b -> b.put("file_url", null));
    return rows;
  }

  public Map<String, Object> book(UUID id, boolean admin) {
    var book = db.byId("books", id);
    if (!admin && !Boolean.TRUE.equals(book.get("is_visible"))) throw ApiException.missing();
    if (!admin) book.put("file_url", null);
    return book;
  }

  @Transactional
  public Map<String, Object> save(
      String table, UUID id, Map<String, Object> body, Map<String, Object> serverFields) {
    if (id != null) db.one("select id from " + table + " where id=? for update", id);
    var spec = spec(table);
    var values = fields(body, spec.fields.split(" "));
    for (String required : spec.required.split(" ")) {
      if (id == null || body.containsKey(required)) {
        Object v = values.get(required);
        if (v == null || (v instanceof String s && s.isBlank()))
          throw ApiException.bad(required + " is required");
      }
    }
    choice(values, "format_type", "pdf", "epub");
    choice(values, "content_type", "text", "pdf", "video");
    choice(values, "material_type", "model_paper", "pyq", "notes", "syllabus");
    if (table.equals("books")) {
      var previous = id == null ? Map.<String, Object>of() : db.byId("books", id);
      if (values.get("android_product_id") instanceof String product) {
        if (product.isBlank()) values.put("android_product_id", null);
        else if (!product.matches("[a-z][a-z0-9_.]{2,149}"))
          throw ApiException.bad("Invalid Android product ID");
      }
      if (id != null
          && values.containsKey("android_product_id")
          && !Objects.equals(values.get("android_product_id"), previous.get("android_product_id"))
          && db.count("select count(*) from book_purchases where book_id=?", id) > 0)
        throw new ApiException(409, "Product mapping cannot change after a purchase");
      if (values.containsKey("currency") && !"INR".equals(values.get("currency")))
        throw ApiException.bad("Only INR is supported");
      Object price = values.getOrDefault("price_paise", previous.getOrDefault("price_paise", 0));
      int amount = integer(price, "price_paise", 0, 1000000);
      if (amount > 0) {
        String file =
            Objects.toString(values.getOrDefault("file_url", previous.get("file_url")), "");
        if (!storage.privateFile(file))
          throw ApiException.bad("Paid books require a PDF/EPUB uploaded to private book storage");
        storage.requirePrivateBucket();
        storage.requirePrivateFile(file);
        if (id != null)
          storage.requirePublicCopyRetired(Objects.toString(previous.get("file_url"), null));
      }
    }
    if (serverFields != null) values.putAll(serverFields);
    return id == null ? db.insert(table, values) : db.update(table, id, values);
  }

  @Transactional
  public List<Map<String, Object>> bulk(
      String table, List<Map<String, Object>> rows, Map<String, Object> extra) {
    if (rows.isEmpty() || rows.size() > 500)
      throw ApiException.bad("Supply between 1 and 500 records");
    return rows.stream().map(b -> save(table, null, b, extra)).toList();
  }

  @Transactional
  public void delete(String table, UUID id) {
    spec(table);
    if (table.equals("books")
        && (db.count("select count(*) from book_purchases where book_id=?", id) > 0
            || db.count("select count(*) from purchase_orders where book_id=?", id) > 0))
      throw new ApiException(409, "This book has purchase records; hide it instead of deleting it");
    db.delete(table, id);
  }

  private Spec spec(String table) {
    Spec s = SPECS.get(table);
    if (s == null) throw new IllegalArgumentException("Invalid catalog");
    return s;
  }
}
