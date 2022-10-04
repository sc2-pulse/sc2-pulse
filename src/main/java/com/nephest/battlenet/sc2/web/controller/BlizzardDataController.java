// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.config.security.BlizzardOidcUser;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardFullPlayerCharacter;
import com.nephest.battlenet.sc2.web.service.BlizzardDataService;
import com.nephest.battlenet.sc2.web.service.PersonalService;
import io.swagger.v3.oas.annotations.Hidden;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.SessionAttributes;

@Controller @Hidden
@RequestMapping("/data")
@SessionAttributes({"blizzardOidcUser, blizzardCharacters"})
public class BlizzardDataController
{

    public enum Action
    {

        IMPORT(Duration.ofMinutes(30)),
        DELETE(Duration.ofMinutes(90));

        private final Duration duration;

        Action(Duration duration)
        {
            this.duration = duration;
        }

        public Duration getDuration()
        {
            return duration;
        }

    }

    @Autowired
    private PersonalService personalService;

    @Autowired
    private BlizzardDataService blizzardDataService;

    @Autowired @Qualifier("dbExecutorService")
    private ExecutorService dbExecutorService;

    private Future<?> lastAction;

    @ModelAttribute
    public void loadBattleNetData(Model model)
    {
        model.addAttribute("blizzardOidcUser", personalService.getOidcUser().orElseThrow());
        model.addAttribute("blizzardCharacters", personalService.getCharacters());
    }

    @GetMapping("/battle-net")
    public String displayBattleNetData(Model model)
    {
        return "battle-net-data-manager";
    }

    @PostMapping("/battle-net")
    public String doBattleNetAction
    (
        @ModelAttribute("blizzardOidcUser") BlizzardOidcUser user,
        @ModelAttribute("blizzardCharacters") List<BlizzardFullPlayerCharacter> characters,
        @ModelAttribute("action") Action action,
        Model model
    )
    {
        switch (action)
        {
            case IMPORT:
                lastAction = dbExecutorService
                    .submit(()->blizzardDataService.importData(user.getAccount(), characters));
                break;
            case DELETE:
                lastAction = dbExecutorService
                    .submit(()->blizzardDataService.removeData(user.getAccount(), characters));
                break;
            default:
                throw new UnsupportedOperationException("Unsupported action: " + action);
        }
        model.addAttribute("actionDuration", action.getDuration());
        return "action-queue";
    }

    /**
     * Block until last action is complete. Useful in tests.
     *
     * @throws ExecutionException when exception is thrown in task
     * @throws InterruptedException when blocked thread is interrupted
     */
    public void waitForLastAction()
    throws ExecutionException, InterruptedException
    {
        if(lastAction != null) lastAction.get();
    }

}
