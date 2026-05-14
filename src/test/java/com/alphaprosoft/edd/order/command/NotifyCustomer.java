package com.alphaprosoft.edd.order.command;

import java.util.UUID;

import com.alphaprosoft.edd.Command;
import com.alphaprosoft.edd.CommandId;

public record NotifyCustomer(UUID id, UUID customerId, String message) implements Command {
    public static final CommandId<NotifyCustomer> CMD_ID = CommandId.of("notify-customer", NotifyCustomer.class);
}
