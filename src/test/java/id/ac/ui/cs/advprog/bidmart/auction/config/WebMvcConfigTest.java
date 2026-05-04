package id.ac.ui.cs.advprog.bidmart.auction.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebMvcConfigTest {

    @Mock
    private JwtInterceptor jwtInterceptor;

    @Mock
    private InterceptorRegistry registry;

    @Mock
    private InterceptorRegistration registration;

    @InjectMocks
    private WebMvcConfig webMvcConfig;

    @Test
    void testAddInterceptors() {
        when(registry.addInterceptor(jwtInterceptor)).thenReturn(registration);
        when(registration.addPathPatterns(anyString())).thenReturn(registration);

        webMvcConfig.addInterceptors(registry);

        verify(registry).addInterceptor(jwtInterceptor);
        verify(registration).addPathPatterns("/api/auctions/**");
    }
}
