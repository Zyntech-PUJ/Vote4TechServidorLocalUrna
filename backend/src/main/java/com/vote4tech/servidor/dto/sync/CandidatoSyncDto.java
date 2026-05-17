package com.vote4tech.servidor.dto.sync;

import lombok.Data;

@Data
public class CandidatoSyncDto {
    private Long idCandidato;
    private String nombre;
    private String numero;
    private String fotoUrl;
    private Boolean activo;
    private Long idLista;
    private Long idPartido;
}
