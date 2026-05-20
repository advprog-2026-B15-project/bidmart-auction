package id.ac.ui.cs.advprog.bidmart.auction.config;

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
class AuthInterceptorTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @InjectMocks
    private AuthInterceptor authInterceptor;

    @BeforeEach
    void setUp() {
    }

    @Test
    void testPreHandleGetRequestWithoutUserId() throws Exception {
        when(request.getHeader("X-User-Id")).thenReturn(null);
        when(request.getMethod()).thenReturn("GET");

        boolean result = authInterceptor.preHandle(request, response, new Object());

        assertTrue(result);
        verify(request, never()).setAttribute(anyString(), any());
    }

    @Test
    void testPreHandleRequestWithUserId() throws Exception {
        when(request.getHeader("X-User-Id")).thenReturn("user-123");
        when(request.getMethod()).thenReturn("GET");

        boolean result = authInterceptor.preHandle(request, response, new Object());

        assertTrue(result);
        verify(request).setAttribute("userId", "user-123");
    }

    @Test
    void testPreHandlePostRequestWithoutUserId() throws Exception {
        when(request.getHeader("X-User-Id")).thenReturn(null);
        when(request.getMethod()).thenReturn("POST");

        boolean result = authInterceptor.preHandle(request, response, new Object());

        assertFalse(result);
        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "X-User-Id header is required for this action");
    }

    @Test
    void testPreHandlePatchRequestWithoutUserId_isBlocked() throws Exception {
        when(request.getHeader("X-User-Id")).thenReturn("   ");
        when(request.getMethod()).thenReturn("PATCH");

        boolean result = authInterceptor.preHandle(request, response, new Object());

        assertFalse(result);
        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "X-User-Id header is required for this action");
    }

    @Test
    void testPreHandleDeleteRequestWithoutUserId_isBlocked() throws Exception {
        when(request.getHeader("X-User-Id")).thenReturn("");
        when(request.getMethod()).thenReturn("DELETE");

        boolean result = authInterceptor.preHandle(request, response, new Object());

        assertFalse(result);
        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "X-User-Id header is required for this action");
    }
}
