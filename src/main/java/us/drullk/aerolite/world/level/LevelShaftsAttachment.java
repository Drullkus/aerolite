package us.drullk.aerolite.world.level;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import tamaized.beanification.Component;
import tamaized.beanification.PostConstruct;
import us.drullk.aerolite.Aerolite;

/**
 * Registers the data attachment type that holds the shaft data of a server level.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 */
@Component
public final class LevelShaftsAttachment {

    /** The level load binds the value, so the type has no default value. */
    public static final AttachmentType<LevelShafts> TYPE = AttachmentType.<LevelShafts>builder(holder -> {
        throw new IllegalStateException("The level load binds the shaft data of a level");
    }).build();

    private final DeferredRegister<AttachmentType<?>> attachmentTypes = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Aerolite.MODID);

    public LevelShaftsAttachment() {
        this.attachmentTypes.register("level_shafts", () -> TYPE);
    }

    @PostConstruct
    public void register(IEventBus modEventBus) {
        this.attachmentTypes.register(modEventBus);
    }

}
