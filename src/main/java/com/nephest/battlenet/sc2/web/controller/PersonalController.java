package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.config.security.BlizzardOidcUser;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderSearchDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/my")
public class PersonalController
{

    @Autowired
    private LadderSearchDAO ladderSearchDAO;

    @GetMapping("/account")
    public Account getAccount(@AuthenticationPrincipal BlizzardOidcUser user)
    {
        return user.getAccount();
    }

    @GetMapping("/characters")
    public List<LadderDistinctCharacter> getCharacters(@AuthenticationPrincipal BlizzardOidcUser user)
    {
        return ladderSearchDAO.findDistinctCharactersByAccountId(user.getAccount().getId());
    }

}
