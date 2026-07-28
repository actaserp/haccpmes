package mes.app.interceptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

/**
 * 스마트공장 사업관리시스템 로그 수집 API 연동.
 *
 * [전송 방식] 요청 발생 시마다 즉시 전송(비동기).
 *
 * [참고] 수집 서버는 직전 성공 이후 10분 이내 전송분을 AP1029 로 응답하며
 *        실제 DB 에는 적재하지 않는다. 따라서 실제 적재는 10분에 1건이 상한이다.
 *        (규격서: 하루 성공 144건 = 24h / 10min)
 *        또한 하루 요청 5,000건 초과 시 차단될 수 있으므로(AP1031)
 *        일일 요청 건수를 카운트하여 경고 로그를 남긴다.
 */
@Slf4j
@Service
public class SmartFactoryLogService {

    @Value("${smartfactory.log.crtfc-key}")
    private String apiKey;

    @Value("${smartfactory.log.url:https://log.smart-factory.kr/apisvc/sendLogDataJSON.do}")
    private String apiUrl;

    /** 전송 제외 IP. 값이 '.' 으로 끝나면 대역(접두어) 매칭으로 동작한다. */
    @Value("${smartfactory.log.excluded-ips:}")
    private String excludedIpsRaw;

    private List<String> excludedIps = Collections.emptyList();

    private static final DateTimeFormatter LOG_DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /** dataUsgqty 는 Integer(10) 이므로 10억 미만이어야 한다 (초과 시 AP1026) */
    private static final long MAX_DATA_USG_QTY = 999_999_999L;

    /** 규격서: 하루 요청 5,000건 초과 시 차단될 수 있음 (AP1031) */
    private static final int DAILY_REQUEST_LIMIT = 5000;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    /** 일일 카운터 (모니터링용) */
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private volatile LocalDate counterDate = LocalDate.now();

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

    // ────────────────────────────────────────────────────────────
    // 전송
    // ────────────────────────────────────────────────────────────

    /** 인터셉터에서 호출 (데이터 사용량 포함) */
    @Async
    public void sendLog(String useSe, String userId, String ip, long dataBytes) {

        if (isExcluded(ip)) {
            return;
        }

        // DO6999(테스트 정보)는 통계 제외 대상 코드이므로 운영 경로에서는 전송하지 않는다.
        if ("DO6999".equals(useSe)) {
            log.debug("[스마트공장 API] 테스트 코드(DO6999) 전송 생략 | 사용자: {}", userId);
            return;
        }

        rollCounterIfNeeded();

        int reqNo = requestCount.incrementAndGet();
        if (reqNo == DAILY_REQUEST_LIMIT - 500) {
            log.warn("[스마트공장 API] 일일 요청 {}건 도달. 5,000건 초과 시 차단될 수 있음", reqNo);
        }

        long usage = Math.max(0L, Math.min(dataBytes, MAX_DATA_USG_QTY));

        try {
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("crtfcKey", apiKey);
            dataMap.put("logDt", LocalDateTime.now().format(LOG_DT_FMT));
            dataMap.put("useSe", useSe);
            dataMap.put("sysUser", truncate(userId, 60));
            dataMap.put("conectIp", truncate(ip, 30));
            dataMap.put("dataUsgqty", usage);

            String jsonStr = objectMapper.writeValueAsString(dataMap);

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("logData", jsonStr);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, headers);
            String response = restTemplate.postForObject(apiUrl, entity, String.class);

            handleResponse(response, useSe, userId, ip, usage);

        } catch (Exception e) {
            log.error("[스마트공장 API] 전송 오류 | 코드: {} | 사용자: {} | IP: {} | 메시지: {}",
                    useSe, userId, ip, e.getMessage(), e);
        }
    }

    /** 기존 호출부(AccountController 등) 호환용. 데이터 사용량은 0. */
    public void sendLog(String useSe, String userId, String ip) {
        sendLog(useSe, userId, ip, 0L);
    }

    /** 응답의 recptnRsltCd 를 파싱하여 실제 적재 여부를 판정한다. */
    private void handleResponse(String response, String useSe, String userId, String ip, long usage) {

        String code = "UNKNOWN";
        String desc = "";
        try {
            JsonNode result = objectMapper.readTree(response).path("result");
            code = result.path("recptnRsltCd").asText("UNKNOWN");
            desc = result.path("recptnRslt").asText("");
        } catch (Exception e) {
            log.warn("[스마트공장 API] 응답 파싱 실패 | 원문: {}", response);
        }

        // 규격서: 정상 전송 시 반환값은 AP1002(데이터 이관 완료).
        if ("AP1002".equals(code)) {
            int n = successCount.incrementAndGet();
            log.info("[스마트공장 API] 적재 완료 | 코드: {} | 사용자: {} | IP: {} | 사용량: {}B | 금일 적재: {} (요청 {})",
                    useSe, userId, ip, usage, n, requestCount.get());

        } else if ("AP1029".equals(code)) {
            // 10분 주기 미달. 정상 응답이지만 DB 에는 적재되지 않는다.
            log.debug("[스마트공장 API] 미적재(AP1029) | 코드: {} | 사용자: {}", useSe, userId);

        } else if ("AP1001".equals(code) || "AP1028".equals(code)) {
            log.info("[스마트공장 API] 수신 확인({}) | 적재 대상 아님 | 사용자: {}", code, userId);

        } else if ("AP1030".equals(code)) {
            log.error("[스마트공장 API] 수집 미대상(AP1030) | 과제 정보 누락 또는 수집기간 종료 - 사업관리시스템 확인 필요");

        } else if ("AP1031".equals(code)) {
            log.error("[스마트공장 API] 일일 요청 5,000건 초과(AP1031) | 금일 요청: {}", requestCount.get());

        } else if ("AP1032".equals(code) || "AP1033".equals(code)) {
            log.error("[스마트공장 API] 일일 한도 초과 | 코드: {} ({})", code, desc);

        } else {
            log.error("[스마트공장 API] 전송 실패 | 코드: {} ({}) | 원문: {}", code, desc, response);
        }
    }

    // ────────────────────────────────────────────────────────────
    // 보조
    // ────────────────────────────────────────────────────────────

    private void rollCounterIfNeeded() {
        LocalDate today = LocalDate.now();
        if (!today.equals(counterDate)) {
            synchronized (this) {
                if (!today.equals(counterDate)) {
                    log.info("[스마트공장 API] 일일 집계 마감 | {} | 요청 {}건 / 적재 {}건",
                            counterDate, requestCount.get(), successCount.get());
                    counterDate = today;
                    requestCount.set(0);
                    successCount.set(0);
                }
            }
        }
    }

    private String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max) : value;
    }
}