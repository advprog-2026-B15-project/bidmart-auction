package id.ac.ui.cs.advprog.bidmart.auction.config;

import id.ac.ui.cs.advprog.bidmart.auction.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtService jwtService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String authHeader = request.getHeader("Authorization");
        String method = request.getMethod();

        // Izinkan GET request tanpa token (agar publik bisa melihat daftar lelang)
        if ("GET".equalsIgnoreCase(method)) {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                try {
                    String userId = jwtService.extractUserId(authHeader);
                    request.setAttribute("userId", userId);
                } catch (Exception e) {
                    // Abaikan error pada GET, userId tetap null
                }
            }
            return true;
        }

        // Untuk POST, PATCH, DELETE, dll, token WAJIB ada dan valid
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authorization token is required for this action");
            return false;
        }

        try {
            String userId = jwtService.extractUserId(authHeader);
            request.setAttribute("userId", userId);
            return true;
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
            return false;
        }
    }
}
