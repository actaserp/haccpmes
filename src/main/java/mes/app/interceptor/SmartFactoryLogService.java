package mes.app.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import mes.app.aop.ControllerExecutionTimeAspect;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class SmartFactoryLogService {

    private final String API_KEY = "$5$API$BdEMRE/lvGbEVKfHgLB.VGxKeF8kQn0XZRR6WXDfDV2"; // 발급받은 키
    private final String API_URL = "https://log.smart-factory.kr/apisvc/sendLogDataJSON.do";
    private final ControllerExecutionTimeAspect controllerExecutionTimeAspect;

    public SmartFactoryLogService(ControllerExecutionTimeAspect controllerExecutionTimeAspect) {
        this.controllerExecutionTimeAspect = controllerExecutionTimeAspect;
    }

    @Async
    public void sendLog(String useSe, String userId, String ip) {
        try {

            if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "14.36.41.134".equals(ip)) {
                log.info("[스마트공장 API] 전송 스킵 대상입니다. (접속 IP: {}, 사용자: {})", ip, userId);
                return;
            }

            String cleanIp = (ip == null || ip.contains(":")) ? "127.0.0.1" : ip;
            String logDt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));

            // 1. 실제 보낼 데이터 Map 생성
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("crtfcKey", API_KEY);
            dataMap.put("logDt", logDt);
            dataMap.put("useSe", useSe);
            dataMap.put("sysUser", userId);
            dataMap.put("conectIp", cleanIp);
            dataMap.put("dataUsgqty", 0);

            // 2. Map을 JSON 문자열로 변환 (중요!)
            String jsonStr = new ObjectMapper().writeValueAsString(dataMap);

            // 3. Form Data 형태로 구성 (logData=JSON문자열)
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("logData", jsonStr);

            // 4. 헤더 설정 (Form 전송 방식)
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, headers);

            RestTemplate restTemplate = new RestTemplate();
            String response = restTemplate.postForObject(API_URL, entity, String.class);

            log.info("[스마트공장 API] 전송 성공 | 코드: {} | 사용자: {} | IP: {} | 응답: {}", useSe, userId, ip, response);

        } catch (Exception e) {
            log.error("[스마트공장 API] 전송 오류 | 코드: {} | 사용자: {} | IP: {} | 메시지: {}", useSe, userId, ip, e.getMessage(), e);
        }
    }
}