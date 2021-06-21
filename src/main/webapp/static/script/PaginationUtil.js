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

    static updatePagination(pagination, params, currentPage, isLastPage, onClick)
    {
        const pages = pagination.getElementsByClassName("page-link");
        PaginationUtil.updatePaginationPage(pages.item(0), params, PAGE_TYPE.FIRST, 1, 0, "First", currentPage != 1, false, onClick);
        PaginationUtil.updatePaginationPage(pages.item(1), params, PAGE_TYPE.GENERAL, -1, currentPage, "<", currentPage - 1 >= 1, false, onClick);
        PaginationUtil.updatePaginationPage(pages.item(pages.length - 1), params, PAGE_TYPE.LAST, 1, currentPage, "Last", false, false, onClick);
        PaginationUtil.updatePaginationPage(pages.item(pages.length - 2), params, PAGE_TYPE.GENERAL, 1, currentPage, ">", !isLastPage, false, onClick);

        const dynamicCount = pages.length - 4;
        const sideCount = (dynamicCount - 1) / 2;
        const middleMin = sideCount + 1;
        const middleMax = Number.MAX_VALUE;
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
            const curCount = curDynamicPage - currentPage;
            const active = curDynamicPage != currentPage && (curDynamicPage > currentPage ? !isLastPage : true);
            PaginationUtil.updatePaginationPage(pages.item(i), params, PAGE_TYPE.GENERAL, curCount, currentPage, (active || curDynamicPage == currentPage) ? curDynamicPage : "", active, curDynamicPage == currentPage, onClick);
        }
    }

    static updatePaginationPage(page, params, pageType, count, pageNumber, label, enabled, current, onClick)
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
            page.removeEventListener("click", onClick);
        }
        else if (enabled && !page.classList.contains("enabled"))
        {
            page.parentElement.classList.add("enabled");
            page.parentElement.classList.remove("disabled");
            page.addEventListener("click", onClick)
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
                pageParams = count > -1 ? params.forward : params.backward;
                break;
        }
        for(const [key, val] of pageParams) page.setAttribute("data-page-" + key, val);

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
        PaginationUtil.PAGINATIONS.set("ladder",
            new Pagination(
                ".pagination-ladder",
                [
                    {name: "rating-anchor", min: 0, max: 99999, getter: (t)=>t.rating},
                    {name: "id-anchor", min: 0, max: 1, getter: (t)=>t.id}
                ],
                LadderUtil.ladderPaginationPageClick)
        );
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
PaginationUtil.PAGINATIONS = new Map();