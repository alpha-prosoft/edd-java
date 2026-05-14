package com.alphaprosoft.edd.order.effect;

import java.util.List;
import java.util.UUID;

import com.alphaprosoft.edd.Command;
import com.alphaprosoft.edd.Context;
import com.alphaprosoft.edd.EventFxHandler;
import com.alphaprosoft.edd.order.command.ShipOrderCommand;
import com.alphaprosoft.edd.order.event.PaymentConfirmedEvent;

public final class PaymentConfirmedEffect implements EventFxHandler<PaymentConfirmedEvent> {

    @Override
    public List<Command> fx(Context ctx, PaymentConfirmedEvent event) {
        return List.of(new ShipOrderCommand(UUID.randomUUID(), event.id(), "TRACK-" + event.id()));
    }
}
