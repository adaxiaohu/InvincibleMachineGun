package com.codigohasta.addon.utils.slimefunhelper.accessors.interfaces;

import org.jetbrains.annotations.NotNull;
import com.codigohasta.addon.utils.slimefunhelper.utils.containers.MetaData;

public interface MetadataHolder {
    @NotNull
    public MetaData getMetadata();

    public boolean isMetaEmpty();
}
