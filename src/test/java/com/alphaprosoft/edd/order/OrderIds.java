package com.alphaprosoft.edd.order;

import com.alphaprosoft.edd.CommandId;
import com.alphaprosoft.edd.EventId;
import com.alphaprosoft.edd.QueryId;
import com.alphaprosoft.edd.order.command.CancelOrder;
import com.alphaprosoft.edd.order.command.ConfirmPayment;
import com.alphaprosoft.edd.order.command.PlaceOrder;
import com.alphaprosoft.edd.order.command.ShipOrder;
import com.alphaprosoft.edd.order.event.OrderCancelled;
import com.alphaprosoft.edd.order.event.OrderPlaced;
import com.alphaprosoft.edd.order.event.OrderShipped;
import com.alphaprosoft.edd.order.event.PaymentConfirmed;
import com.alphaprosoft.edd.order.query.GetCustomer;
import com.alphaprosoft.edd.order.query.GetOrder;
import com.alphaprosoft.edd.order.query.GetProduct;

public final class OrderIds {

    public static final CommandId<PlaceOrder> PLACE_ORDER = CommandId.of("place-order", PlaceOrder.class);
    public static final CommandId<ConfirmPayment> CONFIRM_PAYMENT =
            CommandId.of("confirm-payment", ConfirmPayment.class);
    public static final CommandId<CancelOrder> CANCEL_ORDER = CommandId.of("cancel-order", CancelOrder.class);
    public static final CommandId<ShipOrder> SHIP_ORDER = CommandId.of("ship-order", ShipOrder.class);

    public static final EventId<OrderPlaced> ORDER_PLACED = EventId.of("order-placed", OrderPlaced.class);
    public static final EventId<PaymentConfirmed> PAYMENT_CONFIRMED =
            EventId.of("payment-confirmed", PaymentConfirmed.class);
    public static final EventId<OrderCancelled> ORDER_CANCELLED = EventId.of("order-cancelled", OrderCancelled.class);
    public static final EventId<OrderShipped> ORDER_SHIPPED = EventId.of("order-shipped", OrderShipped.class);

    public static final QueryId<GetOrder, OrderAggregate> GET_ORDER =
            QueryId.of("get-order", GetOrder.class, OrderAggregate.class);
    public static final QueryId<GetCustomer, Customer> GET_CUSTOMER =
            QueryId.of("get-customer", GetCustomer.class, Customer.class);
    public static final QueryId<GetProduct, Product> GET_PRODUCT =
            QueryId.of("get-product", GetProduct.class, Product.class);

    private OrderIds() {}
}
