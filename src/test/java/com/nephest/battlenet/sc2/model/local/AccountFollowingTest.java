package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

public class AccountFollowingTest
{

    @Test
    public void testUniqueness()
    {
        AccountFollowing following = new AccountFollowing(1L, 1L);
        AccountFollowing equalFollowing = new AccountFollowing(1L, 1L);
        AccountFollowing[] notEqualFollowings = new AccountFollowing[]
        {
            new AccountFollowing(0L, 1L),
            new AccountFollowing(1L, 0L),
            new AccountFollowing(0L, 0L)
        };
        TestUtil.testUniqueness(following, equalFollowing, notEqualFollowings);
    }

}
