// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

//Inspired by https://aoe4world.com/ UI

class MatrixUI {

    constructor(
        id,
        data,
        mainParameter,
        renderParameters,
        theme = THEME.LIGHT,
        afterValueMutation = null,
        toStringConverter = (name, value)=>value) {
            this.id = id;
            this.data = data;
            this.mainParameter = mainParameter;
            this.renderParameters = renderParameters;
            this.afterValueMutation = afterValueMutation;
            this.toStringConverter = toStringConverter;
            this.highlightMidPoint = 0;
            this.theme = theme;
            this.useDataColors = true;
            this.series = null;
            this.categories == null;
            this.cells = null;
            this.node = null;
            this.afterDataProcessing = null;
            MatrixUI.OBJECTS.set(id, this);
    }

    static getCellType(rowIx, colIx)
    {
        if(rowIx == 0) {
            if(colIx == 0) return CELL_TYPE.SUMMARY_CELL;
            return CELL_TYPE.SUMMARY_ROW;
        }
        if(colIx == 0) return CELL_TYPE.SUMMARY_COLUMN;
        return CELL_TYPE.DATA;
    }
    
    setTheme(theme)
    {
        this.theme = theme;
    }

    setSeriesComparator(comparator)
    {
        this.seriesComparator = comparator;
    }
    
    setUseDataColors(useDataColors)
    {
        this.useDataColors = useDataColors;
    }

    setAfterDataProcessing(afterDataProcessing)
    {
        this.afterDataProcessing = afterDataProcessing;
    }

    getCategories()
    {
        if(this.categories == null) this.categories = this.processCategories();
        return this.categories;
    }

    processCategories()
    {
        const categories = new Map();
        categories.set("Total", 0);
        [...new Set(this.data
            .flatMap(series=>series.values)
            .map(value=>value.category))]
            .sort()
            .forEach((category, ix)=>categories.set(category, ix + 1));
        return categories;
    }

    getSeries()
    {
        if(this.series == null) this.series = this.processSeries();
        return this.series;
    }

    processSeries()
    {
        const series = new Map();
        series.set("Total", 0);
        this.data.map(s=>s.name)
            .forEach((name, ix)=>series.set(name, ix + 1));
        return series;
    }

    getSummaryCell()
    {
        return this.cells ? [this.cells[0][0]] : null;
    }

    getSummaryRow()
    {
        return this.cells ? this.cells[0].slice(1, this.cells[0].length) : null;
    }

    getSummaryColumns()
    {
        return this.cells ? this.cells.slice(1, this.cells.length).map(row=>row[0]) : null;
    }

    getDataCells()
    {
        return this.cells
            ? this.cells.slice(1, this.cells.length)
                .map(row=>row.slice(1, row.length))
                .flat(1)
            : null;
    }

    getCells(type)
    {
        return type == null ? this.cells : type.getCells(this);
    }

    processCells()
    {
        const series = this.getSeries();
        const categories = this.getCategories();
        const cells = new Array(series.size);
        const emptyObject = Util.emptyClone(this.data[0].values[0].value);
        for(const dataSeries of this.data) {
            const serIx = series.get(dataSeries.name);
            cells[serIx] = new Array(categories.size);
            cells[serIx][0] = emptyObject; //summary cell
            for(const dataEntry of dataSeries.values)
                cells[serIx][categories.get(dataEntry.category)] = dataEntry.value;
        }
        cells[0] = this.processSeriesSummaryCells(cells);
        this.processCategorySummaryCells(cells);

        return cells;
    }

    processSeriesSummaryCells(cells)
    {
        const summary = new Array(cells[1].length);
        for(let i = 0; i < summary.length; i++) {
            const summaryColumnCell = Util.addObjects(cells.map(row=>row[i]).filter(o=>o != null));
            if(this.afterValueMutation != null) this.afterValueMutation(summaryColumnCell);
            summary[i] = summaryColumnCell;
        }
        return summary;
    }

    processCategorySummaryCells(cells)
    {
        for(let i = 0; i < cells.length; i++) {
            const summaryRowCell = Util.addObjects(cells[i].filter(o=>o != null));
            if(this.afterValueMutation != null) this.afterValueMutation(summaryRowCell);
            cells[i][0] = summaryRowCell;
        }
    }

    processData()
    {
        if(this.cells == null) {
            this.cells = this.processCells();
            if(this.afterDataProcessing) this.afterDataProcessing();
        }
    }

    clear()
    {
        this.cells = null;
        this.clearNode();
    }

    render()
    {
        this.processData();

        const series = this.getSeries();
        const categories = this.getCategories();
        const tableCategories = [""].concat(Array.from(categories.keys()));
        const table = TableUtil.createTable(tableCategories);
        const tableElem = table.querySelector(":scope table");
        tableElem.className = '';
        tableElem.classList.add("matrix", "sticky", "mx-auto");
        const tbody = table.querySelector(":scope tbody");
        const seriesArray = Array.from(series.keys());
        for(let seriesIx = 0; seriesIx < seriesArray.length; seriesIx++) {
            const row = this.cells[seriesIx];
            const tr = document.createElement("tr");
            TableUtil.createRowTh(tr).textContent = seriesArray[seriesIx];
            for(const col of row) {
                const td = document.createElement("td");
                if(col) {
                    for(const param of this.renderParameters) {
                        td.appendChild(ElementUtil.createElement(
                        "div",
                        null,
                        "parameter",
                        this.toStringConverter(param, col[param]),
                        [["data-parameter-name", param]]));
                    }
                }
                tr.appendChild(td);
            }
            tbody.appendChild(tr);
        }
        this.node = table;
        this.applyMainParameter();
        this.highlightMinMax();

        return table;
    }

