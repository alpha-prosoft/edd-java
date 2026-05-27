package com.alphaprosoft.edd.order.effect;

import com.alphaprosoft.edd.command.Command;
import com.alphaprosoft.edd.command.EventFxHandler;
import com.alphaprosoft.edd.core.Context;
import com.alphaprosoft.edd.order.command.ShipOrderCommand;
import com.alphaprosoft.edd.order.event.PaymentConfirmedEvent;
import java.util.List;
import java.util.UUID;

public final class PaymentConfirmedEffect implements EventFxHandler<PaymentConfirmedEvent> {

  @Override
  public List<Command> fx(Context ctx, PaymentConfirmedEvent event) {
    return List.of(
        ShipOrderCommand.builder()
            .id(UUID.randomUUID())
            .orderId(event.id())
            .trackingNumber("TRACK-" + event.id())
            .build());
  }
}
