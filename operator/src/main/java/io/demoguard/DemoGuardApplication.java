package io.demoguard;

import io.demoguard.reconciler.DemoPolicyReconciler;
import io.javaoperatorsdk.operator.Operator;

import java.time.Duration;

public final class DemoGuardApplication {

    private DemoGuardApplication() {
    }

    public static void main(String[] args) {
        Operator operator = createOperator();
        operator.installShutdownHook(Duration.ofSeconds(10));
        operator.start();
    }

    static Operator createOperator() {
        Operator operator = new Operator();
        operator.register(new DemoPolicyReconciler(operator.getKubernetesClient()));
        return operator;
    }
}
