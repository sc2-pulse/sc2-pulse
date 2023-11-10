luxon.Duration.prototype.toLargestUnitString = function(unitNameLen = 3) {
    const shifted = this.shiftTo("years", "month", "days", "hours", "minutes", "seconds", "milliseconds").toObject();
    const largest = Object.entries(shifted).find(e=>e[1] > 0);

    return largest ? (largest[1] + " " + largest[0].substring(0, Math.min(unitNameLen, largest[0].length))) : "recent";
}