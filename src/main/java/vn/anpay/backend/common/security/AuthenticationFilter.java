package vn.anpay.backend.common.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.anpay.backend.common.api.RequestLoggingFilter;
import java.io.IOException;
import java.util.*;

@Component
public class AuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(AuthenticationFilter.class);
    private final JwtService jwt;
    public AuthenticationFilter(JwtService jwt) {
        this.jwt=jwt;

    }
    protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException {
        String h=req.getHeader("Authorization");
        if(h!=null&&h.startsWith("Bearer ")) try {
            var c=jwt.verify(h.substring(7));
            if("access".equals(c.getStringClaim("token_type"))) {
                var p=new CurrentUser(UUID.fromString(c.getSubject()));
                var a=new UsernamePasswordAuthenticationToken(p,null,List.of(new SimpleGrantedAuthority("ROLE_USER")));
                SecurityContextHolder.getContext().setAuthentication(a);
                req.setAttribute(RequestLoggingFilter.AUTHENTICATED_USER_ATTRIBUTE, c.getSubject());

            }

        } catch(Exception exception) {
            log.debug("JWT authentication rejected for {} {} reason={}",
                    req.getMethod(), req.getRequestURI(), exception.getClass().getSimpleName());

        }
        chain.doFilter(req,res);

    }

}
