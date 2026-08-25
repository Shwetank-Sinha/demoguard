package io.demoguard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DemoGuardApplicationTest {

    @Test
    void applicationStarts() {
        assertDoesNotThrow(() -> DemoGuardApplication.main(new String[0]));
    }
}
