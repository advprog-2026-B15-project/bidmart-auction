package id.ac.ui.cs.advprog.bidmart.auction;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.redisson.api.RedissonClient;

@SpringBootTest
@ActiveProfiles("test")
class BidmartAuctionApplicationTests {

    @MockBean
    private RedissonClient redissonClient;

    @Test
    void contextLoads() {
    }

}
