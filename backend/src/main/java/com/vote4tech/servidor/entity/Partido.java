package com.vote4tech.servidor.entity;

import jakarta.persistence.*;
import lombok.*;

@Data @Entity @Builder @AllArgsConstructor @NoArgsConstructor
@Table(name = "partido")
public class Partido {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_partido")
    private Long idPartido;

    @Column(name = "nombre", nullable = false, length = 64)
    private String nombre;

    @Column(name = "sigla", nullable = false, length = 16)
    private String sigla;

    @Column(name = "logo_url", length = 512)
    private String logoUrl;
}
