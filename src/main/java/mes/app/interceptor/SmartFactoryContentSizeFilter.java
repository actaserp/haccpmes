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

/**
 * 응답 본문 크기를 측정하기 위해 응답을 ContentCachingResponseWrapper 로 감싼다.
 * afterCompletion 시점에는 response.getContentLength() 가 -1 인 경우가 많아
 * 래퍼의 getContentSize() 를 사용해야 실제 바이트 수를 얻을 수 있다.
 *
 * 주의: copyBodyToResponse() 를 반드시 호출해야 실제 클라이언트로 본문이 전달된다.
 */
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

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }
}