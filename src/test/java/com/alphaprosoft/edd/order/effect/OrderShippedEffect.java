package com.alphaprosoft.edd.order.effect;

import java.util.List;
import java.util.UUID;

import com.alphaprosoft.edd.CommandEnvelope;
import com.alphaprosoft.edd.Context;
import com.alphaprosoft.edd.EventFxHandler;
import com.alphaprosoft.edd.order.Services;
import com.alphaprosoft.edd.order.command.NotifyCustomerCommand;
import com.alphaprosoft.edd.order.event.OrderShippedEvent;

public final class OrderShippedEffect implements EventFxHandler<OrderShippedEvent> {

    @Override
    public List<CommandEnvelope<?>> fx(Context ctx, OrderShippedEvent event) {
        return List.of(CommandEnvelope.on(
                Services.NOTIFICATION_SVC,
                new NotifyCustomerCommand(
                        UUID.randomUUID(),
                        event.id(),
                        "Order shipped: " + event.id() + " tracking " + event.trackingNumber())));
    }
}
