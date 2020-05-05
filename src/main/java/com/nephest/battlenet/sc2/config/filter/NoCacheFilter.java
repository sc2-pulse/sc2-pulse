package com.nephest.battlenet.sc2.config.filter;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletResponse;

@WebFilter("/api/my/*")
public class NoCacheFilter
implements Filter
{
    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
    throws java.io.IOException, ServletException
    {
        HttpServletResponse hresp = (HttpServletResponse) resp;
        hresp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        chain.doFilter(req, resp);
    }
}
