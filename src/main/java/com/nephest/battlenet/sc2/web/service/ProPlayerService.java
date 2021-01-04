// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.aligulac.AligulacProPlayer;
import com.nephest.battlenet.sc2.model.local.ProPlayer;
import com.nephest.battlenet.sc2.model.local.ProTeam;
import com.nephest.battlenet.sc2.model.local.ProTeamMember;
import com.nephest.battlenet.sc2.model.local.SocialMediaLink;
import com.nephest.battlenet.sc2.model.local.dao.*;
import com.nephest.battlenet.sc2.model.revealed.RevealedProPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class ProPlayerService
{

    private static final Logger LOG = LoggerFactory.getLogger(ProPlayerService.class);

    private final ProPlayerDAO proPlayerDAO;
    private final ProTeamDAO proTeamDAO;
    private final ProTeamMemberDAO proTeamMemberDAO;
    private final SocialMediaLinkDAO socialMediaLinkDAO;
    private final ProPlayerAccountDAO proPlayerAccountDAO;

    private final SC2RevealedAPI sc2RevealedAPI;

    private final AligulacAPI aligulacAPI;

    private int aligulacBatchSize = 100;

    @Autowired
    public ProPlayerService
    (
        ProPlayerDAO proPlayerDAO,
        ProTeamDAO proTeamDAO,
        ProTeamMemberDAO proTeamMemberDAO,
        SocialMediaLinkDAO socialMediaLinkDAO,
        ProPlayerAccountDAO proPlayerAccountDAO,
        SC2RevealedAPI sc2RevealedAPI,
        AligulacAPI aligulacAPI
    )
    {
        this.proPlayerDAO = proPlayerDAO;
        this.proTeamDAO = proTeamDAO;
        this.proTeamMemberDAO = proTeamMemberDAO;
        this.socialMediaLinkDAO = socialMediaLinkDAO;
        this.proPlayerAccountDAO = proPlayerAccountDAO;
        this.sc2RevealedAPI = sc2RevealedAPI;
        this.aligulacAPI = aligulacAPI;
    }

    @CacheEvict(cacheNames={"pro-player-characters"}, allEntries=true)
    @Transactional
    (
        propagation = Propagation.REQUIRES_NEW
    )
    public void update()
    {
        updateRevealed();
        updateAligulac();
        proTeamMemberDAO.removeExpired();
        proTeamDAO.removeExpired();
        proPlayerAccountDAO.removeExpired();
        proPlayerDAO.removeExpired();
        LOG.info("Updated pro players");
    }

    private void updateRevealed()
    {
        ByteBuffer idBuffer = ByteBuffer.allocate(Long.BYTES);
        for(RevealedProPlayer revealedProPlayer : sc2RevealedAPI.getPlayers().block().getPlayers())
        {
            //save only identified players
            if(revealedProPlayer.getFirstName() == null || revealedProPlayer.getFirstName().isEmpty()) continue;

            ProPlayer proPlayer = ProPlayer.of(revealedProPlayer);
            ProTeam proTeam = ProTeam.of(revealedProPlayer);
            SocialMediaLink[] links = SocialMediaLink.of(proPlayer, revealedProPlayer);
            /*
                sc2revealed data treats multi-region players as distinct entities. Using aligulac id as revealed id
                to merge multi-region players in single entity. This still allows multi-region players to exist if
                they do not have an aligulac link.
             */
            if(proPlayer.getAligulacId() != null)
            {
                byte[] id = idBuffer.putLong(proPlayer.getAligulacId()).array();
                proPlayer.setRevealedId(id);
                idBuffer.flip();
            }

            proPlayerDAO.merge(proPlayer);
            if(proTeam != null)
            {
                proTeamDAO.merge(proTeam);
                proTeamMemberDAO.merge(new ProTeamMember(proTeam.getId(), proPlayer.getId()));
            }
            for(SocialMediaLink link : links) link.setProPlayerId(proPlayer.getId());
            socialMediaLinkDAO.merge(links);
            proPlayerAccountDAO.link(proPlayer.getId(), revealedProPlayer.getBnetTags());
            proPlayerAccountDAO.link(proPlayer.getId(), revealedProPlayer.getKnownIds());
        }
        idBuffer.clear();
    }

    private void updateAligulac()
    {
        int ix = 0;
        int total = 0;
        List<ProPlayer> originalProPlayers = proPlayerDAO.findAligulacList();
        long[] ids = new long[getAligulacBatchSize()];
        ProPlayer[] proPlayers = new ProPlayer[getAligulacBatchSize()];
        ArrayList<ProTeamMember> members = new ArrayList<>();

        for(ProPlayer proPlayer : originalProPlayers)
        {
            ids[ix] = proPlayer.getAligulacId();
            proPlayers[ix] = proPlayer;
            ix++; total++;
            if(ix == getAligulacBatchSize() || total == originalProPlayers.size())
            {
                AligulacProPlayer[] aligulacProPlayers = aligulacAPI.getPlayers(Arrays.copyOf(ids, ix)).block().getObjects();
                //aligulac returns players in the same order they were requested
                for(int i = 0; i < aligulacProPlayers.length; i++)
                {
                    ProPlayer.update(proPlayers[i], aligulacProPlayers[i]);
                    ProTeam proTeam = ProTeam.of(aligulacProPlayers[i]);
                    if(proTeam != null)
                    {
                        ProTeamMember member = new ProTeamMember
                        (
                            proTeamDAO.merge(proTeam).getId(),
                            proPlayers[i].getId()
                        );
                        members.add(member);
                    }
                }
                proPlayerDAO.merge(Arrays.copyOf(proPlayers, ix));
                proTeamMemberDAO.merge(members.toArray(new ProTeamMember[0]));
                members.clear();
                ix = 0;
            }
        }
    }

    public int getAligulacBatchSize()
    {
        return aligulacBatchSize;
    }

    protected void setAligulacBatchSize(int aligulacBatchSize)
    {
        if(aligulacBatchSize < 1) throw new IllegalArgumentException("Only positive values allowed");
        this.aligulacBatchSize = aligulacBatchSize;
    }

}
