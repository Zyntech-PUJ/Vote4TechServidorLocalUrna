package com.vote4tech.servidor.service;

import com.vote4tech.servidor.dto.sync.SyncResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Descarga automáticamente los datos electorales del sistema central
 * cada vez que el servidor arranca (docker compose up).
 * Corre en hilo separado para no bloquear el inicio del servidor.
 * Si no hay conexión al central, falla silenciosamente y el servidor
 * sigue disponible para sincronización manual.
 */
@Component
public class SyncStartupRunner {

    private static final Logger log = LoggerFactory.getLogger(SyncStartupRunner.class);

    private final SyncService syncService;

    public SyncStartupRunner(SyncService syncService) {
        this.syncService = syncService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        Thread syncThread = new Thread(() -> {
            try {
                log.info("=== Sincronización automática al arranque: iniciando... ===");
                SyncResultDto result = syncService.descargar();
                if (result.isExitoso()) {
                    log.info("=== Sincronización automática completada: {} registros ===",
                            result.getRegistrosProcesados());
                } else {
                    log.warn("=== Sincronización automática falló (sin conexión al central?): {} ===",
                            result.getMensaje());
                }
            } catch (Exception e) {
                log.error("=== Error en sincronización automática al arranque: {} ===", e.getMessage());
            }
        }, "sync-startup-thread");
        syncThread.setDaemon(true);
        syncThread.start();
    }
}
