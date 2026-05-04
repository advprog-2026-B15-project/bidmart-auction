package id.ac.ui.cs.advprog.bidmart.auction.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisConfig {

    /**
     * URL koneksi Redis.
     * Untuk Upstash, gunakan format: rediss://:<password>@<host>:<port> (SSL).
     * Untuk penggunaan lokal, gunakan: redis://localhost:6379.
     */
    @Value("${spring.data.redis.url:redis://localhost:6379}")
    private String redisUrl;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + redisHost + ":" + redisPort);
        return Redisson.create(config);
    }
}
