package com.vote4tech.servidor.dto.sync;

import lombok.Data;

import java.util.List;

/**
 * DTO raíz que corresponde a la respuesta de
 * GET {central.api.url}/sync/datos-electorales
 */
@Data
public class DatosElectoralesDto {
    private List<CiudadanoSyncDto> ciudadanos;
    private List<PartidoSyncDto> partidos;
    private List<EleccionSyncDto> elecciones;
    private List<ListaSyncDto> listas;
    private List<CandidatoSyncDto> candidatos;
    private List<CentroVotacionSyncDto> centrosVotacion;
    private List<MesaSyncDto> mesas;
    private List<RegistradorSyncDto> registradores;
}
