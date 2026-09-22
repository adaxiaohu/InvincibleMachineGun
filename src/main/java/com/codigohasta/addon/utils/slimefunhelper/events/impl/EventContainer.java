package com.codigohasta.addon.utils.slimefunhelper.events.impl;

public record EventContainer<T>(Class<T> type,T value) {public T getValue(){return value;}}
