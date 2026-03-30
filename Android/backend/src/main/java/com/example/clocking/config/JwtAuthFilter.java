package com.example.clocking.config;

import com.example.clocking.service.JwtService;
import com.example.clocking.service.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/auth/login";
    private static final String REFRESH_PATH = "/auth/refresh";

    private static final UrlPathHelper PATH_HELPER = new UrlPathHelper();

    private final JwtService jwtService;
    private final SessionService sessionService;

    public JwtAuthFilter(JwtService jwtService, SessionService sessionService) {
        this.jwtService = jwtService;
        this.sessionService = sessionService;
    }

    /**
     * No ejecutar este filtro en login/refresh: así no se puede validar un Bearer caducado antes del
     * controlador de login (mismo JSON 401 que confundía con el entry point).
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = resolvePath(request);
        if (LOGIN_PATH.equals(path)) {
            return true;
        }
        return REFRESH_PATH.equals(path) && HttpMethod.POST.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7).trim();
        if (token.isEmpty()) {
            unauthorized(response);
            return;
        }

        var payloadOpt = jwtService.parseToken(token);
        if (payloadOpt.isEmpty()) {
            unauthorized(response);
            return;
        }
        var payload = payloadOpt.get();
        var sessionOpt = sessionService.validate(payload.sessionId(), payload.userId(), token);
        if (sessionOpt.isEmpty()) {
            unauthorized(response);
            return;
        }

        var auth = new UsernamePasswordAuthenticationToken(
                String.valueOf(payload.userId()), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        auth.setDetails(new AuthDetails(payload.sessionId(), payload.expiresAt().toString()));
        SecurityContextHolder.getContext().setAuthentication(auth);
        filterChain.doFilter(request, response);
    }

    public record AuthDetails(long sessionId, String expiresAt) {}

    /** Ruta dentro de la aplicación, con fallback si UrlPathHelper devuelve vacío en el filtro. */
    static String resolvePath(HttpServletRequest request) {
        String path = PATH_HELPER.getPathWithinApplication(request);
        if (path == null || path.isEmpty()) {
            String uri = request.getRequestURI();
            String ctx = request.getContextPath();
            if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
                uri = uri.substring(ctx.length());
            }
            path = uri.isEmpty() ? "/" : uri;
        }
        int semi = path.indexOf(';');
        if (semi >= 0) {
            path = path.substring(0, semi);
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private static void unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"No autorizado\"}");
    }
}
