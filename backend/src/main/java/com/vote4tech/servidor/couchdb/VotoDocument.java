package com.vote4tech.servidor.couchdb;

import com.vote4tech.servidor.enums.TipoMesa;
import com.vote4tech.servidor.enums.TipoSeleccion;
import lombok.*;

import java.time.LocalDateTime;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class VotoDocument {
    private String id;
    private Long idEleccion;
    private Long idMesa;
    private TipoMesa tipoMesa;
    private Long idCentroVotacion;
    private TipoSeleccion tipoSeleccion;
    private Long idSeleccion;
    private LocalDateTime timestamp;
}
