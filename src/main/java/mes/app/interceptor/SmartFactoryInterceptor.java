package mes.app.interceptor;

import mes.domain.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@Component
public class SmartFactoryInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(SmartFactoryInterceptor.class);

    @Autowired
    SmartFactoryLogService logService;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {

        // 1. 요청 정보 추출
        String uri = request.getRequestURI();
        String method = request.getMethod().toUpperCase();
        int status = response.getStatus();

        if (uri.contains("/login") && !"POST".equals(method)) {
            return;
        }

        // 2. 접속 구분(useSe) 판별 로직
        String useSe = "DO6003"; // 기본값: 조회

        // [최우선순위] 명시적 입출고 API 패턴 (재고 변동 발생)
        if ("POST".equals(method) && (
                uri.equals("/api/inventory/material_inout/save") ||
                        uri.equals("/api/inventory/material_inout/save_balju") ||
                        uri.equals("/api/production/prod_result/chasu_del") ||
                        uri.equals("/api/production/prod_result/chasu_add") ||
                        uri.equals("/api/shipment/shipment_do_a/batch_save")
        )) {
            useSe = "DO6007"; // 입출고
        }

        // [2순위] 일반 행위 기반 판별
        else if (uri.contains("/login")) {
            useSe = "DO6001";
        } else if (uri.contains("/logout")) {
            useSe = "DO6002";
        } else if (uri.contains("/delete") || uri.contains("/remove")) {
            useSe = "DO6006"; // 삭제
        } else if (uri.contains("/update") || uri.contains("/modify") || uri.contains("/edit")) {
            useSe = "DO6005"; // 수정
        } else if (uri.contains("/insert") || uri.contains("/save") || uri.contains("/add")) {
            useSe = "DO6004"; // 등록
        } else {
            // [3순위] 키워드가 없을 경우 HTTP Method로 추측
            switch (method) {
                case "POST":   useSe = "DO6004"; break;
                case "DELETE": useSe = "DO6006"; break;
                case "PUT":
                case "PATCH":  useSe = "DO6005"; break;
                default:       useSe = "DO6003"; break;
            }
        }

        // 3. 사용자 및 IP 정보 (IPv6 처리 포함)
        String ip = getClientIp(request);
        if ("0:0:0:0:0:0:0:1".equals(ip)) {
            ip = "127.0.0.1";
        }

        String userId = "system"; // 기본값 설정 (로그인이 안 된 상태일 경우 대비)

        // SecurityContextHolder에서 인증 정보 확인
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            Object principal = auth.getPrincipal();

            if (principal instanceof User) { // User 객체는 본인의 프로젝트 User 도메인 클래스
                userId = ((User) principal).getUsername();
            } else if (principal instanceof String) {
                userId = (String) principal;
            }
        } else {
            // 만약 SecurityContext가 비어있다면 세션에서 직접 확인 (예비용)
            HttpSession session = request.getSession(false);
            if (session != null) {
                // 세션에 저장한 키값이 "userId"라고 가정 (프로젝트 설정에 따라 다를 수 있음)
                Object sessionUserId = session.getAttribute("username");
                if (sessionUserId != null) {
                    userId = sessionUserId.toString();
                }
            }
        }

        boolean isSuccess = (status >= 200 && status < 300);
        boolean isRedirectSuccess = (status == 302 && (useSe.equals("DO6001") || useSe.equals("DO6002")));

        if (isSuccess || isRedirectSuccess) {
             logService.sendLog(useSe, userId, ip);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}