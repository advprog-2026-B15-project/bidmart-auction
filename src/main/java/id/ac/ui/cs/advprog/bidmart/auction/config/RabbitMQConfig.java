package id.ac.ui.cs.advprog.bidmart.auction.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "bidmart.auction.exchange";
    public static final String ROUTING_KEY_BID_PLACED = "auction.event.bid-placed";
    public static final String ROUTING_KEY_WINNER_DETERMINED = "auction.event.winner-determined";
    public static final String ROUTING_KEY_AUCTION_CLOSED = "auction.event.auction-closed";
    
    public static final String LISTING_PUBLISHED_EXCHANGE = "auction.events";
    public static final String LISTING_PUBLISHED_QUEUE = "bidmart.auction.listing-published.queue";
    public static final String ROUTING_KEY_LISTING_PUBLISHED = "auction.event.listing-published";

    public static final String DLX_NAME = "bidmart.auction.dlx";
    public static final String DLQ_NAME = "bidmart.auction.dlq";
    public static final String DLQ_ROUTING_KEY = "dlq";

    @Bean
    public TopicExchange auctionExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public TopicExchange catalogEventsExchange() {
        return new TopicExchange(LISTING_PUBLISHED_EXCHANGE);
    }

    @Bean
    public Queue listingPublishedQueue() {
        return QueueBuilder.durable(LISTING_PUBLISHED_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_NAME)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding listingPublishedBinding() {
        return BindingBuilder.bind(listingPublishedQueue())
                .to(catalogEventsExchange())
                .with(ROUTING_KEY_LISTING_PUBLISHED);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_NAME, true, false);
    }

    @Bean
    public Queue auctionDeadLetterQueue() {
        return QueueBuilder.durable(DLQ_NAME).build();
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(auctionDeadLetterQueue())
                .to(deadLetterExchange())
                .with(DLQ_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());

        template.setMandatory(true);

        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                String id = correlationData != null ? correlationData.getId() : "unknown";
                log.error("[RabbitMQ] Message NOT confirmed by broker. id={}, cause={}", id, cause);
            }
        });

        template.setReturnsCallback(returned ->
            log.error("[RabbitMQ] Message returned unroutable: exchange={}, routingKey={}, replyCode={}, replyText={}",
                    returned.getExchange(),
                    returned.getRoutingKey(),
                    returned.getReplyCode(),
                    returned.getReplyText())
        );

        return template;
    }
}
