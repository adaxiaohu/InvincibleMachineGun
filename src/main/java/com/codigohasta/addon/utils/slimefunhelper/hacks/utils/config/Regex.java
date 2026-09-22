package com.codigohasta.addon.utils.slimefunhelper.hacks.utils.config;

public record Regex(String expression) implements java.util.function.Predicate<String> {
    public java.util.function.Predicate<String> asPredicate(){return this;}
    public boolean test(String value) {return java.util.regex.Pattern.matches(expression,value);}
public String regex(){return expression;}
}