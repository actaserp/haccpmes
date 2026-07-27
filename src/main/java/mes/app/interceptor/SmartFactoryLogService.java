package mes.app.interceptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

/**
 * 스마트공장 사업관리시스템 로그 수집 API 연동.
 *
 * [변경 사유]
 * 기존에는 요청이 발생할 때마다 즉시 API 를 호출했으나,
 * 규격상 수신 주기가 10분이므로 그 이전 전송분은 모두 AP1029 로 응답되며
 * 실제 DB 에는 적재되지 않는다. (하루 성공 상한 144건 = 24h / 10min)
 *
 * 따라서 인터셉터는 큐에 적재만 하고, 본 서비스가 10분 주기로 1건씩 전송한다.
 *
 * [주의] 큐가 메모리 기반이므로 단일 서버 환경 전제.
 *        WAS 를 2대 이상 운용할 경우 각 인스턴스가 별도로 전송하여
 *        다시 AP1029 가 발생하므로 DB 테이블 기반 큐 + 락으로 전환해야 한다.
 */
@Slf4j
@Service
public class SmartFactoryLogService {

    @Value("${smartfactory.log.crtfc-key}")
    private String apiKey;

    @Value("${smartfactory.log.url:https://log.smart-factory.kr/apisvc/sendLogDataJSON.do}")
    private String apiUrl;

    /**
     * 전송 제외 IP. application.yml 에서 관리한다.
     * 값이 '.' 으로 끝나면 대역(접두어) 매칭으로 동작한다. 예) 220.77.13.
     */
    @Value("${smartfactory.log.excluded-ips:}")
    private String excludedIpsRaw;

    private List<String> excludedIps = Collections.emptyList();

    @PostConstruct
    void initExcludedIps() {
        excludedIps = (excludedIpsRaw == null || excludedIpsRaw.trim().isEmpty())
                ? Collections.emptyList()
                : Arrays.stream(excludedIpsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        log.info("[스마트공장 API] 로그 제외 IP 설정: {}", excludedIps);
    }

    /** 로컬/사내 등 통계에서 빠져야 하는 접속인지 판단 */
    private boolean isExcluded(String ip) {
        if (ip == null || ip.trim().isEmpty()) return true;
        if (ip.contains(":")) return true;                      // IPv6 (규격 30자 제한 + 로컬)
        if (ip.startsWith("127.") || ip.startsWith("10.")
                || ip.startsWith("192.168.") || ip.startsWith("172.16.")) {
            return true;                                        // 사설/루프백
        }
        for (String rule : excludedIps) {
            if (rule.endsWith(".")) {
                if (ip.startsWith(rule)) return true;
            } else if (rule.equals(ip)) {
                return true;
            }
        }
        return false;
    }

    /** useSe 중요도 우선순위 (앞쪽이 높음) */
    private static final List<String> USE_SE_PRIORITY =
            Arrays.asList("DO6007", "DO6006", "DO6005", "DO6004", "DO6001", "DO6002", "DO6003");

    private static final DateTimeFormatter LOG_DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /** dataUsgqty 는 Integer(10) 이므로 10억 미만이어야 한다 (초과 시 AP1026) */
    private static final long MAX_DATA_USG_QTY = 999_999_999L;

    /** 규격서: 하루 성공 144건 초과 시 AP1032 */
    private static final int DAILY_SUCCESS_LIMIT = 144;

    private final AtomicReference<PendingLog> pending = new AtomicReference<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    /** 하루 전송 건수 모니터링용 */
    private LocalDate counterDate = LocalDate.now();
    private int successCount = 0;

    /** 10분 구간 동안 누적되는 로그 */
    private static class PendingLog {
        final String useSe;
        final String userId;
        final String ip;
        final long bytes;
        final int count;

        PendingLog(String useSe, String userId, String ip, long bytes, int count) {
            this.useSe = useSe;
            this.userId = userId;
            this.ip = ip;
            this.bytes = bytes;
            this.count = count;
        }
    }

    /**
     * 인터셉터에서 호출. API 호출 없이 메모리에만 누적한다.
     * 동일 구간에 여러 활동이 들어오면 중요도가 높은 useSe 를 채택하고,
     * 데이터 사용량은 구간 전체를 합산한다.
     */
    public void enqueue(String useSe, String userId, String ip, long bytes) {

        if (isExcluded(ip)) {
            return;
        }

        // DO6999(테스트 정보)는 통계 제외 대상 코드이므로 운영 경로에서는 전송하지 않는다.
        if ("DO6999".equals(useSe)) {
            log.debug("[스마트공장 API] 테스트 코드(DO6999) 전송 생략 | 사용자: {}", userId);
            return;
        }

        pending.updateAndGet(prev -> {
            if (prev == null) {
                return new PendingLog(useSe, userId, ip, bytes, 1);
            }
            long merged = prev.bytes + bytes;
            int cnt = prev.count + 1;
            if (isHigherPriority(useSe, prev.useSe)) {
                return new PendingLog(useSe, userId, ip, merged, cnt);
            }
            return new PendingLog(prev.useSe, prev.userId, prev.ip, merged, cnt);
        });
    }

    /**
     * 기존 호출부(AccountController 등) 호환용.
     * 즉시 전송하지 않고 큐에 적재만 한다. 데이터 사용량은 알 수 없어 0.
     */
    public void sendLog(String useSe, String userId, String ip) {
        enqueue(useSe, userId, ip, 0L);
    }

    private boolean isHigherPriority(String candidate, String current) {
        int c = USE_SE_PRIORITY.indexOf(candidate);
        int u = USE_SE_PRIORITY.indexOf(current);
        if (c < 0) return false;
        if (u < 0) return true;
        return c < u;
    }

    /**
     * 10분 주기 전송. 초기 지연 1분.
     * 누적된 로그가 없으면(=해당 구간에 사용자 활동 없음) 전송하지 않는다.
     */
    @Scheduled(initialDelay = 60_000L, fixedDelay = 605_000L)
    public void flush() {

        PendingLog target = pending.getAndSet(null);
        if (target == null) {
            log.debug("[스마트공장 API] 전송 대상 없음 - 스킵");
            return;
        }

        rollCounterIfNeeded();
        if (successCount >= DAILY_SUCCESS_LIMIT) {
            log.warn("[스마트공장 API] 일일 성공 한도({}) 도달 - 이번 주기 전송 생략", DAILY_SUCCESS_LIMIT);
            return;
        }

        try {
            long usage = Math.max(0L, Math.min(target.bytes, MAX_DATA_USG_QTY));

            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("crtfcKey", apiKey);
            dataMap.put("logDt", LocalDateTime.now().format(LOG_DT_FMT));
            dataMap.put("useSe", target.useSe);
            dataMap.put("sysUser", truncate(target.userId, 60));
            dataMap.put("conectIp", truncate(target.ip, 30));
            dataMap.put("dataUsgqty", usage);

            String jsonStr = objectMapper.writeValueAsString(dataMap);

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("logData", jsonStr);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, headers);
            String response = restTemplate.postForObject(apiUrl, entity, String.class);

            handleResponse(response, target, usage);

        } catch (Exception e) {
            log.error("[스마트공장 API] 전송 오류 | 코드: {} | 사용자: {} | IP: {} | 메시지: {}",
                    target.useSe, target.userId, target.ip, e.getMessage(), e);
        }
    }

