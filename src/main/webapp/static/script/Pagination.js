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
        const backwardParams = this.createParams(data.result[0]);
        const forwardParams = this.createParams(data.result[data.result.length - 1]);
        const firstParams = this.createMaxParams();
        const lastParams = this.createMinParams();
        const params = {first: firstParams, last: lastParams, forward: forwardParams, backward: backwardParams};
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

    createParams(obj)
    {
        const result = new Map();
        for(const param of this.config) result.set(param.name, param.getter(obj));
        return result;
    }

    createMinParams()
    {
        const result = new Map();
        for(const param of this.config) result.set(param.name, param.min);
        return result;
    }

    createMaxParams()
    {
        const result = new Map();
        for(const param of this.config) result.set(param.name, param.max);
        return result;
    }

}