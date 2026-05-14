package com.alphaprosoft.edd.order;

import com.alphaprosoft.edd.Application;
import com.alphaprosoft.edd.CommandSpec;
import com.alphaprosoft.edd.Deps;
import com.alphaprosoft.edd.order.command.CancelOrder;
import com.alphaprosoft.edd.order.command.CancelOrderHandler;
import com.alphaprosoft.edd.order.command.ConfirmPayment;
import com.alphaprosoft.edd.order.command.ConfirmPaymentHandler;
import com.alphaprosoft.edd.order.command.PlaceOrder;
import com.alphaprosoft.edd.order.command.PlaceOrderHandler;
import com.alphaprosoft.edd.order.command.ShipOrder;
import com.alphaprosoft.edd.order.command.ShipOrderHandler;
import com.alphaprosoft.edd.order.effect.OrderPlacedEffect;
import com.alphaprosoft.edd.order.effect.OrderShippedEffect;
import com.alphaprosoft.edd.order.effect.PaymentConfirmedEffect;
import com.alphaprosoft.edd.order.event.OrderEvent;
import com.alphaprosoft.edd.order.query.GetCustomer;
import com.alphaprosoft.edd.order.query.GetOrder;
import com.alphaprosoft.edd.order.query.GetProduct;

public final class OrderModule {

    public static Application.Builder register(Application.Builder app) {
        return app.regCmd(CommandSpec.builder(OrderIds.PLACE_ORDER, OrderAggregate.class)
                        .handler(new PlaceOrderHandler())
                        .deps(Deps.<PlaceOrder>builder()
                                .reg(OrderDeps.CUSTOMER, (_, cmd) -> new GetCustomer(cmd.customerId()))
                                .reg(OrderDeps.PRODUCT, (_, cmd) -> new GetProduct(cmd.productId()))
                                .build())
                        .build())
                .regCmd(CommandSpec.builder(OrderIds.CONFIRM_PAYMENT, OrderAggregate.class)
                        .handler(new ConfirmPaymentHandler())
                        .deps(Deps.<ConfirmPayment>builder()
                                .reg(OrderDeps.CURRENT_ORDER, (_, cmd) -> new GetOrder(cmd.orderId()))
                                .build())
                        .idFn((_, cmd) -> cmd.orderId())
                        .build())
                .regCmd(CommandSpec.builder(OrderIds.CANCEL_ORDER, OrderAggregate.class)
                        .handler(new CancelOrderHandler())
                        .deps(Deps.<CancelOrder>builder()
                                .reg(OrderDeps.CURRENT_ORDER, (_, cmd) -> new GetOrder(cmd.orderId()))
                                .build())
                        .idFn((_, cmd) -> cmd.orderId())
                        .build())
                .regCmd(CommandSpec.builder(OrderIds.SHIP_ORDER, OrderAggregate.class)
                        .handler(new ShipOrderHandler())
                        .deps(Deps.<ShipOrder>builder()
                                .reg(OrderDeps.CURRENT_ORDER, (_, cmd) -> new GetOrder(cmd.orderId()))
                                .build())
                        .idFn((_, cmd) -> cmd.orderId())
                        .build())
                .regEvent(OrderIds.ORDER_PLACED, OrderAggregate.class, OrderModule::apply)
                .regEvent(OrderIds.PAYMENT_CONFIRMED, OrderAggregate.class, OrderModule::apply)
                .regEvent(OrderIds.ORDER_CANCELLED, OrderAggregate.class, OrderModule::apply)
                .regEvent(OrderIds.ORDER_SHIPPED, OrderAggregate.class, OrderModule::apply)
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
