package us.drullk.aerolite.client;

import net.minecraft.core.Direction;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.DualNoiseProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.NoiseProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.NoiseThresholdProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.RandomizedIntStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.RotatedBlockProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.WeightedStateProvider;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.util.InclusiveRange;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import us.drullk.aerolite.Aerolite;
import us.drullk.aerolite.client.provider.ProviderType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Checks the type name and the summary text of each provider type with the lang file of the mod.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
class ProviderTextTest {

    private static final NormalNoise.NoiseParameters NOISE = new NormalNoise.NoiseParameters(-3, 1.0);
    private static Language previous = Language.getInstance();

    private final ProviderText text = new ProviderText(new BlockSearch());

    /** Reads the generated lang file of the mod, so a translatable text resolves like in the game. */
    @BeforeAll
    static void injectLanguage() throws IOException {
        previous = Language.getInstance();
        Map<String, String> entries = new HashMap<>();
        Path path = ModList.get().getModFileById(Aerolite.MODID).getFile().findResource("assets", Aerolite.MODID, "lang", "en_us.json");
        try (InputStream stream = Files.newInputStream(path)) {
            Language.loadFromJson(stream, entries::put);
        }
        Language.inject(new Language() {
            @Override
            public String getOrDefault(String key, String defaultValue) {
                return entries.getOrDefault(key, defaultValue);
            }

            @Override
            public boolean has(String key) {
                return entries.containsKey(key);
            }

            @Override
            public boolean isDefaultRightToLeft() {
                return false;
            }

            @Override
            public FormattedCharSequence getVisualOrder(FormattedText formatted) {
                return FormattedCharSequence.forward(formatted.getString(), Style.EMPTY);
            }
        });
    }

    @AfterAll
    static void restoreLanguage() {
        Language.inject(previous);
    }

    private String typed(BlockStateProvider provider) {
        return this.text.typedSummary(provider).getString();
    }

    @Test
    void typeNames() {
        List<String> names = ProviderType.EDITABLE.stream().map(type -> this.text.typeName(type).getString()).toList();
        assertEquals(List.of("Simple state", "Weighted", "Noise threshold", "Noise", "Dual noise", "Rotated block", "Randomized int"), names);
        assertEquals("Weighted", this.text.typeName(new WeightedStateProvider(SimpleWeightedRandomList.single(Blocks.STONE.defaultBlockState()))).getString());
    }

    @Test
    void simpleSummary() {
        assertEquals("stone_bricks", this.text.summary(BlockStateProvider.simple(Blocks.STONE_BRICKS)).getString());
        assertEquals("Simple state: oak_log[axis=x]",
                this.typed(BlockStateProvider.simple(Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X))));
    }

    @Test
    void weightedSummary() {
        WeightedStateProvider weighted = new WeightedStateProvider(SimpleWeightedRandomList.<BlockState>builder()
                .add(Blocks.STONE_BRICKS.defaultBlockState(), 3)
                .add(Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), 1));
        assertEquals("Weighted: 3 stone_bricks, 1 mossy_stone_bricks", this.typed(weighted));
    }

    @Test
    void noiseSummaries() {
        List<BlockState> states = List.of(Blocks.STONE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState());
        assertEquals("Noise: stone / andesite", this.typed(new NoiseProvider(1L, NOISE, 0.02F, states)));
        assertEquals("Dual noise: stone / andesite", this.typed(new DualNoiseProvider(new InclusiveRange<>(1, 3), NOISE, 1.0F, 1L, NOISE, 0.02F, states)));
        assertEquals("Noise threshold: stone_bricks | 1 low | 2 high", this.typed(new NoiseThresholdProvider(1L, NOISE, 0.02F, -0.8F, 0.35F,
                Blocks.STONE_BRICKS.defaultBlockState(), List.of(Blocks.MOSSY_STONE_BRICKS.defaultBlockState()),
                List.of(Blocks.COBBLESTONE.defaultBlockState(), Blocks.GRAVEL.defaultBlockState()))));
    }

    @Test
    void rotatedSummary() {
        assertEquals("Rotated block: oak_log", this.typed(new RotatedBlockProvider(Blocks.OAK_LOG)));
    }

    @Test
    void randomizedIntSummary() {
        BlockStateProvider source = BlockStateProvider.simple(Blocks.SEA_PICKLE);
        assertEquals("Randomized int: sea_pickle pickles 1-4", this.typed(new RandomizedIntStateProvider(source, SeaPickleBlock.PICKLES, UniformInt.of(1, 4))));
        assertEquals("Randomized int: sea_pickle pickles 2", this.typed(new RandomizedIntStateProvider(source, SeaPickleBlock.PICKLES, ConstantInt.of(2))));
    }

}
