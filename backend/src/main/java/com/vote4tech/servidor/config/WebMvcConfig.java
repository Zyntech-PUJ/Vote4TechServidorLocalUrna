package com.vote4tech.servidor.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final DispositivoInterceptor dispositivoInterceptor;

    public WebMvcConfig(DispositivoInterceptor dispositivoInterceptor) {
        this.dispositivoInterceptor = dispositivoInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(dispositivoInterceptor).addPathPatterns("/**");
    }
}
