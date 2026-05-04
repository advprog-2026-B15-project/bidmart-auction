package id.ac.ui.cs.advprog.bidmart.auction;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.redisson.api.RedissonClient;

@SpringBootTest
@ActiveProfiles("test")
class BidmartAuctionApplicationTests {

    @MockitoBean
    private RedissonClient redissonClient;

    @Test
    void contextLoads() {
    }

}
