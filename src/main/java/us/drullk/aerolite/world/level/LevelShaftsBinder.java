package us.drullk.aerolite.world.level;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import tamaized.beanification.Autowired;
import tamaized.beanification.Component;
import tamaized.beanification.PostConstruct;

/**
 * Binds the shaft data to each server level when the level loads. A level whose settings come from its generator writes the world shaft pack.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 */
@Component
public final class LevelShaftsBinder {

    private final LevelShaftsResolver resolver;
    private final LevelShaftsBinding binding;
    private final WorldShaftPack pack;

    public LevelShaftsBinder(@Autowired LevelShaftsResolver resolver, @Autowired LevelShaftsBinding binding, @Autowired WorldShaftPack pack) {
        this.resolver = resolver;
        this.binding = binding;
        this.pack = pack;
    }

    @PostConstruct
    public void register() {
        NeoForge.EVENT_BUS.addListener(this::onLoad);
    }

    /** The server posts the load event of a level before the level generates its first chunk. */
    private void onLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        LevelShafts shafts = this.resolver.resolve(level);
        this.binding.bind(level, shafts);
        if (shafts.source() == LevelShafts.Source.GENERATOR) {
            this.pack.writeOnce(level.getServer());
        }
    }

}
