package com.alphaprosoft.edd.order;

import com.alphaprosoft.edd.Module;
import com.alphaprosoft.edd.order.command.CancelOrderHandler;
import com.alphaprosoft.edd.order.command.ConfirmPaymentHandler;
import com.alphaprosoft.edd.order.command.PlaceOrderHandler;
import com.alphaprosoft.edd.order.command.ShipOrderHandler;
import com.alphaprosoft.edd.order.effect.PaymentConfirmedEffect;
import com.alphaprosoft.edd.order.query.GetCustomerQuery;
import com.alphaprosoft.edd.order.query.GetOrderQuery;
import com.alphaprosoft.edd.order.query.GetProductQuery;

public final class OrderModule {

    public static Module<OrderAggregate> register(Module<OrderAggregate> m) {
        return m.regCmd(
                        CommandRegistry.PLACE_ORDER,
                        spec -> spec.handler(PlaceOrderHandler.class)
                                .dep(OrderDeps.CUSTOMER, (_, cmd) -> new GetCustomerQuery(cmd.customerId()))
                                .dep(OrderDeps.PRODUCT, (_, cmd) -> new GetProductQuery(cmd.productId()))
                                .build())
                .regCmd(
                        CommandRegistry.CONFIRM_PAYMENT,
                        spec -> spec.handler(ConfirmPaymentHandler.class)
                                .dep(OrderDeps.CURRENT_ORDER, (_, cmd) -> new GetOrderQuery(cmd.orderId()))
                                .id((_, cmd) -> cmd.orderId())
                                .build())
                .regCmd(
                        CommandRegistry.CANCEL_ORDER,
                        spec -> spec.handler(CancelOrderHandler.class)
                                .dep(OrderDeps.CURRENT_ORDER, (_, cmd) -> new GetOrderQuery(cmd.orderId()))
                                .id((_, cmd) -> cmd.orderId())
                                .build())
                .regCmd(
                        CommandRegistry.SHIP_ORDER,
                        spec -> spec.handler(ShipOrderHandler.class)
                                .dep(OrderDeps.CURRENT_ORDER, (_, cmd) -> new GetOrderQuery(cmd.orderId()))
                                .id((_, cmd) -> cmd.orderId())
                                .build())
                .regApply(CommandRegistry.ORDER_PLACED, OrderAggregate::placed)
                .regApply(CommandRegistry.PAYMENT_CONFIRMED, OrderAggregate::paid)
                .regApply(CommandRegistry.ORDER_CANCELLED, OrderAggregate::cancelled)
                .regApply(CommandRegistry.ORDER_SHIPPED, OrderAggregate::shipped)
                .regFx(CommandRegistry.PAYMENT_CONFIRMED, new PaymentConfirmedEffect());
    }

    private OrderModule() {}
}
