package com.project.young.orderservice.dataaccess.support;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public final class SqlStatementCounter implements StatementInspector {

  private static final ThreadLocal<AtomicInteger> DELETE_STATEMENTS =
      ThreadLocal.withInitial(AtomicInteger::new);

  public static void reset() {
    DELETE_STATEMENTS.get().set(0);
  }

  public static int deleteCount() {
    return DELETE_STATEMENTS.get().get();
  }

  @Override
  public String inspect(String sql) {
    if (sql != null && sql.stripLeading().toLowerCase(Locale.ROOT).startsWith("delete")) {
      DELETE_STATEMENTS.get().incrementAndGet();
    }
    return sql;
  }
}
