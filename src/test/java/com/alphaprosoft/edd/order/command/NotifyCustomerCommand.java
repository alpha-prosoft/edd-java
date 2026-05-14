package com.alphaprosoft.edd.order.command;

import java.util.UUID;

import com.alphaprosoft.edd.Command;
import com.alphaprosoft.edd.CommandId;

public record NotifyCustomerCommand(UUID id, UUID customerId, String message) implements Command {
    public static final CommandId<NotifyCustomerCommand> CMD_ID =
            CommandId.of("notify-customer", NotifyCustomerCommand.class);
}
