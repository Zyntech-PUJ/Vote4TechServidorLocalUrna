package com.vote4tech.servidor.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registra en memoria la última vez que cada IP hizo una petición HTTP al servidor.
 * Permite consultar qué dispositivos estuvieron activos en los últimos N minutos.
 */
@Component
public class DispositivoTracker {

    private final ConcurrentHashMap<String, Instant> dispositivos = new ConcurrentHashMap<>();

    public void registrar(String ip) {
        dispositivos.put(ip, Instant.now());
    }

    /**
     * Retorna las IPs que hicieron al menos una petición en los últimos {@code minutosVentana} minutos.
     * El campo "ultimaVez" se devuelve como epoch milliseconds (string) para evitar
     * problemas de zona horaria en el cliente JS.
     */
    public List<Map<String, String>> getActivos(int minutosVentana) {
        Instant limite = Instant.now().minus(minutosVentana, ChronoUnit.MINUTES);
        return dispositivos.entrySet().stream()
                .filter(e -> e.getValue().isAfter(limite))
                .map(e -> Map.of(
                        "ip", e.getKey(),
                        "ultimaVez", String.valueOf(e.getValue().toEpochMilli())
                ))
                .collect(Collectors.toList());
    }

    public void remover(String ip) {
        dispositivos.remove(ip);
    }

    public void removerTodos() {
        dispositivos.clear();
    }
}
