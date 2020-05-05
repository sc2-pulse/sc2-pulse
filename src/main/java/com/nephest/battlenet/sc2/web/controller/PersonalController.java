package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.config.security.BlizzardOidcUser;
import com.nephest.battlenet.sc2.model.local.Account;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/my")
public class PersonalController
{

    @GetMapping("/account")
    public Account getBattlenetAccountIdentity(@AuthenticationPrincipal BlizzardOidcUser user)
    {
        return user.getAccount();
    }

}
