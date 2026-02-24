package me.samuelh2005.lite_economy.commands.arguments;

import java.util.UUID;

/**
 * Implemented by data objects that can be identified by a UUID and have a human-readable display
 * name, allowing them to be used as suggestions in {@link NamedUUIDArgumentType}.
 */
public interface UUIDNameable {
    UUID getId();
    String getDisplayName();
}
