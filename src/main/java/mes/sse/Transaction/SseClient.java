package mes.sse.Transaction;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

@Slf4j
public class SseClient implements SseObserver{

    private final SseEmitter emitter;

    public SseClient(SseEmitter emitter) {
        this.emitter = emitter;
    }

    public void send(String message) {
        try {
            emitter.send(SseEmitter.event().data(message));
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    public SseEmitter getEmitter(){
        return emitter;
    }

    public void ping() {
        try {
            emitter.send(SseEmitter.event().comment("keepalive"));
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }
}
