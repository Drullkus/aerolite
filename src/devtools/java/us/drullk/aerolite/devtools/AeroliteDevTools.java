package us.drullk.aerolite.devtools;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The dev tools mod. Only the client creates it.
 * The system property {@value SettingsProbe#PROPERTY} starts the screenshot probe of the settings screens.
 * The system property {@value IslandsProbe#PROPERTY} starts the screenshot probe of the Islands preset.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-07_01-04_30006f9a-885c-4e1b-8259-941ec5aa5133.md}.
 */
@Mod(value = AeroliteDevTools.MODID, dist = Dist.CLIENT)
public class AeroliteDevTools {

    public static final String MODID = "aerolitedevtools";

    public AeroliteDevTools() {
        if (Boolean.getBoolean(SettingsProbe.PROPERTY)) {
            NeoForge.EVENT_BUS.addListener(new SettingsProbe()::tick);
        }
        if (Boolean.getBoolean(IslandsProbe.PROPERTY)) {
            NeoForge.EVENT_BUS.addListener(new IslandsProbe()::tick);
        }
    }

}
