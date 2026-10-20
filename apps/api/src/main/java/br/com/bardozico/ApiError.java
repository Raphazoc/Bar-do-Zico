package br.com.bardozico;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.Map;

class ApiError extends RuntimeException {
    final int status;
    ApiError(int status, String message) { super(message); this.status = status; }
    static ApiError bad(String message) { return new ApiError(400,message); }
}

@RestControllerAdvice
class Errors {
    @ExceptionHandler(ApiError.class) ResponseEntity<?> business(ApiError e) {
        return ResponseEntity.status(e.status).body(Map.of("error", e.getMessage()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            IllegalArgumentException.class}) ResponseEntity<?> invalid(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error", "Dados inválidos. Confira os campos e tente novamente."));
    }
    @ExceptionHandler(DataAccessException.class) ResponseEntity<?> database(DataAccessException e) {
        return ResponseEntity.status(409).body(Map.of("error", "A operação conflita com o estado atual. Atualize a tela e confira valores, vínculos e permissões."));
    }
}
