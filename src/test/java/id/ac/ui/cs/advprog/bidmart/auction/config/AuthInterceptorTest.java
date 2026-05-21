package id.ac.ui.cs.advprog.bidmart.auction.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    @ParameterizedTest
    @CsvSource({
        "null, POST",
        "'   ', PATCH",
        "'', DELETE"
    })
    void testPreHandleBlockedMethodsWithoutUserId(String headerValue, String method) throws Exception {
        String actualHeaderValue = "null".equals(headerValue) ? null : headerValue;
        when(request.getHeader("X-User-Id")).thenReturn(actualHeaderValue);
        when(request.getMethod()).thenReturn(method);

        boolean result = authInterceptor.preHandle(request, response, new Object());

        assertFalse(result);
        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "X-User-Id header is required for this action");
    }
}
