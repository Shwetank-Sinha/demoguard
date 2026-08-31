package io.demoguard.dashboard.api;

import java.util.regex.Pattern;

final class ResourceNameValidator {
    private static final Pattern DNS_LABEL = Pattern.compile(
            "[a-z0-9](?:[-a-z0-9]{0,61}[a-z0-9])?");

    private ResourceNameValidator() {}

    static void requireValid(String value, String field) {
        if (value == null || !DNS_LABEL.matcher(value).matches()) {
            throw new InvalidRequestException(field + " must be a valid Kubernetes DNS label");
        }
    }
}
