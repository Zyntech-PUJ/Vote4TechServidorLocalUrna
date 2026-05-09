package com.vote4tech.servidor.entity;

import jakarta.persistence.*;
import lombok.*;

@Data @Entity @Builder @AllArgsConstructor @NoArgsConstructor
@Table(name = "ciudadano")
public class Ciudadano {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ciudadano")
    private Long idCiudadano;

    @Column(name = "nombre", nullable = false, length = 64)
    private String nombre;

    @Column(name = "cedula", nullable = false, unique = true, length = 32)
    private String cedula;

    @Column(name = "genero", length = 1)
    private String genero;

    @Column(name = "voto_obligatorio", nullable = false)
    private Boolean votoObligatorio;

    @Column(name = "habilitado_domicilio", nullable = false)
    private Boolean habilitadoDomicilio = false;
}
