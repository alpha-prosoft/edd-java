package com.alphaprosoft.edd.order.effect;

import java.util.List;
import java.util.UUID;

import com.alphaprosoft.edd.CommandEnvelope;
import com.alphaprosoft.edd.Context;
import com.alphaprosoft.edd.EventFxHandler;
import com.alphaprosoft.edd.order.command.ShipOrder;
import com.alphaprosoft.edd.order.event.PaymentConfirmed;

public final class PaymentConfirmedEffect implements EventFxHandler<PaymentConfirmed> {

    @Override
    public List<CommandEnvelope<?>> fx(Context ctx, PaymentConfirmed event) {
        return List.of(CommandEnvelope.local(new ShipOrder(UUID.randomUUID(), event.id(), "TRACK-" + event.id())));
    }
}
