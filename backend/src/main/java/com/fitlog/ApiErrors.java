package com.fitlog;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> invalid(MethodArgumentNotValidException e) {
        String message=e.getBindingResult().getTarget() instanceof AuthController.Registration
            ? "請填寫有效電子郵件、1–60 字暱稱及至少 12 字元密碼。"
            : "請檢查欄位：標題、日期、時間及每組次數與重量皆須有效。";
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<?> unreadable() { return ResponseEntity.badRequest().body(Map.of("message", "資料格式不正確")); }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> status(ResponseStatusException e) { return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason()==null ? "請重新登入後再試。" : e.getReason())); }
}
