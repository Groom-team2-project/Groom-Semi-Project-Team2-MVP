package org.example.groommvp.global.common;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.*;

@Getter
@Setter
@Deprecated(since = "0.0.1", forRemoval = false)
@JsonPropertyOrder({"status", "data", "message"})
public class ApiResponse<T> {
    private String status;
    private T data;
    private String message;

    public ApiResponse(){}
}
