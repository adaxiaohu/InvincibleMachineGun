package com.codigohasta.addon.utils.slimefunhelper.accessors.events;

import net.minecraft.entity.Entity;
import com.codigohasta.addon.utils.slimefunhelper.accessors.hacks.EntityInternalAccess;
public interface EntityAccess {static EntityInternalAccess of(Entity entity){return EntityInternalAccess.of(entity);}}
