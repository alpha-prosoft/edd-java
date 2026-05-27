package com.alphaprosoft.edd.aws;

import com.alphaprosoft.edd.command.Command;
import com.alphaprosoft.edd.core.RequestMeta;

/** A decoded command plus the request meta to dispatch it with. */
public record Inbound(Command command, RequestMeta meta) {}
