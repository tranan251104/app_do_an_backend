package vn.anpay.backend.common.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String AUTHENTICATED_USER_ATTRIBUTE =
            RequestLoggingFilter.class.getName() + ".authenticatedUser";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = requestId(request);
        String previousTraceId = MDC.get("traceId");
        long startNanos = System.nanoTime();
        Throwable failure = null;

        MDC.put("traceId", requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            failure = exception;
            throw exception;
        } finally {
            long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            int status = failure != null && response.getStatus() < 500
                    ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                    : response.getStatus();
            Object authenticatedUser = request.getAttribute(AUTHENTICATED_USER_ATTRIBUTE);
            String user = authenticatedUser == null ? "anonymous" : authenticatedUser.toString();

            if (status >= 500) {
                if (failure == null) {
                    log.error("{} {} -> {} ({} ms) requestId={} user={}",
                            request.getMethod(), request.getRequestURI(), status,
                            durationMillis, requestId, user);
                } else {
                    log.error("{} {} -> {} ({} ms) requestId={} user={} exception={}",
                            request.getMethod(), request.getRequestURI(), status,
                            durationMillis, requestId, user, failure.getClass().getSimpleName());
                }
            } else if (status >= 400) {
                log.warn("{} {} -> {} ({} ms) requestId={} user={}",
                        request.getMethod(), request.getRequestURI(), status,
                        durationMillis, requestId, user);
            } else {
                log.info("{} {} -> {} ({} ms) requestId={} user={}",
                        request.getMethod(), request.getRequestURI(), status,
                        durationMillis, requestId, user);
            }

            if (previousTraceId == null) {
                MDC.remove("traceId");
            } else {
                MDC.put("traceId", previousTraceId);
            }
        }
    }

    private String requestId(HttpServletRequest request) {
        String supplied = request.getHeader(REQUEST_ID_HEADER);
        if (supplied != null && SAFE_REQUEST_ID.matcher(supplied).matches()) {
            return supplied;
        }
        return UUID.randomUUID().toString();
    }
}
