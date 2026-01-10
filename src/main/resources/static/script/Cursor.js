class Cursor {

    #token;
    #direction;

    constructor(token, direction) {
        this.#token = token;
        this.#direction = direction;
    }

    static fromUrlSearchParams(params) {
        for(const direction of Object.values(NAVIGATION_DIRECTION)) {
            const token = params.get(direction.relativePosition);
            if(token != null) return new Cursor(token, direction);
        }
        return null;
    }

    static fromElementAttributes(element, prefix = "") {
        for(const direction of Object.values(NAVIGATION_DIRECTION)) {
            const token = element.getAttribute(prefix + direction.relativePosition);
            if(token != null) return new Cursor(token, direction);
        }
        return null;
    }

    get token() {
        return this.#token;
    }

    get direction() {
        return this.#direction;
    }

}
