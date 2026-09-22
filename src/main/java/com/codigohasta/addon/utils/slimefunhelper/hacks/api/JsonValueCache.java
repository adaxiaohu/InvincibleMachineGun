package com.codigohasta.addon.utils.slimefunhelper.hacks.api;

import com.google.gson.Gson;
import java.util.Objects;
import java.util.function.Predicate;

/** Keeps structured settings and their derived caches alive until their JSON changes. */
public final class JsonValueCache<V> {
    private final Gson gson;
    private final Class<?> type;
    private final Predicate<V> validator;
    private String lastJson;
    private V valid;

    public JsonValueCache(Gson gson, V initial, Predicate<V> validator) {
        this.gson = gson;
        this.type = initial.getClass();
        this.validator = validator;
        this.valid = initial;
        this.lastJson = gson.toJson(initial);
    }

    @SuppressWarnings("unchecked")
    public V get(String json) {
        if (!Objects.equals(lastJson, json)) {
            // Remember invalid edits too, so an unfinished editor value is not parsed every tick.
            lastJson = json;
            try {
                V parsed = (V) gson.fromJson(json, type);
                if (parsed != null && validator.test(parsed)) valid = parsed;
            } catch (RuntimeException ignored) {
                // Keep the most recent valid setting while the user edits the JSON.
            }
        }
        return valid;
    }

    public void set(V value, String json) {
        valid = value;
        lastJson = json;
    }
}
