package com.alphaprosoft.edd.order.effect;

import java.util.List;
import java.util.UUID;

import com.alphaprosoft.edd.CommandEnvelope;
import com.alphaprosoft.edd.Context;
import com.alphaprosoft.edd.EventFxHandler;
import com.alphaprosoft.edd.order.Services;
import com.alphaprosoft.edd.order.command.NotifyCustomer;
import com.alphaprosoft.edd.order.event.OrderPlaced;

public final class OrderPlacedEffect implements EventFxHandler<OrderPlaced> {

    @Override
    public List<CommandEnvelope<?>> fx(Context ctx, OrderPlaced event) {
        return List.of(CommandEnvelope.on(
                Services.NOTIFICATION_SVC,
                new NotifyCustomer(UUID.randomUUID(), event.customerId(), "Order placed: " + event.id())));
    }
}
