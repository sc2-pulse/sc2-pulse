package com.nephest.battlenet.sc2.service;

import com.nephest.battlenet.sc2.model.local.AccountFollowing;
import com.nephest.battlenet.sc2.model.local.dao.AccountFollowingDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AccountFollowingService
{

    private int followingMax = 75;

    private final AccountFollowingDAO accountFollowerDAO;

    @Autowired
    public AccountFollowingService(AccountFollowingDAO accountFollowerDAO)
    {
        this.accountFollowerDAO = accountFollowerDAO;
    }

    public int getFollowingMax()
    {
        return followingMax;
    }

    protected void setFollowingMax(int followingMax)
    {
        this.followingMax = followingMax;
    }

    public boolean canFollow(long accountId)
    {
        return getFollowingCount(accountId) < getFollowingMax();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean follow(long accountId, long followingAccountId)
    {
        if(!canFollow(accountId)) return false;

        accountFollowerDAO.create(new AccountFollowing(accountId, followingAccountId));
        return true;
    }

    public void unfollow(long accountId, long followingAccountId)
    {
        accountFollowerDAO.delete(accountId, followingAccountId);
    }

    public int getFollowingCount(long accountId)
    {
        return accountFollowerDAO.getFollowingCount(accountId);
    }

    public List<AccountFollowing> getAccountFollowingList(long accountId)
    {
        return accountFollowerDAO.findAccountFollowingList(accountId);
    }

}
