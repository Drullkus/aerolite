package us.drullk.aerolite.devtools;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import us.drullk.aerolite.devtools.providers.ProviderHub;
import us.drullk.aerolite.devtools.providers.ProvidersProbe;

/**
 * The dev tools mod. Only the client creates it.
 * It adds the client command {@code /aerolitedevtools providers}, which opens the hub of the provider editor prototypes.
 * The system property {@value ProvidersProbe#PROPERTY} starts the screenshot probe of the prototypes.
 * The system property {@value SettingsProbe#PROPERTY} starts the screenshot probe of the settings screens.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
@Mod(value = AeroliteDevTools.MODID, dist = Dist.CLIENT)
public class AeroliteDevTools {

    public static final String MODID = "aerolitedevtools";

    public AeroliteDevTools() {
        ProviderHub hub = new ProviderHub();
        NeoForge.EVENT_BUS.addListener(hub::registerCommands);
        if (Boolean.getBoolean(ProvidersProbe.PROPERTY)) {
            NeoForge.EVENT_BUS.addListener(new ProvidersProbe(hub)::tick);
        }
        if (Boolean.getBoolean(SettingsProbe.PROPERTY)) {
            NeoForge.EVENT_BUS.addListener(new SettingsProbe()::tick);
        }
    }

}
