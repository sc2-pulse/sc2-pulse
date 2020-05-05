package com.nephest.battlenet.sc2.config.security;

import com.nephest.battlenet.sc2.model.local.Account;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collection;
import java.util.Map;

public class BlizzardOidcUser
implements OidcUser
{

    private final OidcUser user;
    private final Account account;

    public BlizzardOidcUser(OidcUser user, Account account)
    {
        this.user = user;
        this.account = account;
    }

    @Override
    public Map<String, Object> getClaims()
    {
        return user.getClaims();
    }

    @Override
    public OidcUserInfo getUserInfo()
    {
        return user.getUserInfo();
    }

    @Override
    public OidcIdToken getIdToken()
    {
        return user.getIdToken();
    }

    @Override
    public Map<String, Object> getAttributes()
    {
        return user.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities()
    {
        return user.getAuthorities();
    }

    @Override
    public String getName()
    {
        return user.getName();
    }

    public Account getAccount()
    {
        return account;
    }

}
