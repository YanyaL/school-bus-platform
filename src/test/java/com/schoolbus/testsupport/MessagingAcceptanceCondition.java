package com.schoolbus.testsupport;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class MessagingAcceptanceCondition
        implements ExecutionCondition {

    private static final String PRIMARY =
            "RUN_MESSAGING_ACCEPTANCE_TESTS";
    private static final String LEGACY =
            "RUN_RABBITMQ_INTEGRATION_TESTS";

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(
            ExtensionContext context
    ) {
        if (isEnabled(System.getenv(PRIMARY))
                || isEnabled(System.getenv(LEGACY))) {
            return ConditionEvaluationResult.enabled(
                    "Messaging acceptance tests enabled via "
                            + PRIMARY + " or " + LEGACY
            );
        }
        return ConditionEvaluationResult.disabled(
                "Set " + PRIMARY + "=true (or legacy "
                        + LEGACY + "=true) to run RabbitMQ "
                        + "acceptance tests"
        );
    }

    private static boolean isEnabled(String value) {
        return "true".equalsIgnoreCase(value);
    }
}