    /** 응답의 recptnRsltCd 를 파싱하여 실제 적재 여부를 판정한다. */
    private void handleResponse(String response, PendingLog target, long usage) {

        String code = "UNKNOWN";
        String desc = "";
        try {
            JsonNode result = objectMapper.readTree(response).path("result");
            code = result.path("recptnRsltCd").asText("UNKNOWN");
            desc = result.path("recptnRslt").asText("");
        } catch (Exception e) {
            log.warn("[스마트공장 API] 응답 파싱 실패 | 원문: {}", response);
        }

        rollCounterIfNeeded();

        if ("AP1002".equals(code) || "AP1001".equals(code)) {
            successCount++;
            log.info("[스마트공장 API] 적재 완료 | 코드: {} | 사용자: {} | IP: {} | 사용량: {}B | 병합: {}건 | 금일 성공: {}/{}",
                    target.useSe, target.userId, target.ip, usage, target.count, successCount, DAILY_SUCCESS_LIMIT);
        } else if ("AP1029".equals(code)) {
            log.warn("[스마트공장 API] 전송주기 미달로 미적재(AP1029) | 병합 {}건 유실 | 서버 다중화 여부 확인 필요", target.count);
        } else if ("AP1030".equals(code)) {
            log.error("[스마트공장 API] 수집 미대상(AP1030) | 과제 정보 누락 또는 수집기간 종료 - 사업관리시스템 확인 필요");
        } else if ("AP1031".equals(code) || "AP1032".equals(code) || "AP1033".equals(code)) {
            log.error("[스마트공장 API] 일일 한도 초과 | 코드: {} ({})", code, desc);
        } else {
            log.error("[스마트공장 API] 전송 실패 | 코드: {} ({}) | 원문: {}", code, desc, response);
        }
    }

    private void rollCounterIfNeeded() {
        LocalDate today = LocalDate.now();
        if (!today.equals(counterDate)) {
            counterDate = today;
            successCount = 0;
        }
    }

    private String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max) : value;
    }
}