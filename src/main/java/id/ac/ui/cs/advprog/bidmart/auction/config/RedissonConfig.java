package id.ac.ui.cs.advprog.bidmart.auction.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Value("${bidmart.redis.url}")
    private String redisUrl;

    @Value("${bidmart.redis.password}")
    private String redisPassword;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress(redisUrl)
                .setPassword(redisPassword != null && !redisPassword.isBlank() ? redisPassword : null);
        return Redisson.create(config);
    }
}
