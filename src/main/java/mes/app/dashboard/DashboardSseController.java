package mes.app.dashboard;

import lombok.RequiredArgsConstructor;
import mes.app.common.SseBroadcaster;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
public class DashboardSseController {

    private final SseBroadcaster broadcaster;

    @GetMapping(value = "/sse/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return broadcaster.registerEmitter();
    }

    @GetMapping(value = "/sse/test", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter test() throws IOException {
        SseEmitter em = new SseEmitter(60_000L);
        em.send(SseEmitter.event().name("hi").data("test"));
        return em;
    }
}