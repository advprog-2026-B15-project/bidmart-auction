package id.ac.ui.cs.advprog.bidmart.auction.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RabbitMQConfigTest {

    @Mock
    private ConnectionFactory connectionFactory;

    private final RabbitMQConfig config = new RabbitMQConfig();

    @Test
    void auctionExchange_isTopicExchangeWithCorrectName() {
        TopicExchange exchange = config.auctionExchange();
        assertEquals(RabbitMQConfig.EXCHANGE_NAME, exchange.getName());
        assertTrue(exchange.isDurable());
    }

    @Test
    void deadLetterExchange_isDirectExchangeWithCorrectName() {
        DirectExchange dlx = config.deadLetterExchange();
        assertEquals(RabbitMQConfig.DLX_NAME, dlx.getName());
        assertTrue(dlx.isDurable());
        assertFalse(dlx.isAutoDelete());
    }

    @Test
    void auctionDeadLetterQueue_isDurableWithCorrectName() {
        Queue dlq = config.auctionDeadLetterQueue();
        assertEquals(RabbitMQConfig.DLQ_NAME, dlq.getName());
        assertTrue(dlq.isDurable());
    }

    @Test
    void dlqBinding_bindsCorrectQueueToExchangeWithRoutingKey() {
        Binding binding = config.dlqBinding();
        assertEquals(RabbitMQConfig.DLQ_NAME, binding.getDestination());
        assertEquals(RabbitMQConfig.DLX_NAME, binding.getExchange());
        assertEquals(RabbitMQConfig.DLQ_ROUTING_KEY, binding.getRoutingKey());
    }

    @Test
    void jsonMessageConverter_isJackson2() {
        MessageConverter converter = config.jsonMessageConverter();
        assertInstanceOf(Jackson2JsonMessageConverter.class, converter);
    }

    @Test
    void rabbitTemplate_hasMessageConverterAndMandatoryFlag() {
        RabbitTemplate template = config.rabbitTemplate(connectionFactory);

        assertNotNull(template);
        assertTrue(template.isMandatoryFor(null));
    }

    @Test
    void rabbitTemplate_isNotNullAndConfiguredWithConverter() {
        RabbitTemplate template = config.rabbitTemplate(connectionFactory);
        assertNotNull(template);
        assertNotNull(template.getMessageConverter());
        assertInstanceOf(Jackson2JsonMessageConverter.class, template.getMessageConverter());
    }
}
