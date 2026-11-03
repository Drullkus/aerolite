package us.drullk.aerolite.devtools.providers;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * The screenshot probe of the provider prototypes. The system property {@value #PROPERTY} turns it on.
 * After the first screen shows, such as the title screen, it opens the hub, then each permutation with the field {@link #FIELD} open.
 * It saves one screenshot of the hub, and two of each permutation, under {@code screenshots/} of the game directory.
 * The first of the two shows the closed fields. The second shows the open field.
 * Then it stops the game.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class ProvidersProbe {

    public static final String PROPERTY = "aerolite.devtools.providersProbe";
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The noise threshold field. Its editor is the tallest. */
    private static final int FIELD = 2;
    private static final int TITLE_WAIT = 40;
    private static final int OPEN_WAIT = 10;
    private static final int RENDER_WAIT = 20;
    private static final int STEPS = 5;
    /** The screenshot writer runs on another thread, so the stop waits. */
    private static final int STOP_WAIT = 60;

    private final ProviderHub hub;
    private int ticks;
    private int step;
    private boolean started;
    private HubScreen hubScreen;
    @Nullable
    private PermutationScreen screen;

    public ProvidersProbe(ProviderHub hub) {
        this.hub = hub;
        this.hubScreen = new HubScreen(null, hub);
    }

    public void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!this.started) {
            if (minecraft.screen != null && minecraft.getOverlay() == null && ++this.ticks >= TITLE_WAIT) {
                this.started = true;
                this.ticks = 0;
                this.hubScreen = new HubScreen(minecraft.screen, this.hub);
                minecraft.setScreen(this.hubScreen);
                LOGGER.info("Providers probe: hub open");
            }
            return;
        }
        this.ticks++;
        if (this.step == 0) {
            if (this.ticks >= RENDER_WAIT) {
                this.grab(minecraft, "providers-0-hub.png");
                this.advance();
            }
            return;
        }
        Permutation[] permutations = Permutation.values();
        // Each permutation takes five steps: open, show the fields, grab, open one field, grab.
        int index = (this.step - 1) / STEPS;
        if (index >= permutations.length) {
            if (this.ticks >= STOP_WAIT) {
                LOGGER.info("Providers probe: done");
                minecraft.stop();
            }
            return;
        }
        Permutation permutation = permutations[index];
        String name = "providers-" + (index + 1) + "-" + permutation.name().toLowerCase(Locale.ROOT);
        switch ((this.step - 1) % STEPS) {
            case 0 -> {
                minecraft.setScreen(this.hubScreen);
                this.screen = this.hub.open(permutation, this.hubScreen);
                this.advance();
            }
            case 1 -> {
                if (this.ticks >= OPEN_WAIT && this.screen != null) {
                    this.screen.revealBlocks();
                    this.advance();
                }
            }
            case 2 -> {
                if (this.ticks >= RENDER_WAIT) {
                    this.grab(minecraft, name + "-closed.png");
                    this.advance();
                }
            }
            case 3 -> {
                if (this.screen != null) {
                    this.screen.openField(FIELD);
                }
                this.advance();
            }
            default -> {
                if (this.ticks >= RENDER_WAIT) {
                    this.grab(minecraft, name + ".png");
                    this.advance();
                }
            }
        }
    }

    private void advance() {
        this.step++;
        this.ticks = 0;
    }

    private void grab(Minecraft minecraft, String name) {
        LOGGER.info("Providers probe: grab {} of {}", name, minecraft.screen == null ? "no screen" : minecraft.screen.getClass().getSimpleName());
        Screenshot.grab(minecraft.gameDirectory, name, minecraft.getMainRenderTarget(), message -> LOGGER.info("Providers probe: {}", message.getString()));
    }

}
