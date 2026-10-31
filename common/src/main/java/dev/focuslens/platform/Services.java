package dev.focuslens.platform;

import java.util.ServiceLoader;

public final class Services {
    public static final Platform PLATFORM = load(Platform.class);

    private Services() {
    }

    private static <T> T load(Class<T> type) {
        return ServiceLoader.load(type, Services.class.getClassLoader())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No implementation of " + type.getName()
                        + " found. Each loader module must ship META-INF/services/" + type.getName()));
    }
}
