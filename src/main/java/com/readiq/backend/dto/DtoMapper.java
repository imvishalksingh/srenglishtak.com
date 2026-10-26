package com.readiq.backend.dto;

import java.util.*;

/** Compatibility adapter for SQL projections/services. Nested DTOs retain supplied fields. */
public final class DtoMapper {
  private DtoMapper() {}

  public static Map<String, Object> body(Payload input) {
    var result = new LinkedHashMap<String, Object>();
    input.values().forEach((key, value) -> result.put(key, convert(value)));
    return result;
  }

  private static Object convert(Object value) {
    if (value instanceof Payload p) return body(p);
    if (value instanceof List<?> list) return list.stream().map(DtoMapper::convert).toList();
    return value;
  }
}
