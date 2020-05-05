package com.nephest.battlenet.sc2.config.security;

import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig
extends WebSecurityConfigurerAdapter
{

    @Autowired
    private AccountDAO accountDAO;

    @Autowired
    private BlizzardOidcRememberMeHandler blizzardOidcRememberMeHandler;

    @Override
    public void configure(HttpSecurity http)
    throws Exception
    {
        http
            .exceptionHandling()
                .defaultAuthenticationEntryPointFor
                (
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    new AntPathRequestMatcher("/api/my/**")
                )
            .and().authorizeRequests()
                .antMatchers("/api/my/**").authenticated()
            .and().logout()
                .logoutSuccessUrl("/#generator")
                .deleteCookies(BlizzardOidcRememberMeHandler.COOKIE_NAME)
            .and().oauth2Login()
                .successHandler(blizzardOidcRememberMeHandler)
                .userInfoEndpoint().oidcUserService(new BlizzardOidcUserService(accountDAO));
    }

}
