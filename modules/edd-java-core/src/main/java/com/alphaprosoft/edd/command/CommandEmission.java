package com.alphaprosoft.edd.command;

/**
 * A single thing a command handler emits. A handler returns a flat {@code List<CommandEmission>}
 * mixing any of these — mirroring edd-core, where one handler commonly emits an {@link Event} and
 * reserves an {@link Identity} together.
 *
 * <p>The dispatcher partitions the list: any {@link Rejection} fails the whole command (events
 * discarded); otherwise events and identities are collected into the success.
 */
public sealed interface CommandEmission permits Event, Identity, Rejection {}
