package com.vote4tech.servidor.entity;

import com.vote4tech.servidor.enums.EstadoEleccion;
import com.vote4tech.servidor.enums.TipoEleccion;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data @Entity @Builder @AllArgsConstructor @NoArgsConstructor
@Table(name = "eleccion")
public class Eleccion {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_eleccion")
    private Long idEleccion;

    @Column(name = "nombre", nullable = false, length = 64)
    private String nombre;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_finalizacion", nullable = false)
    private LocalDateTime fechaFinalizacion;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 32)
    private TipoEleccion tipo;

    @Column(name = "lista_abierta", nullable = false)
    private Boolean listaAbierta;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 32)
    private EstadoEleccion estado;

    @OneToMany(mappedBy = "eleccion", fetch = FetchType.LAZY)
    private List<Lista> listas;
}
