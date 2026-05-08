package id.ac.ui.cs.advprog.bidmart.auction.config;

import id.ac.ui.cs.advprog.bidmart.auction.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtInterceptorTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @InjectMocks
    private JwtInterceptor jwtInterceptor;

    @BeforeEach
    void setUp() {
    }

    @Test
    void testPreHandleGetRequestWithoutToken() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn(null);

        boolean result = jwtInterceptor.preHandle(request, response, new Object());

        assertTrue(result);
        verify(request, never()).setAttribute(anyString(), any());
    }

    @Test
    void testPreHandleGetRequestWithValidToken() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(jwtService.extractUserId("Bearer valid-token")).thenReturn("user@mail.com");

        boolean result = jwtInterceptor.preHandle(request, response, new Object());

        assertTrue(result);
        verify(request).setAttribute("userId", "user@mail.com");
    }

    @Test
    void testPreHandleGetRequestWithInvalidToken() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid-token");
        when(jwtService.extractUserId("Bearer invalid-token")).thenThrow(new RuntimeException("Invalid"));

        boolean result = jwtInterceptor.preHandle(request, response, new Object());

        assertTrue(result);
        verify(request, never()).setAttribute(eq("userId"), any());
    }

    @Test
    void testPreHandlePostRequestWithoutToken() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Authorization")).thenReturn(null);

        boolean result = jwtInterceptor.preHandle(request, response, new Object());

        assertFalse(result);
        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authorization token is required for this action");
    }

    @Test
    void testPreHandlePostRequestWithValidToken() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(jwtService.extractUserId("Bearer valid-token")).thenReturn("user@mail.com");

        boolean result = jwtInterceptor.preHandle(request, response, new Object());

        assertTrue(result);
        verify(request).setAttribute("userId", "user@mail.com");
    }

    @Test
    void testPreHandlePostRequestWithInvalidToken() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid-token");
        when(jwtService.extractUserId("Bearer invalid-token")).thenThrow(new RuntimeException("Invalid"));

        boolean result = jwtInterceptor.preHandle(request, response, new Object());

        assertFalse(result);
        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
    }
}
