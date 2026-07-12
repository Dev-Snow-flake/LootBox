package dev.snowflake.lootbox.pool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

final class ProbabilityCalculatorTest {
    @Test
    void drawUsesWeightedCumulativeRange() {
        ProbabilityCalculator calculator = new ProbabilityCalculator(new FixedRandom(75L));
        PoolEntry common = entry("basic", "STONE", 50);
        PoolEntry rare = entry("basic", "DIAMOND", 30);
        PoolEntry mythic = entry("basic", "NETHER_STAR", 20);

        assertThat(calculator.draw(List.of(common, rare, mythic))).isSameAs(rare);
    }

    @Test
    void viewsReflectEnabledWeightsOnly() {
        ProbabilityCalculator calculator = new ProbabilityCalculator(new FixedRandom(0L));
        PoolEntry enabled = entry("basic", "STONE", 3);
        PoolEntry disabled = entry("basic", "DIAMOND", 7).enabledCopy(false);

        assertThat(calculator.views(List.of(enabled, disabled)))
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.entry()).isSameAs(enabled);
                    assertThat(view.percent()).isEqualTo(100.0D);
                });
    }

    @Test
    void drawRejectsEmptyEnabledPool() {
        ProbabilityCalculator calculator = new ProbabilityCalculator(new FixedRandom(0L));

        assertThatThrownBy(() -> calculator.draw(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no enabled");
    }

    private static PoolEntry entry(String poolId, String material, int weight) {
        return new PoolEntry(
                0L,
                poolId,
                new RewardItem(material, material, List.of(), null, null),
                weight,
                Tier.COMMON,
                1,
                1,
                true);
    }

    private record FixedRandom(long value) implements RandomGenerator {
        @Override
        public long nextLong() {
            return value;
        }

        @Override
        public long nextLong(long bound) {
            return value % bound;
        }

        @Override
        public int nextInt() {
            return (int) value;
        }

        @Override
        public int nextInt(int bound) {
            return (int) (value % bound);
        }

        @Override
        public double nextDouble() {
            return 0.0D;
        }

        @Override
        public boolean nextBoolean() {
            return false;
        }

        @Override
        public float nextFloat() {
            return 0.0F;
        }
    }
}

