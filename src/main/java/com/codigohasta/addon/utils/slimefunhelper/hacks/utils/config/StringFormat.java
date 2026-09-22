package com.codigohasta.addon.utils.slimefunhelper.hacks.utils.config;

import java.util.*;
import java.util.regex.*;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import com.codigohasta.addon.utils.slimefunhelper.utils.ChatUtils;

/** Source named-placeholder formatter; its data is persisted by the Meteor JSON editor. */
public final class StringFormat {
    private final List<String> formattingArgument;
    private final String formatString;
    private final boolean colorString;
    private static final Pattern PATTERN = Pattern.compile("\\{[^{}]*\\}");
    public StringFormat(List<String> arguments, String format) { this(arguments, format, false); }
    public StringFormat(List<String> arguments, String format, boolean color) {
        formattingArgument = arguments; formatString = format; colorString = color;
    }
    public List<String> formattingArgument() { return formattingArgument; }
    public String formatString() { return formatString; }
    public boolean colorString() { return colorString; }
    public StringFormat withFormatString(String format) { return new StringFormat(formattingArgument, format, colorString); }
    public String format(String... arguments) {
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < Math.min(arguments.length, formattingArgument.size()); i++) values.put(formattingArgument.get(i), arguments[i]);
        return format(values);
    }
    public String format(Map<String, String> values) {
        Matcher matcher = PATTERN.matcher(formatString);
        StringBuilder result = new StringBuilder();
        int end = 0;
        while (matcher.find()) {
            result.append(formatString, end, matcher.start());
            String token = matcher.group();
            result.append(values.getOrDefault(token.substring(1, token.length() - 1), token));
            end = matcher.end();
        }
        return result.append(formatString, end, formatString.length()).toString();
    }
    public MutableText formatText(Object... arguments) {
        Map<String, Object> values = new HashMap<>();
        for (int i = 0; i < Math.min(arguments.length, formattingArgument.size()); i++) values.put(formattingArgument.get(i), arguments[i]);
        return formatText(values);
    }
    public MutableText formatText(Map<String, Object> values) {
        MutableText result = Text.empty();
        Matcher matcher = PATTERN.matcher(formatString);
        int end = 0;
        while (matcher.find()) {
            result.append(ChatUtils.stringToText(formatString.substring(end, matcher.start())));
            String token = matcher.group();
            Object value = values.getOrDefault(token.substring(1, token.length() - 1), token);
            result.append(value instanceof Text text ? text : ChatUtils.stringToText(String.valueOf(value)));
            end = matcher.end();
        }
        return result.append(ChatUtils.stringToText(formatString.substring(end)));
    }
    @Override public boolean equals(Object other) {
        return other instanceof StringFormat f && Objects.equals(formattingArgument, f.formattingArgument)
            && Objects.equals(formatString, f.formatString) && colorString == f.colorString;
    }
    @Override public int hashCode() { return Objects.hash(formattingArgument, formatString, colorString); }
}
