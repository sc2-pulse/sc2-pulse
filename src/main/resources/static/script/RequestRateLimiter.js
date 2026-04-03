class RequestRateLimiter {
    #channel;
    #tabId;
    #isLeader = false;
    #tokenCount;
    #bucketCapacity;
    #refillRate;
    #refillInterval;
    #isLeaky;
    #lastRefillTimestamp;
    #leaderQueue = [];
    #localQueue = new Map();
    #requestId = 0;
    #refillTimer = null;

    constructor({
        channelName = 'request-rate-limiter',
        bucketCapacity = 12,
        refillRate = 4,
        refillInterval = 1000,
        isLeaky = true
    } = {}) {
        this.#isLeaky = isLeaky;
        this.#normalizeAndSetBucketParameters({refillRate, refillInterval, bucketCapacity});
        this.#tokenCount = bucketCapacity;
        this.#tabId = crypto.randomUUID();
        this.#channel = new BroadcastChannel(channelName);
        this.#channel.onmessage = ({ data }) => this.#handleMessage(data);

        this.#electLeader(channelName);
    }

    fetch(url, options) {
        return this.#acquireToken()
            .then(e=>fetch(url, options))
            .then(response=>{
                const bucketParameters = this.#parseHeaders(response.headers);
                if(bucketParameters != null) {
                    if(this.#normalizeAndSetBucketParameters(bucketParameters))
                        this.#channel.postMessage({
                            type: 'BUCKET_PARAMETERS',
                            bucketParameters: bucketParameters,
                        });
                }
                return response;
            });
    }

    destroy() {
        this.#channel.close();
        clearInterval(this.#refillTimer);
    }

    #resetRefillTimer()
    {
        clearInterval(this.#refillTimer);
        this.#refillTimer = setInterval(() => this.#refill(), this.#refillInterval);
    }

    #electLeader(channelName) {
        const lockName = `${channelName}-leader`;
        navigator.locks.request(
            lockName,
            { mode: 'exclusive' },
            async () => {
                this.#isLeader = true;
                this.#lastRefillTimestamp = Date.now();
                this.#resetRefillTimer();
                this.#localQueue.entries().forEach(([id, res])=>this.#leaderQueue.push({id: id, tabId: this.#tabId, resolve: res}));
                this.#channel.postMessage({ type: 'LEADER_READY' });
                // Hold lock until tab closes
                await new Promise(() => {});
            }
        );
        /*
            If not a leader, then remove tokens and attempt to sync other parameters with the leader.
            This will be useful in case this tab will become a leader in the future.
        */
        navigator.locks.request(
            lockName,
            { mode: 'exclusive', ifAvailable: true },
            async (lock) => {
                if(this.#isLeader) return;

                this.#channel.postMessage({ type: 'LEADER_DISCOVER' });
                this.#tokenCount = 0;
            }
        );
    }

    #refill() {
        const now = Date.now();
        const intervalsElapsed = (now - this.#lastRefillTimestamp) / this.#refillInterval;
        const added = intervalsElapsed * this.#refillRate;
        if (added >= 1) {
            this.#tokenCount = Math.min(this.#bucketCapacity, this.#tokenCount + added);
            this.#lastRefillTimestamp = now;
            this.#drain();
        }
    }

    #drain() {
        while (this.#leaderQueue.length > 0 && this.#tokenCount >= 1) {
            this.#tokenCount--;
            const item = this.#leaderQueue.shift();
            // Leader-local leaderQueue items have a direct resolve callback
            if (item.resolve != null) {
                item.resolve();
            } else {
                this.#channel.postMessage({ type: 'GO', id: item.id, tabId: item.tabId });
            }
        }
    }

    #parseHeaders(headers) {
        const limitHeader = headers.get('ratelimit-limit');
        const burstHeader = headers.get('ratelimit-burst');
        const resetHeader = headers.get('ratelimit-reset');
        if(limitHeader == null || burstHeader == null || resetHeader == null) return null;

        const limit = parseInt(limitHeader);
        if (isNaN(limit) || limit < 1) return null;

        const burst = parseInt(burstHeader);
        if (isNaN(burst) || burst < 1) return null;

        const reset = parseFloat(resetHeader);
        if (isNaN(reset) || reset <= 0) return null;
        const resetMs = reset * 1000;

        return {refillRate: limit, bucketCapacity: burst, refillInterval: resetMs};
    }

    static #normalizeBucketParameters(bucketParameters) {
        bucketParameters.refillInterval = bucketParameters.refillInterval / bucketParameters.refillRate;
        bucketParameters.refillRate = 1;
    }

    #setBucketParameters(bucketParameters) {
        if(bucketParameters == null) return false;
        const changed = this.#refillRate != bucketParameters.refillRate
            || this.#bucketCapacity != bucketParameters.bucketCapacity
            || this.#refillInterval != bucketParameters.refillInterval;
        if(!changed) return false;


        this.#refillRate = bucketParameters.refillRate;
        this.#bucketCapacity = bucketParameters.bucketCapacity;
        if(this.#refillInterval != bucketParameters.refillInterval) {
            this.#refillInterval = bucketParameters.refillInterval;
            if(this.#isLeader) this.#resetRefillTimer();
        }
        this.#tokenCount = Math.min(this.#tokenCount, this.#bucketCapacity);
        return changed;
    }

    #normalizeAndSetBucketParameters(bucketParameters) {
        if(this.#isLeaky) RequestRateLimiter.#normalizeBucketParameters(bucketParameters);
        return this.#setBucketParameters(bucketParameters);
    }

    #acquireToken() {
        if (this.#isLeader) {
            if (this.#tokenCount >= 1) {
                this.#tokenCount--;
                return Promise.resolve();
            }
            return new Promise(resolve => this.#leaderQueue.push({ id: this.#requestId++, tabId: this.#tabId, resolve: resolve}));
        }

        return new Promise((resolve) => {
            const id = this.#requestId++;
            this.#localQueue.set(id, resolve);
            this.#channel.postMessage({ type: 'ACQUIRE', id: id, tabId: this.#tabId });
        });
    }

    #handleAcquireMessage(data) {
        if (!this.#isLeader) return;
        if (data.reAcquire == true
            && this.#leaderQueue.some(queueItem=>queueItem.tabId == data.tabId && queueItem.id == data.id)) return;

        if (this.#tokenCount >= 1) {
            this.#tokenCount--;
            this.#channel.postMessage({ type: 'GO', id: data.id, tabId: data.tabId });
        } else {
            this.#leaderQueue.push({ id: data.id, tabId: data.tabId });
        }
    }

    #handleGoMessage(data) {
        if (data.tabId !== this.#tabId) return;

        this.#localQueue.get(data.id)?.();
        this.#localQueue.delete(data.id);
    }

    #handleLeaderDiscoverMessage(data) {
        if(!this.#isLeader) return;

        this.#channel.postMessage({
            type: 'LEADER_BROADCAST',
            tabId: this.#tabId,
            bucketParameters: {
                refillRate: this.#refillRate,
                refillInterval: this.#refillInterval,
                bucketCapacity: this.#bucketCapacity
            }
        });
    }

    #handleLeaderReadyMessage(data) {
        for (const [id] of this.#localQueue)
            this.#channel.postMessage({ type: 'ACQUIRE', id: id, tabId: this.#tabId, reAcquire: true });
    }

    #handleMessage(data) {
        switch (data.type) {

            // Leader receives token request from a remote tab
            case 'ACQUIRE': {
                this.#handleAcquireMessage(data);
                break;
            }

            // Tab receives parameter sync(from response headers) from a remote tab
            case 'BUCKET_PARAMETERS': {
                this.#setBucketParameters(data.bucketParameters);
                break;
            }

            // Remote tab receives GO from leader
            case 'GO': {
                this.#handleGoMessage(data);
                break;
            }

            // Leader receives discover request from remote tab
            case 'LEADER_DISCOVER': {
                this.#handleLeaderDiscoverMessage(data);
                break;
            }

            // Remote tab receives leader broadcast
            case 'LEADER_BROADCAST': {
                if(!this.#isLeader) this.#setBucketParameters(data.bucketParameters);
                break;
            }

            // New leader elected
            case 'LEADER_READY': {
                this.#handleLeaderReadyMessage(data);
                break;
            }
        }
    }
}