package com.codewild.food.shared.observability;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

public class RequestIdFilter implements Filter {
  public static final String REQUEST_ID_MDC = "requestId";
  public static final String HEADER = "X-Request-Id";

  @Override
  public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest hreq = (HttpServletRequest) req;
    HttpServletResponse hres = (HttpServletResponse) res;
    String id = hreq.getHeader(HEADER);
    if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
    MDC.put(REQUEST_ID_MDC, id);
    hres.setHeader(HEADER, id);
    try { chain.doFilter(req, res); } finally { MDC.remove(REQUEST_ID_MDC); }
  }
}
