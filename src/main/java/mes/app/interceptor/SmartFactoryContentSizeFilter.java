package mes.app.interceptor;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SmartFactoryContentSizeFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(request, wrapper);
        } finally {
            wrapper.copyBodyToResponse();
        }
    }

    /**
     * ★ SSE(스트리밍) 응답은 래핑하면 안 된다.
     *   ContentCachingResponseWrapper 가 본문을 버퍼에 모았다가
     *   copyBodyToResponse() 에서 Content-Length 를 붙이는 순간
     *   브라우저가 응답 완료로 판단해 연결을 끊는다.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/sse/")) {
            return true;
        }
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("text/event-stream");
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return true;   // ★ false → true
    }
}