package io.kaoto.backend.metadata.parser;

import io.kaoto.backend.model.Metadata;

import java.io.File;

public abstract class YamlProcessFile<T extends Metadata>
        extends ProcessFile<T> {
    protected boolean isDesiredType(final File file) {
        return (file.getName().endsWith(".yml")
                || file.getName().endsWith(".yaml"))
                && !file.getName().startsWith(".");
    }
}
