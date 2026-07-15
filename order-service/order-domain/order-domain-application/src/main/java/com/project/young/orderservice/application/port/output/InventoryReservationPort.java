package com.project.young.orderservice.application.port.output;

import com.project.young.orderservice.application.port.output.view.ReserveInventoryLineView;
import com.project.young.orderservice.application.port.output.view.ReserveInventoryResultView;

import java.util.List;
import java.util.UUID;

/**
 * Soft-hold inventory operations via product-service internal API.
 * {@code checkoutId} is the order/checkout correlation id (typically {@code orderId}).
 */
public interface InventoryReservationPort {

  /**
   * @param checkoutId idempotency key for the checkout attempt
   * @param lines distinct variant lines to reserve; must not be empty
   */
  ReserveInventoryResultView reserve(UUID checkoutId, List<ReserveInventoryLineView> lines);

  void confirm(UUID checkoutId);

  void release(UUID checkoutId);
}
