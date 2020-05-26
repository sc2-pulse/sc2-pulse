// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class TableUtil
{

    static createTable(theads, responsive = true)
    {
        const table = document.createElement("table");
        const thead = document.createElement("thead");
        const thr = document.createElement("tr");
        for(const h of theads)
        {
            const th = document.createElement("th");
            th.setAttribute("span", "col");
            th.textContent = h;
            thr.appendChild(th);
        }
        thead.appendChild(thr);
        const tbody = document.createElement("tbody");
        table.appendChild(thead);
        table.appendChild(tbody);
        table.classList.add("table", "table-sm", "table-hover");
        if(responsive)
        {
            const tcontainer = document.createElement("div");
            tcontainer.classList.add("table-responsive");
            tcontainer.appendChild(table);
            return tcontainer;
        }
        return table;
    }

    static updateColRowTable(table, data, sorter = null, headTranslator = null, rowTranslator = null)
    {
        const headRow = table.getElementsByTagName("thead")[0].getElementsByTagName("tr")[0];
        ElementUtil.removeChildren(headRow);
        //row header padding
        headRow.appendChild(document.createElement("th"));
        const tBody = table.getElementsByTagName("tbody")[0];
        ElementUtil.removeChildren(tBody);
        let rowIx = 0;
        for(const[rowHeader, rowData] of Object.entries(data))
        {
            const bodyRow = document.createElement("tr");
            const rowHeadCell = document.createElement("th");
            const rowHeaderTranslated = rowTranslator == null ? rowHeader : rowTranslator(rowHeader);
            rowHeadCell.setAttribute("span", "row");
            rowHeadCell.appendChild(document.createTextNode(rowHeaderTranslated));
            bodyRow.appendChild(rowHeadCell);
            for(const [header, value] of Object.entries(rowData).sort(sorter == null ? (a, b)=>b[0].localeCompare(a[0]) : sorter))
            {
                if(rowIx == 0)
                {
                    const headCell = document.createElement("th");
                    headCell.setAttribute("span", "col");
                    const headerTranslated = headTranslator == null ? header : headTranslator(header);
                    headCell.setAttribute("data-chart-color", headerTranslated.toLowerCase());
                    headCell.appendChild(document.createTextNode(headerTranslated));
                    headRow.appendChild(headCell);
                }
                bodyRow.insertCell().appendChild(document.createTextNode(value));
            }
            tBody.appendChild(bodyRow);
            rowIx++;
        }
        table.setAttribute("data-last-updated", Date.now());
    }

    static updateGenericTable(table, data, sorter = null, translator = null)
    {
        const headRow = table.getElementsByTagName("thead")[0].getElementsByTagName("tr")[0];
        const bodyRow = table.getElementsByTagName("tbody")[0].getElementsByTagName("tr")[0];
        ElementUtil.removeChildren(headRow);
        ElementUtil.removeChildren(bodyRow);
        for(const [header, value] of Object.entries(data).sort(sorter == null ? (a, b)=>b[0].localeCompare(a[0]) : sorter))
        {
            const headCell = document.createElement("th");
            const headerTranslated = translator == null ? header : translator(header);
            headCell.setAttribute("data-chart-color", headerTranslated.toLowerCase());
            headCell.appendChild(document.createTextNode(headerTranslated));
            headRow.appendChild(headCell);

            bodyRow.insertCell().appendChild(document.createTextNode(value));
        }
        table.setAttribute("data-last-updated", Date.now());
    }

    static sortTable(table, ths)
    {
        if(ths.length < 1) return;

        const tbody = table.querySelector('tbody');
        const thsArray = Array.from(ths[0].parentNode.children);
        const ixs = [];
        for(th of ths) ixs.push(thsArray.indexOf(th));
        Array.from(tbody.querySelectorAll('tr'))
            .sort(TableUtil.tableComparer(ixs, false))
            .forEach(tr => tbody.appendChild(tr));
    }

    static getCellValues(tr, idxs)
    {
        var vals = [];
        for(const idx of idxs) vals.push(tr.children[idx].innerText || tr.children[idx].textContent);
        return vals;
    }

    static collectTableData(elem)
    {
        let mode = elem.getAttribute("data-chart-collection-mode");
        mode = mode == null ? "body" : mode;
        const headers = [];
        const rowHeaders = [];
        const allVals = [];
        const colors = [];
        const headings = elem.getElementsByTagName("thead")[0].getElementsByTagName("tr")[0].getElementsByTagName("th");
        const rows = mode === "foot"
            ? elem.getElementsByTagName("tfoot")[0].getElementsByTagName("tr")
            : elem.getElementsByTagName("tbody")[0].getElementsByTagName("tr");
        const startIx = rows[0].getElementsByTagName("th").length > 0 ? 1 : 0;
        for (let i = startIx; i < headings.length; i++)
        {
            const heading = headings[i];
            headers.push(heading.textContent);
            allVals.push([]);
            colors.push(heading.getAttribute("data-chart-color"));
        }

        for (let i = 0; i < rows.length; i++)
        {
            const row = rows[i];
            if(startIx == 1) rowHeaders.push(row.getElementsByTagName("th")[0].textContent);
            const tds = row.getElementsByTagName("td");
            for (let tdix = 0; tdix < tds.length; tdix++)
            {
                const iText = tds[tdix].textContent;
                allVals[tdix].push(parseFloat(iText));
            }
        }
        return {headers: headers, rowHeaders: rowHeaders, values: allVals, colors: colors};
    }

}

TableUtil.tableComparer = (idxs, asc) => (a, b) =>((v1, v2) =>Util.compareValueArrays(v1, v2))
    (TableUtil.getCellValues(asc ? a : b, idxs), TableUtil.getCellValues(asc ? b : a, idxs));
