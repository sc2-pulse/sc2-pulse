class TeamLegacyIdEntry {

    static #SEPARATOR = ".";

    #realm;
    #id;
    #race;
    #pulseString;

    constructor(realm, id, race) {
        this.#realm = realm;
        this.#id = id;
        this.#race = race;
    }

    static fromPulseString(pulseString) {
        const split = pulseString.split(TeamLegacyIdEntry.#SEPARATOR);
        if(split.length != 3) throw new Error("Invalid pulse teamLegacyUidEntry: " + pulseString);

        return new TeamLegacyIdEntry(
            parseInt(split[0]),
            parseInt(split[1]),
            split[2] !== '' ? EnumUtil.enumOfId(parseInt(split[2]), RACE) : null
        );
    }

    get realm() {
        return this.#realm;
    }

    get id() {
        return this.#id;
    }

    get race() {
        return this.#race;
    }

    toPulseString() {
        if(this.#pulseString == null) this.#pulseString = this.#realm
            + TeamLegacyIdEntry.#SEPARATOR + this.#id
            + TeamLegacyIdEntry.#SEPARATOR + (this.#race?.code || "");

        return this.#pulseString;
    }

}