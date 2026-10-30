package us.drullk.aerolite.client;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import tamaized.beanification.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses, writes and searches the block state text of a block field.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 */
@Component(dist = Dist.CLIENT)
public final class BlockSearch {

    public static final int MAX_MATCHES = 8;
    private static final String DEFAULT_NAMESPACE = ResourceLocation.DEFAULT_NAMESPACE + ":";

    /** The text of a state: the block id without the default namespace, with the properties when they differ from the default. */
    public String text(BlockState state) {
        String text = state == state.getBlock().defaultBlockState()
                ? BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()
                : BlockStateParser.serialize(state);
        return text.startsWith(DEFAULT_NAMESPACE) ? text.substring(DEFAULT_NAMESPACE.length()) : text;
    }

    /** The state of a text, or null when the text is no block state or names air. */
    @Nullable
    public BlockState parse(String text) {
        StringReader reader = new StringReader(text.trim());
        try {
            BlockState parsed = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), reader, false).blockState();
            return reader.canRead() || parsed.isAir() ? null : parsed;
        } catch (CommandSyntaxException exception) {
            return null;
        }
    }

    /** Up to {@link #MAX_MATCHES} blocks whose id or name holds the text before the properties. */
    public List<Block> search(String text) {
        List<Block> matches = new ArrayList<>();
        int properties = text.indexOf('[');
        String query = (properties < 0 ? text : text.substring(0, properties)).trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return matches;
        }
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block.defaultBlockState().isAir()) {
                continue;
            }
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            if (id.contains(query) || block.getName().getString().toLowerCase(Locale.ROOT).contains(query)) {
                matches.add(block);
                if (matches.size() == MAX_MATCHES) {
                    break;
                }
            }
        }
        return matches;
    }

}
