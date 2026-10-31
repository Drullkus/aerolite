package us.drullk.aerolite.world.level;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.neoforged.neoforge.common.conditions.WithConditions;
import net.neoforged.neoforge.registries.datamaps.DataMapEntry;
import net.neoforged.neoforge.registries.datamaps.DataMapFile;
import org.junit.jupiter.api.Test;
import us.drullk.aerolite.world.ShaftRegistries;
import us.drullk.aerolite.world.VariationList;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Checks the merge of the shaft variations data map files by dimension id.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 */
class DimensionShaftValuesTest {

    private static final ResourceKey<LevelStem> OVERWORLD = LevelStem.OVERWORLD;
    private static final ResourceKey<LevelStem> NETHER = LevelStem.NETHER;
    private static final VariationList STONE = list("stone");
    private static final VariationList TUFF = list("tuff");

    private static VariationList list(String variation) {
        return new VariationList(List.of(new VariationList.Entry(ResourceKey.create(ShaftRegistries.SHAFT_VARIATION, ShaftRegistries.id(variation)), 1)));
    }

    private static DataMapFile<VariationList, LevelStem> file(boolean replace, Map<ResourceKey<LevelStem>, VariationList> values, List<ResourceKey<LevelStem>> removals) {
        Map<Either<TagKey<LevelStem>, ResourceKey<LevelStem>>, Optional<WithConditions<DataMapEntry<VariationList>>>> entries = new LinkedHashMap<>();
        values.forEach((key, value) -> entries.put(Either.right(key), Optional.of(new WithConditions<>(new DataMapEntry<>(value, false)))));
        return new DataMapFile<>(replace, entries, removals.stream().map(key -> new DataMapEntry.Removal<VariationList, LevelStem>(Either.right(key), Optional.empty())).toList());
    }

    @Test
    void laterValueReplacesEarlierValue() {
        Map<ResourceKey<LevelStem>, VariationList> merged = DimensionShaftValues.merge(List.of(
                file(false, Map.of(OVERWORLD, STONE, NETHER, STONE), List.of()),
                file(false, Map.of(OVERWORLD, TUFF), List.of())));
        assertEquals(Map.of(OVERWORLD, TUFF, NETHER, STONE), merged);
    }

    @Test
    void replaceClearsEarlierFiles() {
        Map<ResourceKey<LevelStem>, VariationList> merged = DimensionShaftValues.merge(List.of(
                file(false, Map.of(OVERWORLD, STONE, NETHER, STONE), List.of()),
                file(true, Map.of(OVERWORLD, TUFF), List.of())));
        assertEquals(Map.of(OVERWORLD, TUFF), merged);
    }

    @Test
    void removalDropsAValue() {
        Map<ResourceKey<LevelStem>, VariationList> merged = DimensionShaftValues.merge(List.of(
                file(false, Map.of(OVERWORLD, STONE, NETHER, STONE), List.of()),
                file(false, Map.of(), List.of(NETHER))));
        assertEquals(Map.of(OVERWORLD, STONE), merged);
    }

    @Test
    void tagValueIsSkipped() {
        Map<Either<TagKey<LevelStem>, ResourceKey<LevelStem>>, Optional<WithConditions<DataMapEntry<VariationList>>>> entries = new LinkedHashMap<>();
        entries.put(Either.left(TagKey.create(Registries.LEVEL_STEM, ResourceLocation.withDefaultNamespace("any"))), Optional.of(new WithConditions<>(new DataMapEntry<>(STONE, false))));
        assertEquals(Map.of(), DimensionShaftValues.merge(List.of(new DataMapFile<>(false, entries, List.of()))));
    }

    @Test
    void valueIsFoundByLevelKey() {
        assertEquals(OVERWORLD.location(), ResourceKey.create(Registries.LEVEL_STEM, Level.OVERWORLD.location()).location());
    }

}
