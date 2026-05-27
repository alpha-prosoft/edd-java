package com.alphaprosoft.edd.order;

import com.alphaprosoft.edd.command.CommandId;
import com.alphaprosoft.edd.command.EventId;
import com.alphaprosoft.edd.core.Module;
import com.alphaprosoft.edd.order.command.CancelOrderCommand;
import com.alphaprosoft.edd.order.command.CancelOrderHandler;
import com.alphaprosoft.edd.order.command.ConfirmPaymentCommand;
import com.alphaprosoft.edd.order.command.ConfirmPaymentHandler;
import com.alphaprosoft.edd.order.command.PlaceOrderCommand;
import com.alphaprosoft.edd.order.command.PlaceOrderHandler;
import com.alphaprosoft.edd.order.command.ShipOrderCommand;
import com.alphaprosoft.edd.order.command.ShipOrderHandler;
import com.alphaprosoft.edd.order.effect.PaymentConfirmedEffect;
import com.alphaprosoft.edd.order.event.OrderCancelledEvent;
import com.alphaprosoft.edd.order.event.OrderPlacedEvent;
import com.alphaprosoft.edd.order.event.OrderShippedEvent;
import com.alphaprosoft.edd.order.event.PaymentConfirmedEvent;
import com.alphaprosoft.edd.order.query.GetCustomerQuery;
import com.alphaprosoft.edd.order.query.GetOrderQuery;
import com.alphaprosoft.edd.order.query.GetProductQuery;
import com.alphaprosoft.edd.query.QueryId;

public final class OrderModule {

  public static final CommandId<PlaceOrderCommand> PLACE_ORDER =
      CommandId.of("place-order", PlaceOrderCommand.class);
  public static final CommandId<ConfirmPaymentCommand> CONFIRM_PAYMENT =
      CommandId.of("confirm-payment", ConfirmPaymentCommand.class);
  public static final CommandId<CancelOrderCommand> CANCEL_ORDER =
      CommandId.of("cancel-order", CancelOrderCommand.class);
  public static final CommandId<ShipOrderCommand> SHIP_ORDER =
      CommandId.of("ship-order", ShipOrderCommand.class);

  public static final EventId<OrderPlacedEvent> ORDER_PLACED =
      EventId.of("order-placed", OrderPlacedEvent.class);
  public static final EventId<PaymentConfirmedEvent> PAYMENT_CONFIRMED =
      EventId.of("payment-confirmed", PaymentConfirmedEvent.class);
  public static final EventId<OrderCancelledEvent> ORDER_CANCELLED =
      EventId.of("order-cancelled", OrderCancelledEvent.class);
  public static final EventId<OrderShippedEvent> ORDER_SHIPPED =
      EventId.of("order-shipped", OrderShippedEvent.class);

  public static final QueryId<GetOrderQuery, OrderAggregate> GET_ORDER =
      QueryId.of("get-order", GetOrderQuery.class, OrderAggregate.class);
  public static final QueryId<GetCustomerQuery, Customer> GET_CUSTOMER =
      QueryId.of("get-customer", GetCustomerQuery.class, Customer.class);
  public static final QueryId<GetProductQuery, Product> GET_PRODUCT =
      QueryId.of("get-product", GetProductQuery.class, Product.class);

  public static Module<OrderAggregate> register() {
    return Module.builder(OrderAggregate.class)
        .regCmd(
            PLACE_ORDER,
            spec ->
                spec.handler(PlaceOrderHandler.class)
                    .dep(OrderDeps.CUSTOMER, (_, cmd) -> new GetCustomerQuery(cmd.customerId()))
                    .dep(OrderDeps.PRODUCT, (_, cmd) -> new GetProductQuery(cmd.productId()))
                    .build())
        .regCmd(
            CONFIRM_PAYMENT,
            spec ->
                spec.handler(ConfirmPaymentHandler.class)
                    .dep(OrderDeps.CURRENT_ORDER, (_, cmd) -> new GetOrderQuery(cmd.orderId()))
                    .id((_, cmd) -> cmd.orderId())
                    .build())
        .regCmd(
            CANCEL_ORDER,
            spec ->
                spec.handler(CancelOrderHandler.class)
                    .dep(OrderDeps.CURRENT_ORDER, (_, cmd) -> new GetOrderQuery(cmd.orderId()))
                    .id((_, cmd) -> cmd.orderId())
                    .build())
        .regCmd(
            SHIP_ORDER,
            spec ->
                spec.handler(ShipOrderHandler.class)
                    .dep(OrderDeps.CURRENT_ORDER, (_, cmd) -> new GetOrderQuery(cmd.orderId()))
                    .id((_, cmd) -> cmd.orderId())
                    .build())
        .regApply(ORDER_PLACED, OrderAggregate::placed)
        .regApply(PAYMENT_CONFIRMED, OrderAggregate::paid)
        .regApply(ORDER_CANCELLED, OrderAggregate::cancelled)
        .regApply(ORDER_SHIPPED, OrderAggregate::shipped)
        .regFx(PAYMENT_CONFIRMED, new PaymentConfirmedEffect())
        // the order aggregate is owned here, so its read query lives in the module
        .regQuery(GET_ORDER, (ctx, q) -> ctx.<OrderAggregate>getAggregate(q.id()).orElse(null))
        .build();
  }

  private OrderModule() {}
}
