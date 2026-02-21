package me.samuelh2005.lite_economy.commands;

import java.util.List;

public final class CommandSuggestionUtil {
    private CommandSuggestionUtil() {
    }

    public static List<String> quoteAll(List<String> values) {
        return values.stream().map(CommandSuggestionUtil::quote).toList();
    }

    private static String quote(String value) {
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }
}
