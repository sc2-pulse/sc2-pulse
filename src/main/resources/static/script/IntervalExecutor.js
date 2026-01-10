// Copyright (C) 2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

class IntervalExecutor
{

    constructor(runnable, shouldRun, interval, maxFails)
    {
        this.runnable = runnable;
        this.shouldRun = shouldRun;
        this.interval = interval;
        this.maxFails = maxFails;
        this.failCount = 0;
    }
    
    execute()
    {
        if(this.shouldRun() === true)
        {
            this.runnable();
        } else {
            this.failCount += 1;
        }
        if(this.failCount >= this.maxFails) this.stop();
    }

    stop()
    {
        if(this.intervalId) {
            window.clearInterval(this.intervalId);
            this.intervalId = null;
            this.failCount = 0;
        }
    }

    schedule()
    {
        this.intervalId = window.setInterval(this.execute.bind(this), this.interval);
    }

    executeAndReschedule()
    {
        this.stop();
        this.execute();
        this.schedule();
    }
    
}
