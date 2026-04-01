class TeamLegacyId {

    static #SEPARATOR = "~";

    #entries;
    #pulseString;

    constructor(entries) {
        this.#entries = entries;
    }

    static fromPulseString(pulseString) {
        return new TeamLegacyId(pulseString.split(TeamLegacyId.#SEPARATOR).map(TeamLegacyIdEntry.fromPulseString));
    }

    get entries() {
        return this.#entries;
    }

    toPulseString() {
        if(this.#pulseString == null)
            this.#pulseString = this.#entries.map(e=>e.toPulseString()).join(TeamLegacyId.#SEPARATOR);

        return this.#pulseString;
    }

}
