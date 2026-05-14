package com.alphaprosoft.edd.order;

import com.alphaprosoft.edd.Module;
import com.alphaprosoft.edd.order.command.CancelOrderHandler;
import com.alphaprosoft.edd.order.command.ConfirmPaymentHandler;
import com.alphaprosoft.edd.order.command.PlaceOrderHandler;
import com.alphaprosoft.edd.order.command.ShipOrderHandler;
import com.alphaprosoft.edd.order.effect.OrderPlacedEffect;
import com.alphaprosoft.edd.order.effect.OrderShippedEffect;
import com.alphaprosoft.edd.order.effect.PaymentConfirmedEffect;
import com.alphaprosoft.edd.order.event.OrderEvent;
import com.alphaprosoft.edd.order.query.GetCustomerQuery;
import com.alphaprosoft.edd.order.query.GetOrderQuery;
import com.alphaprosoft.edd.order.query.GetProductQuery;

public final class OrderModule {

    public static Module<OrderAggregate> register(Module<OrderAggregate> m) {
        return m.regCmd(
                        OrderIds.PLACE_ORDER,
                        spec -> spec.handler(new PlaceOrderHandler())
                                .dep(OrderDeps.CUSTOMER, (_, cmd) -> new GetCustomerQuery(cmd.customerId()))
                                .dep(OrderDeps.PRODUCT, (_, cmd) -> new GetProductQuery(cmd.productId()))
                                .build())
                .regCmd(
                        OrderIds.CONFIRM_PAYMENT,
                        spec -> spec.handler(new ConfirmPaymentHandler())
                                .dep(OrderDeps.CURRENT_ORDER, (_, cmd) -> new GetOrderQuery(cmd.orderId()))
                                .idFn((_, cmd) -> cmd.orderId())
                                .build())
                .regCmd(
                        OrderIds.CANCEL_ORDER,
                        spec -> spec.handler(new CancelOrderHandler())
                                .dep(OrderDeps.CURRENT_ORDER, (_, cmd) -> new GetOrderQuery(cmd.orderId()))
                                .idFn((_, cmd) -> cmd.orderId())
                                .build())
                .regCmd(
                        OrderIds.SHIP_ORDER,
                        spec -> spec.handler(new ShipOrderHandler())
                                .dep(OrderDeps.CURRENT_ORDER, (_, cmd) -> new GetOrderQuery(cmd.orderId()))
                                .idFn((_, cmd) -> cmd.orderId())
                                .build())
                .regEvent(OrderIds.ORDER_PLACED, OrderModule::apply)
                .regEvent(OrderIds.PAYMENT_CONFIRMED, OrderModule::apply)
                .regEvent(OrderIds.ORDER_CANCELLED, OrderModule::apply)
                .regEvent(OrderIds.ORDER_SHIPPED, OrderModule::apply)
                .regEventFx(OrderIds.ORDER_PLACED, new OrderPlacedEffect())
                .regEventFx(OrderIds.PAYMENT_CONFIRMED, new PaymentConfirmedEffect())
                .regEventFx(OrderIds.ORDER_SHIPPED, new OrderShippedEffect());
    }

    private static OrderAggregate apply(OrderAggregate agg, OrderEvent event) {
        OrderAggregate base = agg == null ? OrderAggregate.initial(event.id()) : agg;
        return base.applyEvent(event);
    }

    private OrderModule() {}
}
