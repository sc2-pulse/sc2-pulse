// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class PaginationUtil
{

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
        if(pageParams != null)
            for(const [key, val] of pageParams)
                page.setAttribute("data-page-" + key, val);

        page.setAttribute("data-page-count", count);
        page.setAttribute("data-page-number", pageNumber);
        page.textContent = label;
    }

    static createPaginations()
    {
        for(const container of document.getElementsByClassName("pagination"))
        {
            const sideButtonCount = container.getAttribute("data-pagination-side-button-count");
            const anchor = container.getAttribute("data-pagination-anchor");
            PaginationUtil.createPagination(container, sideButtonCount || PaginationUtil.PAGINATION_SIDE_BUTTON_COUNT, anchor);
        }
        PaginationUtil.PAGINATIONS.set("ladder",
            new Pagination(
                ".pagination-ladder",
                [
                    {name: "rating-cursor", min: 0, max: 99999, getter: (t)=>t.rating},
                    {name: "id-cursor", min: 0, max: 1, getter: (t)=>t.id}
                ],
                LadderUtil.ladderPaginationPageClick)
        );
    }

    static createPagination(container, sidePageCount, anchor, mainPage = false)
    {
        let i;
        const pageCount = sidePageCount * 2 + (mainPage ? 1 : 0) + 2 + 2;
        for (i = 0; i < pageCount; i++)
        {
            container.appendChild(PaginationUtil.createPaginationPage(1, "", anchor));
        }
    }

    static createPaginationPage(pageNum, label, anchor)
    {
        const li = document.createElement("li");
        li.classList.add("page-item");
        const page = document.createElement("a");
        page.setAttribute("href", anchor);
        page.classList.add("page-link");
        page.textContent = label;
        page.setAttribute("data-page-number", pageNum);
        li.appendChild(page);
        return li;
    }

    static resultToPagedResult(result)
    {
        const r =
        {
            meta:
            {
                totalCount: result.length,
                perPage: result.length,
                pageCount: 1,
                page: 1,
                pageDiff: 0
            },
            result: result,
            empty: result.length == 0 ? false : true
        };
        return r;
    }

    static createCursorMeta(isEmpty, isFirstPage, sortingOrder)
    {
        return {
            page: isEmpty
                ? sortingOrder == SORTING_ORDER.ASC
                    ? PaginationUtil.CURSOR_DISABLED_PREV_PAGE_NUMBER
                    : PaginationUtil.CURSOR_DISABLED_NEXT_PAGE_NUMBER
                : isFirstPage
                    ? PaginationUtil.CURSOR_DISABLED_PREV_PAGE_NUMBER
                    : PaginationUtil.CURSOR_PAGE_NUMBER,
            pageDiff: sortingOrder == SORTING_ORDER.DESC ? 1 : -1,
            isLastPage: isEmpty && sortingOrder == SORTING_ORDER.DESC
        };
    }

}

PaginationUtil.PAGINATION_SIDE_BUTTON_COUNT = 0;
PaginationUtil.CURSOR_PAGE_NUMBER = 2;
PaginationUtil.CURSOR_DISABLED_PREV_PAGE_NUMBER = 1;
PaginationUtil.CURSOR_DISABLED_NEXT_PAGE_NUMBER = 3;
PaginationUtil.PAGINATIONS = new Map();