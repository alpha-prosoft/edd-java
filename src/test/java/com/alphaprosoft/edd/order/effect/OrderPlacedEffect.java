package com.alphaprosoft.edd.order.effect;

import java.util.List;
import java.util.UUID;

import com.alphaprosoft.edd.CommandEnvelope;
import com.alphaprosoft.edd.Context;
import com.alphaprosoft.edd.EventFxHandler;
import com.alphaprosoft.edd.order.Services;
import com.alphaprosoft.edd.order.command.NotifyCustomerCommand;
import com.alphaprosoft.edd.order.event.OrderPlacedEvent;

public final class OrderPlacedEffect implements EventFxHandler<OrderPlacedEvent> {

    @Override
    public List<CommandEnvelope<?>> fx(Context ctx, OrderPlacedEvent event) {
        return List.of(CommandEnvelope.on(
                Services.NOTIFICATION_SVC,
                new NotifyCustomerCommand(UUID.randomUUID(), event.customerId(), "Order placed: " + event.id())));
    }
}
