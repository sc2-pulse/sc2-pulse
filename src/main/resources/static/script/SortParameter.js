class SortParameter {

    static DESC_PREFIX = "-";
    static ASC_PREFIX = "+";

    #field;
    #order;

    constructor(field, order) {
        this.#field = field;
        this.#order = order;
    }

    static fromPrefixedString(str) {
        let field = str;
        let order = SORTING_ORDER.ASC;
        if(str.charAt(0) == SortParameter.DESC_PREFIX) {
            order = SORTING_ORDER.DESC;
            field = str.substring(1, str.length);
        } else if (str.charAt(0) == SortParameter.ASC_PREFIX) {
            field = str.substring(1, str.length);
        }
        return new SortParameter(field, order);
    }

    toPrefixedString() {
        return (this.order == SORTING_ORDER.ASC ? "" : SortParameter.DESC_PREFIX) + this.field;
    }

    get field() {
        return this.#field;
    }

    get order() {
        return this.#order;
    }

}