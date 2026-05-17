package com.vote4tech.servidor.dto.sync;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SyncEstadoDto {
    private boolean centralAccesible;
    private Long ultimaDescarga;
    private Long ultimaSubida;
    private Integer votosUrnaLocales;
    private Integer votosDomicilioLocales;
}
