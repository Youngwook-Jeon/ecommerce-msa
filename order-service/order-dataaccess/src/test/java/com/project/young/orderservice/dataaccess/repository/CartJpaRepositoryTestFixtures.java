package com.project.young.orderservice.dataaccess.repository;

import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

public final class CartJpaRepositoryTestFixtures {

  private CartJpaRepositoryTestFixtures() {
  }

  public static void truncateCartTables(TestEntityManager testEntityManager) {
    testEntityManager.getEntityManager()
        .createNativeQuery("""
            TRUNCATE TABLE cart_items, carts RESTART IDENTITY CASCADE
            """)
        .executeUpdate();
    testEntityManager.flush();
    testEntityManager.clear();
  }
}
