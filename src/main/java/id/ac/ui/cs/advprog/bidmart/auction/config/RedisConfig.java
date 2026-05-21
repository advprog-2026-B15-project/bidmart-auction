package id.ac.ui.cs.advprog.bidmart.auction.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisConfig {
    
    @Value("${spring.data.redis.url:redis://localhost:6379}")
    private String redisUrl;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.setNettyThreads(16);
        config.useSingleServer()
              .setAddress(redisUrl)
              .setTimeout(10000)
              .setConnectTimeout(10000)
              .setRetryAttempts(5)
              .setRetryInterval(1500);
        return Redisson.create(config);
    }
}
