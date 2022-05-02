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
            th.setAttribute("scope", "col");
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

    static createRowTh(row = null)
    {
        const th = document.createElement("th");
        th.setAttribute("scope", "row");
        if(row != null) row.appendChild(th);
        return th;
    }

    static insertCell(tr, clazz)
    {
        const td = tr.insertCell();
        td.setAttribute("class", clazz);
        return td;
    }

    static updateColRowTable(table, data, sorter = null, headTranslator = null, rowTranslator = null, rowSorter = null)
    {
        const headRow = table.querySelector(":scope thead tr");
        ElementUtil.removeChildren(headRow);
        //row header padding
        headRow.appendChild(document.createElement("th"));
        const tBody = table.getElementsByTagName("tbody")[0];
        ElementUtil.removeChildren(tBody);
        if(!data) {table.setAttribute("data-last-updated", Date.now()); return;}

        let rowIx = 0;
        const headers = TableUtil.collectHeaders(data).sort(sorter == null ? (a, b)=>b[0].localeCompare(a[0]) : sorter);
        for(const header of headers)
        {
            const headCell = document.createElement("th");
            headCell.setAttribute("scope", "col");
            const headerTranslated = headTranslator == null ? header : headTranslator(header);
            headCell.setAttribute("data-chart-color", headerTranslated.toLowerCase());
            headCell.textContent = headerTranslated;
            headRow.appendChild(headCell);
        }
        const entries = Object.entries(data);
        if(rowSorter) entries.sort((a, b)=>rowSorter(a[0], b[0]));
        for(const[rowHeader, rowData] of entries)
        {
            const bodyRow = document.createElement("tr");
            const rowHeadCell = document.createElement("th");
            const rowHeaderTranslated = rowTranslator == null ? rowHeader : rowTranslator(rowHeader);
            rowHeadCell.setAttribute("scope", "row");
            rowHeadCell.textContent = rowHeaderTranslated;
            bodyRow.appendChild(rowHeadCell);
            for(const header of headers)
                bodyRow.insertCell().textContent = rowData[header] != null ? rowData[header] : "";
            tBody.appendChild(bodyRow);
            rowIx++;
        }
        table.setAttribute("data-last-updated", Date.now());
    }

    static collectHeaders(data)
    {
        const headers = [];
        if(!data) return headers;

        for(const[rowHeader, rowData] of Object.entries(data))
        {
            for(const [header, value] of Object.entries(rowData))
            {
                if(!headers.includes(header)) headers.push(header);
            }
        }
        return headers;
    }

    static updateGenericTable(table, data, sorter = null, translator = null)
    {
        const headRow = table.querySelector(":scope thead tr");
        const bodyRow = table.querySelector(":scope tbody tr");
        ElementUtil.removeChildren(headRow);
        ElementUtil.removeChildren(bodyRow);
        if(!data) {table.setAttribute("data-last-updated", Date.now()); return;}

        for(const [header, value] of Object.entries(data).sort(sorter == null ? (a, b)=>b[0].localeCompare(a[0]) : sorter))
        {
            const headCell = document.createElement("th");
            const headerTranslated = translator == null ? header : translator(header);
            headCell.setAttribute("data-chart-color", headerTranslated.toLowerCase());
            headCell.textContent = headerTranslated;
            headRow.appendChild(headCell);

            bodyRow.insertCell().textContent = value;
        }
        table.setAttribute("data-last-updated", Date.now());
    }

    static sortTable(table, ths)
    {
        if(ths.length < 1) return;

        const tbody = table.querySelector('tbody');
        const thsArray = Array.from(ths[0].parentNode.children);
        const ixs = [];
        for(const th of ths) ixs.push(thsArray.indexOf(th));
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
        const headings = elem.querySelectorAll(":scope thead th");
        const rows = mode === "foot"
            ? elem.querySelectorAll(":scope tfoot tr")
            : elem.querySelectorAll(":scope tbody tr");
        if(rows.length == 0) return {headers: headers, rowHeaders: rowHeaders, values: allVals, colors: colors};

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

    static updateVirtualColRowTable(table, data, dataSetter, sorter = null, headTranslator = null, rowTranslator = null)
    {
        const rawHeaders = TableUtil.collectHeaders(data).sort(sorter == null ? (a, b)=>b[0].localeCompare(a[0]) : sorter);
        const headers = [];
        const colors = [];
        const rowHeaders = [];
        const allVals = [];
        rawHeaders.forEach(rawHeader=>{
            const header = headTranslator == null ? rawHeader : headTranslator(rawHeader);
            headers.push(header);
            colors.push(header.toLowerCase());
            allVals.push([]);
        });
        for(const [rowHeader, vals] of Object.entries(data)) {
            rowHeaders.push(rowTranslator == null ? rowHeader : rowTranslator(rowHeader));
            for(let hIx = 0; hIx < rawHeaders.length; hIx ++) allVals[hIx].push(vals[rawHeaders[hIx]]);
        }
        dataSetter({headers: headers, rowHeaders: rowHeaders, values: allVals, colors: colors});
        table.setAttribute("data-last-updated", Date.now());
    }

    static hoverableColumnHeader(thead)
    {
        const oldText = thead.textContent;
        thead.innerHTML = "<span>" + oldText + "</span>"
        thead.classList.add("hoverable");
        BootstrapUtil.addTooltip(thead, "Hover over this column values to get a more detailed view");
    }

    static createSimpleRow(object, property)
    {
        const row = document.createElement("tr");
        TableUtil.createRowTh(row).textContent = property;
        row.insertCell().textContent = object[property];
        return row;
    }

}

TableUtil.tableComparer = (idxs, asc) => (a, b) =>((v1, v2) =>Util.compareValueArrays(v1, v2))
    (TableUtil.getCellValues(asc ? a : b, idxs), TableUtil.getCellValues(asc ? b : a, idxs));
