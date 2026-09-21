package com.lotus.bixi.common.mq.reliable;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;

class ReliableDeliveryPropertiesTest {
    @Test
    void defaultsAndBackoffStayWithinTheDocumentedDeliveryBounds() {
        var defaults = ReliableDeliveryProperties.defaults();
        assertThat(defaults.pollInterval()).isEqualTo(Duration.ofSeconds(1));
        assertThat(defaults.leaseDuration()).isEqualTo(Duration.ofSeconds(30));
        assertThat(defaults.sendTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(defaults.batchSize()).isEqualTo(20);
        assertThat(defaults.maxAttempts()).isEqualTo(12);
        int[] seconds = {1, 2, 4, 8, 16, 32, 60, 120, 240, 300, 300, 300};
        for (int attempt = 1; attempt <= 12; attempt++) {
            for (int i = 0; i < 20; i++) {
                assertThat(defaults.retryDelay(attempt)).isBetween(Duration.ofSeconds(seconds[attempt - 1]),
                        Duration.ofMillis(seconds[attempt - 1] * 1200L));
            }
        }
    }

    @Test
    void invalidCapacityAndTimeoutSettingsCannotCreateAnUnsafeDispatcher() {
        assertThatThrownBy(() -> new ReliableDeliveryProperties(Duration.ZERO, Duration.ofSeconds(30), Duration.ofSeconds(5), 20, 12)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReliableDeliveryProperties(Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(5), 20, 12)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReliableDeliveryProperties(Duration.ofSeconds(1), Duration.ofSeconds(30), Duration.ofSeconds(6), 20, 12)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReliableDeliveryProperties(Duration.ofSeconds(1), Duration.ofSeconds(30), Duration.ofSeconds(5), 21, 12)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReliableDeliveryProperties(Duration.ofSeconds(1), Duration.ofSeconds(30), Duration.ofSeconds(5), 20, 13)).isInstanceOf(IllegalArgumentException.class);
    }
}
