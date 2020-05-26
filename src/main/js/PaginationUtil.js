// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class PaginationUtil
{

    static setPaginationsState(enabled)
    {
        if(enabled)
        {
            PaginationUtil.updateLadderPaginations();
        }
        else
        {
            for(const pagination of document.getElementsByClassName("pagination-ladder"))
            {
                for(const page of pagination.getElementsByClassName("page-link"))
                {
                    page.parentElement.classList.remove("enabled");
                    page.parentElement.classList.add("disabled");
                    page.removeEventListener("click", LadderUtil.ladderPaginationPageClick);
                }
            }
        }
    }

    static updateLadderPaginations()
    {
        if(Session.currentLadder == null || Session.currentLadder.result.length < 1) return;
        const backwardParams = new Map();
        backwardParams.set("rating-anchor", Session.currentLadder.result[0].rating);
        backwardParams.set("id-anchor", Session.currentLadder.result[0].id);
        const forwardParams = new Map();
        forwardParams.set("rating-anchor", Session.currentLadder.result[Session.currentLadder.result.length - 1].rating);
        forwardParams.set("id-anchor", Session.currentLadder.result[Session.currentLadder.result.length - 1].id);
        const firstParams = new Map();
        firstParams.set("rating-anchor", 99999);
        firstParams.set("id-anchor", 1);
        const lastParams = new Map();
        lastParams.set("rating-anchor", 0);
        lastParams.set("id-anchor", 0);
        const params = {first: firstParams, last: lastParams, forward: forwardParams, backward: backwardParams};
        for(const pagination of document.getElementsByClassName("pagination-ladder"))
        {
            PaginationUtil.updatePagination(pagination, params, Session.currentLadder.meta.page, Session.currentLadder.meta.pageCount);
        }
    }

    static updatePagination(pagination, params, currentPage, lastPage)
    {
        const pages = pagination.getElementsByClassName("page-link");
        PaginationUtil.updatePaginationPage(pages.item(0), params, PAGE_TYPE.FIRST, true, 1, 0, "First", currentPage != 1, false);
        PaginationUtil.updatePaginationPage(pages.item(1), params, PAGE_TYPE.GENERAL, false, 1, currentPage, "<", currentPage - 1 >= 1, false);
        PaginationUtil.updatePaginationPage(pages.item(pages.length - 1), params, PAGE_TYPE.LAST, false, 1, +lastPage + 1, "Last", currentPage != lastPage, false);
        PaginationUtil.updatePaginationPage(pages.item(pages.length - 2), params, PAGE_TYPE.GENERAL, true, 1, currentPage, ">", +currentPage + 1 <= lastPage, false);

        const dynamicCount = pages.length - 4;
        const sideCount = (dynamicCount - 1) / 2;
        const middleMin = sideCount + 1;
        const middleMax = lastPage - sideCount;
        const middleVal = currentPage < middleMin
            ? middleMin
            : currentPage > middleMax
                ? middleMax
                : currentPage;
        let leftStart = middleVal - sideCount;
        leftStart = leftStart < 1 ? 1 : leftStart;

        let curDynamicPage;
        for(let i = 2, curDynamicPage = leftStart; i < dynamicCount + 2; i++, curDynamicPage++ )
        {
            const forward = curDynamicPage > currentPage;
            const curTeam = forward ? teams[teams.length - 1] : teams[0];
            const curCount = Math.abs(curDynamicPage - currentPage);
            const active = curDynamicPage <= lastPage && curDynamicPage != currentPage;
            PaginationUtil.updatePaginationPage(pages.item(i), params, PAGE_TYPE.GENERAL, forward, curCount, currentPage, (active || curDynamicPage == currentPage) ? curDynamicPage : "", active, curDynamicPage == currentPage);
        }
    }

    static updatePaginationPage(page, params, pageType, forward, count, pageNumber, label, enabled, current)
    {
        if(label === "")
        {
            page.parentElement.classList.add("d-none");
        }
        else
        {
            page.parentElement.classList.remove("d-none");
        }
        if(!enabled)
        {
            page.parentElement.classList.remove("enabled");
            page.parentElement.classList.add("disabled");
            page.removeEventListener("click", LadderUtil.ladderPaginationPageClick);
        }
        else if (enabled && !page.classList.contains("enabled"))
        {
            page.parentElement.classList.add("enabled");
            page.parentElement.classList.remove("disabled");
            page.addEventListener("click", LadderUtil.ladderPaginationPageClick)
        }
        if(!current)
        {
            page.parentElement.classList.remove("active");
        }
        else
        {
            page.parentElement.classList.add("active");
        }

        let pageParams;
        switch(pageType)
        {
            case PAGE_TYPE.FIRST:
                pageParams = params.first;
                break;
            case PAGE_TYPE.LAST:
                pageParams = params.last;
                break;
            case PAGE_TYPE.GENERAL:
                pageParams = forward ? params.forward : params.backward;
                break;
        }
        for(let [key, val] of pageParams) page.setAttribute("data-page-" + key, val);

        page.setAttribute("data-page-forward", forward);
        page.setAttribute("data-page-count", count);
        page.setAttribute("data-page-number", pageNumber);
        page.textContent = label;
    }

    static createPaginations()
    {
        for(const container of document.getElementsByClassName("pagination"))
        {
            PaginationUtil.createPagination(container, 4);
        }
    }

    static createPagination(container, sidePageCount)
    {
        let i;
        const pageCount = sidePageCount * 2 + 1 + 2 + 2;
        for (i = 0; i < pageCount; i++)
        {
            container.appendChild(PaginationUtil.createPaginationPage(1, ""));
        }
    }

    static createPaginationPage(pageNum, label)
    {
        const li = document.createElement("li");
        li.classList.add("page-item");
        const page = document.createElement("a");
        page.setAttribute("href", "#generated-info-all");
        page.classList.add("page-link");
        page.textContent = label;
        page.setAttribute("data-page-number", pageNum);
        li.appendChild(page);
        return li;
    }

}

PaginationUtil.PAGINATION_SIDE_BUTTON_COUNT = 4;
