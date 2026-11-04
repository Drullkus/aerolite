package us.drullk.aerolite.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.neoforged.api.distmarker.Dist;
import tamaized.beanification.Autowired;
import us.drullk.aerolite.client.provider.ProviderDraft;
import us.drullk.aerolite.client.provider.ProviderType;
import us.drullk.aerolite.world.StateProviders;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The type name and the one-line summary of a block state provider. It uses no widget, so each editor can show the text.
 * A weighted summary reads {@code 3 stone_bricks, 1 andesite}. A noise summary reads {@code stone / andesite / tuff}.
 * A noise threshold summary reads {@code stone_bricks | 1 low | 2 high}. A randomized int summary reads {@code sea_pickle pickles 1-4}.
 * A provider of another type shows its type id.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
@tamaized.beanification.Component(dist = Dist.CLIENT)
public final class ProviderText {

    private static final String KEY = "createWorld.customize.aerolite.provider.";

    private final BlockSearch blocks;

    public ProviderText(@Autowired BlockSearch blocks) {
        this.blocks = blocks;
    }

    /** The name of the type, such as "Weighted". It reads {@code createWorld.customize.aerolite.provider.type.<type path>}. */
    public Component typeName(ProviderType type) {
        return Component.translatable(KEY + "type." + type.path());
    }

    /** The name of the type of the provider. */
    public Component typeName(BlockStateProvider provider) {
        return this.typeName(ProviderType.of(provider));
    }

    /** The summary of the provider without the type name. */
    public Component summary(BlockStateProvider provider) {
        return this.summary(ProviderDraft.of(provider));
    }

    /** The type name, a colon and the summary, such as "Weighted: 3 stone_bricks, 1 andesite". */
    public Component typedSummary(BlockStateProvider provider) {
        return this.typedSummary(ProviderDraft.of(provider));
    }

    /** The summary of the values of the draft without the type name. */
    public Component summary(ProviderDraft draft) {
        return switch (draft.type) {
            case SIMPLE -> Component.literal(this.blocks.text(draft.state));
            case WEIGHTED -> Component.literal(draft.entries.stream()
                    .map(entry -> entry.weight + " " + this.blocks.text(entry.state))
                    .collect(Collectors.joining(", ")));
            case NOISE, DUAL_NOISE -> Component.literal(this.join(draft.states));
            case NOISE_THRESHOLD -> Component.translatable(KEY + "summary.noise_threshold",
                    this.blocks.text(draft.defaultState), draft.lowStates.size(), draft.highStates.size());
            case ROTATED -> Component.literal(this.blocks.text(draft.rotatedBlock.defaultBlockState()));
            case RANDOMIZED_INT -> Component.literal(this.sourceText(draft) + " " + draft.property + " " + valuesText(draft));
            case OTHER -> Component.literal(draft.other == null ? "" : StateProviders.typeId(draft.other));
        };
    }

    /** The type name, a colon and the summary of the draft. */
    public Component typedSummary(ProviderDraft draft) {
        return Component.translatable(KEY + "typed_summary", this.typeName(draft.type), this.summary(draft));
    }

    /** The text of a state, such as {@code oak_log[axis=x]}. */
    public String stateText(BlockState state) {
        return this.blocks.text(state);
    }

    private String join(List<BlockState> states) {
        return states.stream().map(this.blocks::text).collect(Collectors.joining(" / "));
    }

    private String sourceText(ProviderDraft draft) {
        BlockState source = draft.sourceState();
        return source != null ? this.blocks.text(source) : StateProviders.typeId(draft.source);
    }

    /** A constant reads {@code 2}. A uniform range reads {@code 1-4}. Any other int provider shows its type id. */
    private static String valuesText(ProviderDraft draft) {
        return switch (draft.values) {
            case CONSTANT -> Integer.toString(draft.constant);
            case UNIFORM -> draft.min + "-" + draft.max;
            case OTHER -> draft.otherValues == null ? "" : String.valueOf(BuiltInRegistries.INT_PROVIDER_TYPE.getKey(draft.otherValues.getType()));
        };
    }

}
