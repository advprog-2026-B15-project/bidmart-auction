package id.ac.ui.cs.advprog.bidmart.auction;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.redisson.api.RedissonClient;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;

import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {"spring.main.allow-bean-definition-overriding=true"})
@Import(BidmartAuctionApplicationTests.TestConfig.class)
class BidmartAuctionApplicationTests {

    @MockitoBean
    private RedissonClient redissonClient;

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("auction", "bidHistory");
        }
    }

    @Test
    void contextLoads() {
    }

}
