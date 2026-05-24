package com.vote4tech.servidor.entity;

import jakarta.persistence.*;
import lombok.*;

@Data @Entity @Builder @AllArgsConstructor @NoArgsConstructor
@Table(name = "servidor_config")
public class ServidorConfig {

    @Id
    private Long id = 1L;

    @Column(name = "server_id", length = 36)
    private String serverId;

    @Column(name = "id_centro_asignado")
    private Long idCentroAsignado;
}
