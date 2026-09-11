package dev.riftal.creator.core.platform;

import dev.riftal.creator.core.platform.services.IAttachmentHelper;
import dev.riftal.creator.core.platform.services.INetworkHelper;
import dev.riftal.creator.platform.Services;

/**
 * ServiceLoader-backed handles on the two loader-specific concerns core needs at arbitrary times:
 * attachments and networking. Everything else core does is deferred and flushed by the loader, so
 * it needs no service.
 *
 * <p>Implementations live in {@code fabric|neoforge/src/main/java/dev/riftal/creator/core/platform}
 * and are declared in {@code META-INF/services/...}.
 */
public final class CoreServices {

    public static final IAttachmentHelper ATTACHMENTS = Services.load(IAttachmentHelper.class);
    public static final INetworkHelper NETWORK = Services.load(INetworkHelper.class);

    private CoreServices() {
    }
}
