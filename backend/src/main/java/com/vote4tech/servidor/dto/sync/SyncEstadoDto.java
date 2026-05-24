package com.vote4tech.servidor.dto.sync;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

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
    /** Votos locales de urna agrupados por mesa. Cada entrada tiene: idMesa, numero, tipo, centro, votos. */
    private List<Map<String, Object>> votosPorMesa;
}
