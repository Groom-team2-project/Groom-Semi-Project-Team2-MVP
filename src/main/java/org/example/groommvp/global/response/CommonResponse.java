package org.example.groommvp.global.response;

public record CommonResponse<T> (
    boolean success,
    T data,
    String errorCode,
    String message
) {
    // 성공
    public static <T> CommonResponse<T> success(T data, String message) {
        return new CommonResponse<>(true, data, null, message);
    }

    // 실패
    public static CommonResponse<Void> error(String errorCode, String message) {
        return new CommonResponse<>(false, null, errorCode, message);
    }
}
