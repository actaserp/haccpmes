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
        } catch (IOException e) {
            System.out.println("emitter.send 실패 → 연결 종료 → 클라이언트가 재연결 시도함");
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
