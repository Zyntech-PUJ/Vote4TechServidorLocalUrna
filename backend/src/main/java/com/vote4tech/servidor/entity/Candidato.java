package com.vote4tech.servidor.entity;

import jakarta.persistence.*;
import lombok.*;

@Data @Entity @Builder @AllArgsConstructor @NoArgsConstructor
@Table(name = "candidato")
public class Candidato {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_candidato")
    private Long idCandidato;

    @Column(name = "nombre", nullable = false, length = 64)
    private String nombre;

    @Column(name = "numero", nullable = false, length = 16)
    private String numero;

    @Column(name = "foto_url", nullable = false, length = 512)
    private String fotoUrl;

    @Column(name = "activo", nullable = false)
    private Boolean activo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_lista", referencedColumnName = "id_lista", nullable = false)
    private Lista lista;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_partido", referencedColumnName = "id_partido", nullable = false)
    private Partido partido;
}
