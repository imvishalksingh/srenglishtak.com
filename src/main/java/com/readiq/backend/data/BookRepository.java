package com.readiq.backend.data;

import com.readiq.backend.data.entity.BooksEntity;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookRepository extends JpaRepository<BooksEntity, UUID> {
  @org.springframework.data.jpa.repository.Query(
      "select b from BooksEntity b where b.android_product_id=:productId")
  Optional<BooksEntity> byAndroidProduct(String productId);
}
