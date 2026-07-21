package org.example.groommvp.global.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.example.groommvp.global.error.ErrorCode;
import org.example.groommvp.global.response.CommonResponse;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

public final class SecurityErrorResponseWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SecurityErrorResponseWriter() {
    }

    public static void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        OBJECT_MAPPER.writeValue(
                response.getWriter(),
                CommonResponse.error(errorCode.name(), errorCode.getMessage())
        );
    }
}
