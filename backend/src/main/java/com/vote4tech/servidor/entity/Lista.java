package com.vote4tech.servidor.entity;

import com.vote4tech.servidor.enums.TipoLista;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data @Entity @Builder @AllArgsConstructor @NoArgsConstructor
@Table(name = "lista")
public class Lista {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_lista")
    private Long idLista;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private TipoLista tipo;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_eleccion", referencedColumnName = "id_eleccion", nullable = false)
    private Eleccion eleccion;

    @OneToMany(mappedBy = "lista", fetch = FetchType.LAZY)
    private List<Candidato> candidatos;
}
