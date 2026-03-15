package com.smartcity.common.json;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonUtils {
    private JsonUtils() {
    }

    public static final ObjectMapper MAPPER = new ObjectMapper();
}
