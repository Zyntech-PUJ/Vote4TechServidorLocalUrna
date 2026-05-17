package com.vote4tech.servidor.dto.sync;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SyncResultDto {
    private boolean exitoso;
    private String mensaje;
    private LocalDateTime timestamp;
    private Integer registrosProcesados;
}
