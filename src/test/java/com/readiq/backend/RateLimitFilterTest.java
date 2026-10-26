package com.readiq.backend;
import com.readiq.backend.security.RateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
class RateLimitFilterTest {
    @Test void changingForwardedHeaderCannotBypassUntrustedIpLimit()throws Exception{
        var filter=new RateLimitFilter("");
        for(int i=0;i<11;i++){
            var req=new MockHttpServletRequest("POST","/api/auth/login");req.setRemoteAddr("192.0.2.1");
            req.addHeader("X-Forwarded-For","198.51.100."+i);var res=new MockHttpServletResponse();
            filter.doFilter(req,res,(q,r)->r.getWriter().write("allowed"));
            assertEquals(i<10?200:429,res.getStatus());
        }
    }
}
