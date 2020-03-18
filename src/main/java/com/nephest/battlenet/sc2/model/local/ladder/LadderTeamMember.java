/*-
 * =========================LICENSE_START=========================
 * SC2 Ladder Generator
 * %%
 * Copyright (C) 2020 Oleksandr Masniuk
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END=========================
 */
package com.nephest.battlenet.sc2.model.local.ladder;

import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nephest.battlenet.sc2.model.BaseAccount;
import com.nephest.battlenet.sc2.model.BasePlayerCharacter;
import com.nephest.battlenet.sc2.model.local.BaseLocalTeamMember;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class LadderTeamMember
extends BaseLocalTeamMember
implements java.io.Serializable
{

    private static final long serialVersionUID = 1l;

    @NotNull
    private Long battlenetId;

    @NotNull
    private BasePlayerCharacter character;

    @NotNull
    private BaseAccount account;

    public LadderTeamMember
    (
        Long battlenetId,
        Integer realm,
        String battleTag,
        String characterName,
        Integer terranGamesPlayed,
        Integer protossGamesPlayed,
        Integer zergGamesPlayed,
        Integer randomGamesPlayed
    )
    {
        super(terranGamesPlayed, protossGamesPlayed, zergGamesPlayed, randomGamesPlayed);
        this.battlenetId = battlenetId;
        this.character = new BasePlayerCharacter(realm, characterName);
        this.account = new BaseAccount(battleTag);
    }

    public Long getBattlenetId()
    {
        return battlenetId;
    }

    public BasePlayerCharacter getCharacter()
    {
        return character;
    }

    public BaseAccount getAccount()
    {
        return account;
    }

}
