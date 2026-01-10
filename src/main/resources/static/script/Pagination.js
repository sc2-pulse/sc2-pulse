// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

class Pagination
{

    constructor(cssSelector, config, onClick)
    {
        this.cssSelector = cssSelector;
        this.config = config;
        this.onClick = onClick;
    }

    update(data)
    {
        this.data = data;
        if(data.empty) {
            this.disableNext(data.meta.pageDiff);
            return;
        }
        if(data.result == null || data.result.length < 1) {
            for(const pagination of document.querySelectorAll(this.cssSelector)) pagination.classList.add("d-none");
            return;
        }
        const params = {};
        for(const direction of Object.values(NAVIGATION_DIRECTION)) {
            const directionParams = new Map();
            params[direction.name] = directionParams;
            const value = data.navigation[direction.relativePosition];
            if(value != null) directionParams.set(direction.relativePosition, value);
        }
        for(const pagination of document.querySelectorAll(this.cssSelector))
        {
            PaginationUtil.updatePagination(pagination, params, data.meta.page, data.meta.isLastPage, this.onClick);
            pagination.classList.remove("d-none");
        }
    }

    disableNext(minCount = 1)
    {
        for(const link of document.querySelectorAll(this.cssSelector + " [data-page-count]"))
        {
            if(link.getAttribute("data-page-count") >= minCount && link.getAttribute("data-page-number") != 0)
                link.parentNode.classList.add("disabled");
        }
    }

}