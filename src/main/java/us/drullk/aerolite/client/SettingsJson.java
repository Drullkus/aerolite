package us.drullk.aerolite.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import us.drullk.aerolite.world.AeroliteSettings;

/**
 * The copy and the paste of the preset settings as one line of JSON through the system clipboard.
 * Only the settings codec knows the fields, so a new field needs no change here.
 * The toast of a failed paste is the same on the Aerolite screen and in the preview panel.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 */
@tamaized.beanification.Component(dist = Dist.CLIENT)
public final class SettingsJson {

    /** HTML escapes turn the equals sign of a block state into a unicode escape. */
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    /** The toast id of a failed paste. */
    private final SystemToast.SystemToastId pasteFailedToast = new SystemToast.SystemToastId();

    public SettingsJson() {
    }

    /** Encodes the settings as minified JSON, with no newline and no indentation. */
    public String export(AeroliteSettings settings) {
        JsonElement json = AeroliteSettings.CODEC.encodeStart(JsonOps.INSTANCE, settings).getOrThrow();
        return GSON.toJson(json);
    }

    /** Decodes settings from JSON text. Bad JSON and values that the codec refuses give an error result. */
    public DataResult<AeroliteSettings> parse(String text) {
        JsonElement json;
        try {
            json = JsonParser.parseString(text);
        } catch (JsonParseException exception) {
            Throwable cause = exception;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            String message = cause.getMessage();
            String name = cause.getClass().getSimpleName();
            return DataResult.error(() -> message == null ? name : firstLine(message));
        }
        return AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, json);
    }

    /** The Gson message puts a link to its troubleshooting page on the second line. */
    private static String firstLine(String message) {
        int end = message.indexOf('\n');
        return end < 0 ? message : message.substring(0, end);
    }

    /** Puts the export of the settings on the system clipboard. */
    public void copy(AeroliteSettings settings) {
        Minecraft.getInstance().keyboardHandler.setClipboard(this.export(settings));
    }

    /** Parses the text of the system clipboard. */
    public DataResult<AeroliteSettings> paste() {
        return this.parse(Minecraft.getInstance().keyboardHandler.getClipboard());
    }

    /** Shows the system toast of a failed paste. The error message of the parse wraps over several lines. */
    public void showPasteFailed(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getToasts().addToast(SystemToast.multiline(minecraft, this.pasteFailedToast,
                Component.translatable("createWorld.customize.aerolite.paste_failed"), Component.literal(message)));
    }

}
