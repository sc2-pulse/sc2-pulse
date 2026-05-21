/*
    This class should be used to solve prop drilling in context of poorly organized vanilla js code.
    It uses naive ttl based invalidation, so it should only be used in simple cases, like user settings propagation.  
*/
class CachedLocalStorage {

    #cache = new Map();
    #ttlMillis;

    constructor(ttlMillis = 1000) {
        this.ttlMillis = ttlMillis;
    }

    get ttlMillis() {
        return this.#ttlMillis;
    }

    set ttlMillis(ttlMillis) {
        if(!Number.isInteger(ttlMillis)) throw new Error("Integer TTL expected: " + ttlMillis);
        if(ttlMillis < 0) throw new Error("Negative TTL");

        this.#ttlMillis = ttlMillis;
    }

    getItem(key, converter) {
        const cached = this.#cache.get(key);
        if(cached != null) {
            if(cached[0] + this.ttlMillis >= Date.now()) return cached[1];

            this.#cache.delete(key);
        }

        const item = localStorage.getItem(key);
        this.#cache.set(key, [Date.now(), converter != null && item != null ? converter(item) : item]);
        return item;
    }

}