package com.vote4tech.servidor.dto.sync;

import lombok.Data;

@Data
public class PartidoSyncDto {
    private Long idPartido;
    private String nombre;
    private String sigla;
    private String logoUrl;
}
