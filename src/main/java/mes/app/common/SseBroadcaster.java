package mes.app.common;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Component
public class SseBroadcaster {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ExecutorService sendPool = Executors.newFixedThreadPool(4);
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    void init() {
        heartbeat.scheduleAtFixedRate(this::ping, 15, 15, TimeUnit.SECONDS);
    }

    @PreDestroy
    void shutdown() {
        heartbeat.shutdownNow();
        sendPool.shutdownNow();
        emitters.forEach(SseEmitter::complete);
    }

    public SseEmitter registerEmitter() {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);   // 0L 금지
        emitters.add(emitter);

        Runnable cleanup = () -> emitters.remove(emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        return emitter;
    }

    public void sendEvent(String eventName, Object data) {
        emitters.forEach(em -> sendPool.submit(() -> {
            try {
                em.send(SseEmitter.event().name(eventName).data(data));
            } catch (Exception e) {
                em.completeWithError(e);      // onError가 리스트에서 제거
            }
        }));
    }

    private void ping() {
        emitters.forEach(em -> sendPool.submit(() -> {
            try {
                em.send(SseEmitter.event().comment("keepalive"));
            } catch (Exception e) {
                em.completeWithError(e);
            }
        }));
    }
}