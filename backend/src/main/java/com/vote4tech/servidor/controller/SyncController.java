package com.vote4tech.servidor.controller;

import com.vote4tech.servidor.dto.sync.SyncEstadoDto;
import com.vote4tech.servidor.dto.sync.SyncResultDto;
import com.vote4tech.servidor.service.SyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoints REST para el módulo de sincronización.
 *
 * POST /sync/descargar  → Descarga datos electorales del sistema central a la BD local.
 * POST /sync/subir      → Sube los votos locales al CouchDB central.
 * GET  /sync/estado     → Devuelve el estado de conectividad y últimas operaciones.
 */
@RestController
@RequestMapping("/sync")
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    /**
     * Descarga todos los datos electorales desde el sistema central
     * y los persiste en la base de datos local.
     */
    @PostMapping("/descargar")
    public ResponseEntity<SyncResultDto> descargar() {
        SyncResultDto result = syncService.descargar();
        return result.isExitoso()
                ? ResponseEntity.ok(result)
                : ResponseEntity.status(503).body(result);
    }

    /**
     * Sube los votos almacenados localmente en CouchDB hacia el CouchDB central
     * usando replicación CouchDB nativa.
     */
    @PostMapping("/subir")
    public ResponseEntity<SyncResultDto> subir() {
        SyncResultDto result = syncService.subir();
        return result.isExitoso()
                ? ResponseEntity.ok(result)
                : ResponseEntity.status(503).body(result);
    }

    /**
     * Devuelve cuántos votos locales ya existen en el CouchDB central (por UUID).
     * El frontend lo consulta antes de descargar o subir para mostrar la advertencia.
     */
    @GetMapping("/conflictos")
    public ResponseEntity<Map<String, Object>> conflictos() {
        int count = syncService.contarConflictos();
        return ResponseEntity.ok(Map.of("conflictos", count));
    }

    /**
     * Devuelve el estado actual de la sincronización:
     * accesibilidad del sistema central, timestamp de la última descarga/subida
     * y conteo de votos locales.
     */
    @GetMapping("/estado")
    public ResponseEntity<SyncEstadoDto> estado() {
        return ResponseEntity.ok(syncService.getEstado());
    }
}