    getNode()
    {
        return this.node;
    }

    removeNode()
    {
        if(this.getNode().parentNode) this.getNode().parentNode.removeChild(this.getNode());
    }
    
    clearNode()
    {
        this.removeNode();
        this.node = null;
    }

    remove()
    {
        this.removeNode();
        MatrixUI.OBJECTS.delete(this.id);
    }

    setMainParameter(parameter)
    {
        this.mainParameter = parameter;
    }

    applyMainParameter()
    {
        this.node.querySelectorAll(":scope .parameter.main").forEach(elem=>elem.classList.remove("main"));
        this.node.querySelectorAll(':scope .parameter[data-parameter-name="' + this.mainParameter + '"]')
            .forEach(elem=>elem.classList.add("main"));
    }

    setHighlightRange(min, mid, max)
    {
        if(min > max) throw new Error("Invalid boundaries, min should be less than max");
        if(mid < min || mid > max) throw new Error("Highlight mid is out of boundaries");

        this.highlightMin = min;
        this.highlightMax = max;
        this.highlightMidPoint = mid;
        this.highlightMinSize = mid - min;
        this.highlightMaxSize = max - mid;
    }

    highlight()
    {
        if(this.highlightMin != null) {
            this.highlightMinMax();
        } else {
            throw new Error("Unsupported operation");
        }
    }

    highlightMinMax()
    {
        const tbody = this.node.querySelector(":scope tbody");
        for(let rowIx = 0; rowIx < this.cells.length; rowIx++) {
            const tr = tbody.children[rowIx];
            const data = rowIx != 0 ? this.data[rowIx - 1] : null;
            const backgroundColors = this.useDataColors && data
                ? data.backgroundColors || MatrixUI.HIGHLIGHT_BACKGROUND_COLORS
                : MatrixUI.HIGHLIGHT_BACKGROUND_COLORS;
            const colors = this.useDataColors && data
                ? data.colors || MatrixUI.HIGHLIGHT_COLORS
                : MatrixUI.HIGHLIGHT_COLORS;
            for(let colIx = 0; colIx < this.cells[rowIx].length; colIx++) {
                const value = this.cells[rowIx][colIx][this.mainParameter];
                if(value == null) continue;
                const diff = value - this.highlightMidPoint;
                const highlightSize = diff < 0 ? this.highlightMinSize : this.highlightMaxSize;
                const opacity = Math.min((Math.abs(diff) / highlightSize) * MatrixUI.HIGHLIGHT_MAX_OPACITY, MatrixUI.HIGHLIGHT_MAX_OPACITY);
                const highlightColor = this.getBackgroundHighlightColor(backgroundColors, diff, opacity);
                const color = diff == 0
                    ? colors.neutral
                    : diff < 0
                        ? colors.negative
                        : colors.positive;
                const td = tr.children[colIx + 1];
                td.setAttribute("style", "background-color: " + highlightColor + "; color: " + color + ";");
            }
        }
    }

    getBackgroundHighlightColor(colors, diff, opacity)
    {
        const color = colors[this.theme.name][diff < 0 ? "negative" : "positive"];
        return Util.changeFullRgbaAlpha(color, opacity);
    }

}

MatrixUI.OBJECTS = new Map();

MatrixUI.HIGHLIGHT_MAX_OPACITY = 0.4;
MatrixUI.HIGHLIGHT_NEGATIVE_COLOR = "rgba(220, 53, 69)";
MatrixUI.HIGHLIGHT_NEUTRAL_COLOR = "rgba(128, 128, 128)";
MatrixUI.HIGHLIGHT_POSITIVE_COLOR = "rgba(40, 167, 69)";
MatrixUI.HIGHLIGHT_COLORS = {
    negative: MatrixUI.HIGHLIGHT_NEGATIVE_COLOR,
    neutral: MatrixUI.HIGHLIGHT_NEUTRAL_COLOR,
    positive: MatrixUI.HIGHLIGHT_POSITIVE_COLOR
}

MatrixUI.HIGHLIGHT_NEGATIVE_BACKGROUND_COLOR_DARK = "rgba(110, 26, 35, 1)";
MatrixUI.HIGHLIGHT_POSITIVE_BACKGROUND_COLOR_DARK = "rgba(20, 83, 35, 1)";
MatrixUI.HIGHLIGHT_NEGATIVE_BACKGROUND_COLOR_LIGHT = "rgba(220, 53, 69, 1) ";
MatrixUI.HIGHLIGHT_POSITIVE_BACKGROUND_COLOR_LIGHT = "rgba(40, 167, 69, 1) ";
MatrixUI.HIGHLIGHT_BACKGROUND_COLORS = {
    dark: {
        negative: MatrixUI.HIGHLIGHT_NEGATIVE_BACKGROUND_COLOR_DARK,
        positive: MatrixUI.HIGHLIGHT_POSITIVE_BACKGROUND_COLOR_DARK
    },
    light: {
        negative: MatrixUI.HIGHLIGHT_NEGATIVE_BACKGROUND_COLOR_LIGHT,
        positive: MatrixUI.HIGHLIGHT_POSITIVE_BACKGROUND_COLOR_LIGHT
    }
}

const CELL_TYPE = Object.freeze
({
    SUMMARY_CELL: {name: "summaryCell", order: 1, getCells: (matrix)=>matrix.getSummaryCell()},
    SUMMARY_ROW: {name: "summaryRow", order: 2, getCells: (matrix)=>matrix.getSummaryRow()},
    SUMMARY_COLUMN: {name: "summaryColumn", order: 3, getCells: (matrix)=>matrix.getSummaryColumns()},
    DATA: {name: "data", order: 4, getCells: (matrix)=>matrix.getDataCells()}
});
