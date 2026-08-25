package io.demoguard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DemoGuardApplicationTest {

    @Test
    void applicationRegistersItsController() {
        var operator = DemoGuardApplication.createOperator();
        try {
            assertEquals(1, operator.getRegisteredControllersNumber());
        } finally {
            operator.stop();
        }
    }
}
