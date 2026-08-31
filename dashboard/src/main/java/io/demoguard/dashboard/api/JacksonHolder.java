package io.demoguard.dashboard.api;

import com.fasterxml.jackson.databind.ObjectMapper;

final class JacksonHolder {
    static final ObjectMapper MAPPER = new ObjectMapper();
    private JacksonHolder() {}
}
