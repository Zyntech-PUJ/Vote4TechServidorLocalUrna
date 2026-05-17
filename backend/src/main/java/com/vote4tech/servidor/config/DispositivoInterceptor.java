package com.vote4tech.servidor.config;

import com.vote4tech.servidor.service.DispositivoTracker;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Intercepta cada petición HTTP entrante y registra la IP del cliente
 * en DispositivoTracker, permitiendo que el dashboard muestre dispositivos conectados.
 */
@Component
public class DispositivoInterceptor implements HandlerInterceptor {

    private final DispositivoTracker tracker;

    public DispositivoInterceptor(DispositivoTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String ip = request.getRemoteAddr();
        if (ip != null) {
            tracker.registrar(ip);
        }
        return true;
    }
}
