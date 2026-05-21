package id.ac.ui.cs.advprog.bidmart.auction.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String userId = request.getHeader("X-User-Id");
        String method = request.getMethod();

        if (userId != null && !userId.trim().isEmpty()) {
            request.setAttribute("userId", userId);
            return true;
        }

        if ("GET".equalsIgnoreCase(method)) {
            return true;
        }

        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "X-User-Id header is required for this action");
        return false;
    }
}
