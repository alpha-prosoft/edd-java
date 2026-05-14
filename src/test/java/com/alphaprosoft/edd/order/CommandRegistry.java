package com.alphaprosoft.edd.order;

import com.alphaprosoft.edd.CommandId;
import com.alphaprosoft.edd.EventId;
import com.alphaprosoft.edd.order.command.CancelOrderCommand;
import com.alphaprosoft.edd.order.command.ConfirmPaymentCommand;
import com.alphaprosoft.edd.order.command.PlaceOrderCommand;
import com.alphaprosoft.edd.order.command.ShipOrderCommand;
import com.alphaprosoft.edd.order.event.OrderCancelledEvent;
import com.alphaprosoft.edd.order.event.OrderPlacedEvent;
import com.alphaprosoft.edd.order.event.OrderShippedEvent;
import com.alphaprosoft.edd.order.event.PaymentConfirmedEvent;

public final class CommandRegistry {

    public static final CommandId<PlaceOrderCommand> PLACE_ORDER = CommandId.of("place-order", PlaceOrderCommand.class);
    public static final CommandId<ConfirmPaymentCommand> CONFIRM_PAYMENT =
            CommandId.of("confirm-payment", ConfirmPaymentCommand.class);
    public static final CommandId<CancelOrderCommand> CANCEL_ORDER =
            CommandId.of("cancel-order", CancelOrderCommand.class);
    public static final CommandId<ShipOrderCommand> SHIP_ORDER = CommandId.of("ship-order", ShipOrderCommand.class);

    public static final EventId<OrderPlacedEvent> ORDER_PLACED = EventId.of("order-placed", OrderPlacedEvent.class);
    public static final EventId<PaymentConfirmedEvent> PAYMENT_CONFIRMED =
            EventId.of("payment-confirmed", PaymentConfirmedEvent.class);
    public static final EventId<OrderCancelledEvent> ORDER_CANCELLED =
            EventId.of("order-cancelled", OrderCancelledEvent.class);
    public static final EventId<OrderShippedEvent> ORDER_SHIPPED = EventId.of("order-shipped", OrderShippedEvent.class);

    private CommandRegistry() {}
}
