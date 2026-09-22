package com.codigohasta.addon.utils.slimefunhelper.hacks.utils.config;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;

/** Immutable configuration with a lazily resolved registry whitelist. */
public final class EntityTypeRegex {
    // Preserve the original JSON shape; runtime registry objects must never be serialized.
    private final Regex parent;
    private transient Set<EntityType<?>> resolvedTypes;

    public EntityTypeRegex(Regex parent) {
        this.parent = parent;
    }

    public Regex parent() {
        return parent;
    }

    public boolean test(EntityType<?> type) {
        if (resolvedTypes == null) {
            Set<EntityType<?>> collection = new HashSet<>();
            parseEntityWhiteList(parent == null ? null : parent.regex(), collection);
            resolvedTypes = collection;
        }
        return resolvedTypes.contains(type);
    }

    @Override public boolean equals(Object other) {
        return other instanceof EntityTypeRegex filter && Objects.equals(parent, filter.parent);
    }

    @Override public int hashCode() {
        return Objects.hashCode(parent);
    }

    public static void parseEntityWhiteList(String value, Set<EntityType<?>> collection) {
        collection.clear();
        try {
            Pattern pattern = Pattern.compile(value);
            for (net.minecraft.entity.EntityType<?> entityType : Registries.ENTITY_TYPE) {
                if (pattern.matcher(Registries.ENTITY_TYPE.getId(entityType).getPath()).matches()) {
                    collection.add(entityType);
                }
            }
            if (pattern.matcher("animal").matches()) {
                for (net.minecraft.entity.EntityType<?> entityType : Registries.ENTITY_TYPE) {
                    if (entityType.getSpawnGroup() == SpawnGroup.CREATURE) {
                        collection.add(entityType);
                    }
                }
            }
            if (pattern.matcher("monster").matches()) {
                for (net.minecraft.entity.EntityType<?> entityType : Registries.ENTITY_TYPE) {
                    if (entityType.getSpawnGroup() == SpawnGroup.MONSTER) {
                        if (!(entityType == EntityType.ZOMBIFIED_PIGLIN)
                                && !(entityType == net.minecraft.entity.EntityType.ENDERMAN)) {
                            collection.add(entityType);
                        }
                    }
                }
            }
            // feat: add spawn group flag
            for (SpawnGroup group : SpawnGroup.values()) {
                if (group != SpawnGroup.MONSTER && pattern.matcher(group.getName()).matches()) {
                    for (net.minecraft.entity.EntityType<?> entityType : Registries.ENTITY_TYPE) {
                        if (entityType.getSpawnGroup() == group) {
                            collection.add(entityType);
                        }
                    }
                }
            }
            if (pattern.matcher("living_entity").matches()) {
                for (EntityType<?> entityType : Registries.ENTITY_TYPE) {
                    if (entityType.getSpawnGroup() != SpawnGroup.MISC) {
                        collection.add(entityType);
                    }
                }
            }
            var iter = collection.iterator();
            while (iter.hasNext()) {
                EntityType<?> entityType = iter.next();
                if (!pattern.matcher(Registries.ENTITY_TYPE.getId(entityType).getPath()).matches()
                        && pattern.matcher("!" + Registries.ENTITY_TYPE.getId(entityType).getPath()).matches()) {
                    iter.remove();
                }
            }
        } catch (IllegalArgumentException | NullPointerException valuePatternError) {
            collection.clear();
        }
    }
}
