package mes.sse.Transaction;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SseSubject {

    private final Map<String, List<SseClient>> observers = new ConcurrentHashMap<>();
    private final ExecutorService sendPool = Executors.newFixedThreadPool(4);

    public void addObservers(String key, SseClient client) {
        observers.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(client);
    }

    public void removeObserver(String key, SseClient client) {
        observers.computeIfPresent(key, (k, list) -> {
            list.remove(client);
            return list.isEmpty() ? null : list;      // 원자적 처리
        });
    }

    public void notify(String key, String message) {
        List<SseClient> list = observers.get(key);
        if (list == null) return;
        list.forEach(c -> sendPool.submit(() -> c.send(message)));   // 비동기
    }

    public void ping() {
        observers.values().forEach(list ->
                list.forEach(c -> sendPool.submit(c::ping)));
    }

    public void shutdown() {
        sendPool.shutdownNow();
    }
}
