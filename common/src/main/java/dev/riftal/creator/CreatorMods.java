package dev.riftal.creator;

import dev.riftal.creator.platform.Services;

/**
 * Loader-agnostic entry point. Everything that can be written once lives here; the Fabric and
 * NeoForge entry points do nothing but call {@link #init()}.
 * <p>
 * Common code may only touch vanilla Minecraft, libraries vanilla already ships, and third party
 * libraries that publish loader-neutral binaries. Anything loader specific goes through a service
 * interface - see {@link Services}.
 */
public final class CreatorMods {

    public static void init() {
        Constants.LOG.info("{} starting on {} ({} environment)",
                Constants.MOD_NAME,
                Services.PLATFORM.getPlatformName(),
                Services.PLATFORM.getEnvironmentName());
    }

    private CreatorMods() {
    }
}
