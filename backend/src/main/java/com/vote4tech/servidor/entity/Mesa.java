package com.vote4tech.servidor.entity;

import com.vote4tech.servidor.enums.TipoMesa;
import jakarta.persistence.*;
import lombok.*;

@Data @Entity @Builder @AllArgsConstructor @NoArgsConstructor
@Table(name = "mesa")
public class Mesa {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_mesa")
    private Long idMesa;

    @Column(name = "numero", nullable = false)
    private Integer numero;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 16)
    private TipoMesa tipo;

    @Column(name = "activo", nullable = false)
    private Boolean activo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_centro_votacion", referencedColumnName = "id_centro_votacion", nullable = false)
    private CentroVotacion centroVotacion;
}
