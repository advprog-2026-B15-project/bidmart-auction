package id.ac.ui.cs.advprog.bidmart.auction.service;


import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SseEmitterService {

    
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emittersMap = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String auctionId) {
        SseEmitter emitter = new SseEmitter(300_000L);
        
        emittersMap.computeIfAbsent(auctionId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(auctionId, emitter));
        emitter.onTimeout(() -> removeEmitter(auctionId, emitter));
        emitter.onError(e -> removeEmitter(auctionId, emitter));

        try {
            emitter.send(SseEmitter.event().name("INIT").data("Connected to auction " + auctionId));
        } catch (IOException e) {
            removeEmitter(auctionId, emitter);
        }

        return emitter;
    }

    @Async("notificationExecutor")
    public void broadcast(String auctionId, Object payload) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersMap.get(auctionId);
        if (emitters != null) {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("BID_UPDATE").data(payload));
                } catch (IOException e) {
                    removeEmitter(auctionId, emitter);
                }
            }
        }
    }

    @Scheduled(fixedRateString = "${bidmart.auction.sse.heartbeat-ms:25000}")
    public void sendHeartbeat() {
        emittersMap.forEach((auctionId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("HEARTBEAT").data("ping"));
                } catch (IOException e) {
                    removeEmitter(auctionId, emitter);
                }
            }
        });
    }

    private void removeEmitter(String auctionId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersMap.get(auctionId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                emittersMap.remove(auctionId);
            }
        }
    }
}
