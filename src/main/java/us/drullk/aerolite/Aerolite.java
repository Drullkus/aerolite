package us.drullk.aerolite;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import tamaized.beanification.BeanContext;

/**
 * The mod class of Aerolite.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 */
@Mod(Aerolite.MODID)
public class Aerolite {

    public static final String MODID = "aerolite";
    public static final Logger LOGGER = LogUtils.getLogger();

    static {
        BeanContext.init(MODID);
    }

}
