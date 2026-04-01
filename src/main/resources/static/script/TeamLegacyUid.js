class TeamLegacyUid {

    static #SEPARATOR = "-";

    #queue;
    #teamType;
    #region;
    #legacyId;
    #pulseString;

    constructor(queue, teamType, region, legacyId) {
        this.#queue = queue;
        this.#teamType = teamType;
        this.#region = region;
        this.#legacyId = legacyId;
    }

    static fromPulseString(pulseString) {
        const split = pulseString.split(TeamLegacyUid.#SEPARATOR);
        if(split.length != 4) throw new Error("Invalid pulse teamLegacyUid: " + pulseString);

        return new TeamLegacyUid(
            EnumUtil.enumOfId(parseInt(split[0]), TEAM_FORMAT),
            EnumUtil.enumOfId(parseInt(split[1]), TEAM_TYPE),
            EnumUtil.enumOfId(parseInt(split[2]), REGION),
            TeamLegacyId.fromPulseString(split[3])
        );
    }

    get queue() {
        return this.#queue;
    }

    get teamType() {
        return this.#teamType;
    }

    get region() {
        return this.#region;
    }

    get legacyId() {
        return this.#legacyId;
    }

    toPulseString() {
        if(this.#pulseString == null) this.#pulseString = this.#queue.code
            + TeamLegacyUid.#SEPARATOR + this.#teamType.code
            + TeamLegacyUid.#SEPARATOR + this.#region.code
            + TeamLegacyUid.#SEPARATOR + this.#legacyId.toPulseString();

        return this.#pulseString;
    }

}