package com.vote4tech.servidor.entity;

import jakarta.persistence.*;
import lombok.*;

@Data @Entity @Builder @AllArgsConstructor @NoArgsConstructor
@Table(name = "centro_votacion")
public class CentroVotacion {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_centro_votacion")
    private Long idCentroVotacion;

    @Column(name = "nombre", nullable = false, length = 128)
    private String nombre;

    @Column(name = "direccion", nullable = false, length = 256)
    private String direccion;
}
