package com.readiq.backend.security;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.*;
/** Bounded fixed-window buckets; use ingress/distributed limits when running multiple replicas. */
public class RateLimitFilter extends OncePerRequestFilter {
    private record Window(long expires,int used){}
    private final Map<String,Window>windows=new HashMap<>();
    private final Set<String>trusted;
    public RateLimitFilter(String proxies){trusted=new HashSet<>(Arrays.stream(proxies.split(",")).map(String::trim).toList());}
    private synchronized boolean consume(String key,int max,long millis){
        long now=System.currentTimeMillis();
        if(windows.size()>10000)windows.entrySet().removeIf(e->e.getValue().expires<now);
        Window w=windows.get(key);
        if(w==null||w.expires<now){if(windows.size()>=50000)return false;windows.put(key,new Window(now+millis,1));return true;}
        if(w.used>=max)return false;windows.put(key,new Window(w.expires,w.used+1));return true;
    }
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws IOException,ServletException{
        if("OPTIONS".equals(req.getMethod())){chain.doFilter(req,res);return;}
        String path=req.getRequestURI(),ip=req.getRemoteAddr();
        if(trusted.contains(ip)){
            String forwarded=req.getHeader("X-Forwarded-For");
            if(forwarded!=null){String[]hops=forwarded.split(",");for(int i=hops.length-1;i>=0;i--){
                String candidate=hops[i].trim();if(!candidate.matches("[0-9a-fA-F:.]{2,64}"))break;ip=candidate;if(!trusted.contains(candidate))break;
            }}
        }
        boolean strict=Set.of("/api/auth/login","/api/auth/register","/api/auth/google","/api/auth/forgot-password").contains(path);
        String group=strict?"auth":path.equals("/api/upload")?"upload":"api";int max=strict?10:group.equals("upload")?20:1000;
        if(!consume(group+":"+ip,max,group.equals("upload")?60000:900000)){
            res.setStatus(429);res.setHeader("Retry-After",group.equals("upload")?"60":"900");res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"Too many requests. Please try again later.\"}");return;
        }
        chain.doFilter(req,res);
    }
}
