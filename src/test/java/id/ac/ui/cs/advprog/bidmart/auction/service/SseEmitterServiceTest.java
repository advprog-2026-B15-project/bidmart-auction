package id.ac.ui.cs.advprog.bidmart.auction.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SseEmitterServiceTest {

    private SseEmitterService sseEmitterService;

    @BeforeEach
    void setUp() {
        sseEmitterService = new SseEmitterService();
    }

    @Test
    void testSubscribeReturnsEmitter() {
        SseEmitter emitter = sseEmitterService.subscribe("auction-1");
        assertNotNull(emitter);
    }

    @Test
    void testSubscribeMultipleClientsToSameAuction() {
        SseEmitter emitter1 = sseEmitterService.subscribe("auction-1");
        SseEmitter emitter2 = sseEmitterService.subscribe("auction-1");

        assertNotNull(emitter1);
        assertNotNull(emitter2);
        assertNotSame(emitter1, emitter2);
    }

    @Test
    void testSubscribeDifferentAuctions() {
        SseEmitter emitter1 = sseEmitterService.subscribe("auction-1");
        SseEmitter emitter2 = sseEmitterService.subscribe("auction-2");

        assertNotNull(emitter1);
        assertNotNull(emitter2);
    }

    @Test
    void testBroadcastToNoSubscriberDoesNotThrow() {
        assertDoesNotThrow(() -> sseEmitterService.broadcast("auction-no-subscribers", Map.of("amount", 1000L)));
    }

    @Test
    void testBroadcastToSubscriber() {
        SseEmitter emitter = sseEmitterService.subscribe("auction-1");
        assertNotNull(emitter);
        assertDoesNotThrow(() -> sseEmitterService.broadcast("auction-1", Map.of("amount", 5000L, "bidderId", "buyer-1")));
    }
}
