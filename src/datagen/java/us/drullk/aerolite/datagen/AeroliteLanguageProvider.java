package us.drullk.aerolite.datagen;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;
import us.drullk.aerolite.Aerolite;

/**
 * Writes the American English lang file of Aerolite.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_06-17_8d424d05-8940-4aa5-872e-7b2bd927cb73.md}.
 */
public class AeroliteLanguageProvider extends LanguageProvider {

    public AeroliteLanguageProvider(PackOutput output) {
        super(output, Aerolite.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
    }

}
