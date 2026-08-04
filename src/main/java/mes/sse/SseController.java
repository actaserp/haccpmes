package mes.sse;

import mes.sse.Transaction.SseClient;
import mes.sse.Transaction.SseSubject;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/sse")
public class SseController {

    private final SseSubject subject = new SseSubject();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    void init() {
        heartbeat.scheduleAtFixedRate(subject::ping, 15, 15, TimeUnit.SECONDS);
    }

    @PreDestroy
    void shutdown() {
        heartbeat.shutdownNow();
        subject.shutdown();
    }

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestParam String spjangcd) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        SseClient client = new SseClient(emitter);

        subject.addObservers(spjangcd, client);

        emitter.onCompletion(() -> {
            subject.removeObserver(spjangcd, client);
        });
        emitter.onTimeout(() -> {
            subject.removeObserver(spjangcd, client);
        });
        emitter.onError(e -> {
            subject.removeObserver(spjangcd, client);
        });

        try {
            emitter.send(SseEmitter.event().name("CONNECTED").data("연결"));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }


    public void SendingToClientMessage(String spjangcd, String accnum) {
        subject.notify(spjangcd, accnum);
    }

    /*private void startBroadcastTestMessages() {
        final String spjangcd = "ZZ";
        final String accnum = "94160200188218";

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        // 3분 간격으로 ZZ에게만 메시지 전송
        scheduler.scheduleAtFixedRate(() -> {
            SendingToClientMessage(spjangcd, accnum + " (" + spjangcd + ")");
        }, 0, 120, TimeUnit.SECONDS); // 바로 시작, 3분마다 반복
    }*/

}
