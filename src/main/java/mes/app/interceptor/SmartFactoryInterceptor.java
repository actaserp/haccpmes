package mes.app.interceptor;

import lombok.extern.slf4j.Slf4j;
import mes.domain.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.WebUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * 변경점: API 를 직접 호출하지 않고 LogService 의 큐에 적재만 한다.
 * 실제 전송은 SmartFactoryLogService 의 10분 주기 스케줄러가 담당한다.
 */
@Slf4j
@Component
public class SmartFactoryInterceptor implements HandlerInterceptor {

    @Autowired
    SmartFactoryLogService logService;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {

        String uri = request.getRequestURI();
        String method = request.getMethod().toUpperCase();
        int status = response.getStatus();

        if (uri.contains("/login") && !"POST".equals(method)) {
            return;
        }

        String useSe = resolveUseSe(uri, method);

        String ip = getClientIp(request);
        if ("0:0:0:0:0:0:0:1".equals(ip)) {
            ip = "127.0.0.1";
        }

        String userId = resolveUserId(request);

        boolean isSuccess = (status >= 200 && status < 300);
        boolean isRedirectSuccess = (status == 302 && (useSe.equals("DO6001") || useSe.equals("DO6002")));

        if (isSuccess || isRedirectSuccess) {
            long bytes = calcDataUsage(request, response);
            logService.enqueue(useSe, userId, ip, bytes);
        }
    }

    /** 접속 구분(useSe) 판별 - 기존 로직 유지 */
    private String resolveUseSe(String uri, String method) {

        // [최우선순위] 명시적 입출고 API 패턴 (재고 변동 발생)
        if ("POST".equals(method) && (
                uri.equals("/api/inventory/material_inout/save") ||
                        uri.equals("/api/inventory/material_inout/save_balju") ||
                        uri.equals("/api/production/prod_result/chasu_del") ||
                        uri.equals("/api/production/prod_result/chasu_add") ||
                        uri.equals("/api/shipment/shipment_do_a/batch_save")
        )) {
            return "DO6007"; // 입출고
        }

        // [2순위] 일반 행위 기반 판별
        if (uri.contains("/login"))  return "DO6001";
        if (uri.contains("/logout")) return "DO6002";
        if (uri.contains("/delete") || uri.contains("/remove")) return "DO6006";
        if (uri.contains("/update") || uri.contains("/modify") || uri.contains("/edit")) return "DO6005";
        if (uri.contains("/insert") || uri.contains("/save")   || uri.contains("/add"))  return "DO6004";

        // [3순위] 키워드가 없을 경우 HTTP Method 로 추측
        switch (method) {
            case "POST":   return "DO6004";
            case "DELETE": return "DO6006";
            case "PUT":
            case "PATCH":  return "DO6005";
            default:       return "DO6003";
        }
    }

    private String resolveUserId(HttpServletRequest request) {
        String userId = "system";

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            Object principal = auth.getPrincipal();
            if (principal instanceof User) {
                userId = ((User) principal).getUsername();
            } else if (principal instanceof String) {
                userId = (String) principal;
            }
        } else {
            HttpSession session = request.getSession(false);
            if (session != null) {
                Object sessionUserId = session.getAttribute("username");
                if (sessionUserId != null) {
                    userId = sessionUserId.toString();
                }
            }
        }
        return userId;
    }

    /**
     * dataUsgqty 산출 = 요청 본문 바이트 + 응답 본문 바이트.
     * 규격서상 "등록/조회/수정/삭제 등에 활용된 데이터 크기(Byte)" 이므로
     * 조회(응답 위주)와 등록(요청 위주) 양쪽 모두를 반영한다.
     */
    private long calcDataUsage(HttpServletRequest request, HttpServletResponse response) {
        long total = 0L;

        int reqLen = request.getContentLength();
        if (reqLen > 0) {
            total += reqLen;
        }

        ContentCachingResponseWrapper wrapper =
                WebUtils.getNativeResponse(response, ContentCachingResponseWrapper.class);
        if (wrapper != null) {
            total += wrapper.getContentSize();
        } else {
            // 필터가 적용되지 않은 경로(정적 리소스 등). 응답 크기는 알 수 없으므로 요청 크기만 반영.
            log.debug("ContentCachingResponseWrapper 미적용 - 응답 크기 계측 생략");
        }
        return total;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For 가 다중 IP 인 경우 첫 번째만 사용
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}