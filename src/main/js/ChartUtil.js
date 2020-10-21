// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class ChartUtil
{

    static createChart(chartable)
    {
        const type = chartable.getAttribute("data-chart-type");
        const stacked = chartable.getAttribute("data-chart-stacked");
        const title = chartable.getAttribute("data-chart-title");
        const tooltipPercentage = chartable.getAttribute("data-chart-tooltip-percentage");
        const tooltipSort = chartable.getAttribute("data-chart-tooltip-sort");
        const ctx = document.getElementById(chartable.getAttribute("data-chart-id")).getContext("2d");
        const data = ChartUtil.collectChartJSData(chartable);
        ChartUtil.decorateChartData(data, type);
        const chart = new Chart
        (
            ctx,
            {
                type: type == "line" ? "lineVCursor" : type,
                data: data,
                options:
                {
                    title:
                    {
                        display: title == null ? false : true,
                        text: title
                    },
                    scales:
                    {
                        xAxes:
                        [{
                            display: false,
                            stacked: stacked === "true" ? true : false
                        }],
                        yAxes:
                        [{
                           // ticks:{beginAtZero: true},
                            ticks: {callback: (val, valIx, vals)=>Util.NUMBER_FORMAT.format(val)},
                            stacked: stacked === "true" ? true : false
                        }]
                    },
                    hover:
                    {
                        mode: (data.customMeta.type === "pie" || data.customMeta === "doughnut")
                            ? "dataset"
                            : "index",
                        position: "nearest",
                        intersect: false,
                        animationDuration: 0
                    },
                    tooltips:
                    {
                        bodyFontFamily: "'Liberation Mono', monospace",
                        mode: (data.customMeta.type === "pie" || data.customMeta === "doughnut")
                            ? "dataset"
                            : "index",
                        position: "nearest",
                        intersect: false,
                        callbacks:
                        {
                            label: tooltipPercentage === "true" ? ChartUtil.addTooltipPercentage : ChartUtil.formatTooltip
                        },
                        ...(tooltipSort === "reverse") && {itemSort: ChartUtil.sortTooltipReversed}
                    },
                    layout: {padding: {right: 15}}
                }
            }
        );
        ChartUtil.CHARTS.set(chartable.id, chart);
    }

    static formatTooltip(tooltipItem, data)
    {
        let label;
        let labels;
        if(data.customMeta.type === "pie" || data.customMeta === "doughnut")
        {
            labels = data.labels;
            label = labels[tooltipItem.index];
        }
        else
        {
            labels = data.datasets.map(ds=>ds.label);
            label = labels[tooltipItem.datasetIndex];
        }
        label = Util.addStringTail(label, labels, " ");
        label += " " + Util.NUMBER_FORMAT.format(data.datasets[tooltipItem.datasetIndex].data[tooltipItem.index]);
        return label;
    }

    static addTooltipPercentage(tooltipItem, data)
    {
        let label;
        if(data.customMeta.type === "pie" || data.customMeta === "doughnut")
        {
            label = data.labels[tooltipItem.index];
        }
        else
        {
            label = data.datasets[tooltipItem.datasetIndex].label;
        }
        label += " "
            + Util.NUMBER_FORMAT.format(data.datasets[tooltipItem.datasetIndex].data[tooltipItem.index]);
        let sum = 0;
        for(const dataset of data.datasets) sum += dataset.data[tooltipItem.index];
        label += "\t(" + Util.calculatePercentage(data.datasets[tooltipItem.datasetIndex].data[tooltipItem.index], sum) + "%)";
        return label;
    }

    static sortTooltipReversed(a, b, data)
    {
        return a.datasetIndex !== b.datasetIndex
            ? (b.datasetIndex - a.datasetIndex)
            : (b.index - a.index);
    }

    static decorateChartData(data, type)
    {
        for (let i = 0; i < data.datasets.length; i++)
        {
            if (type === "lineVCursor" || type === "line")
            {
                data.datasets[i]["borderWidth"] = 2;
                data.datasets[i]["pointRadius"] = 0;
                data.datasets[i]["hoverPointRadius"] = 2;

                data.datasets[i]["borderColor"] = SC2Restful.COLORS.get(data.customColors[i]);
                data.datasets[i]["pointBackgroundColor"] = SC2Restful.COLORS.get(data.customColors[i]);
                //data.datasets[i]["pointBorderColor"] = SC2Restful.COLORS.get(data.customColors[i]);
                data.datasets[i]["backgroundColor"] = "rgba(0, 0, 0, 0)";
            }
            else if(type === "doughnut" || type === "pie")
            {
                const dataColors = [];
                const dataEmptyColors = [];
                for(let dataValIx = 0; dataValIx < data.datasets[i].data.length; dataValIx++)
                {
                    dataColors.push(SC2Restful.COLORS.get(data.customColors[dataValIx]));
                    dataEmptyColors.push("rgba(0, 0, 0, 0)");
                }
                data.datasets[i]["backgroundColor"] = dataColors;
                data.datasets[i]["borderColor"] = dataEmptyColors;
            }
            else
            {
                data.datasets[i]["backgroundColor"] = SC2Restful.COLORS.get(data.customColors[i]);
                data.datasets[i]["borderColor"] = "rgba(0, 0, 0, 0)";
            }
        }
    }

    static collectChartJSData(elem)
    {
        const type = elem.getAttribute("data-chart-type");
        const stacked = elem.getAttribute("data-chart-stacked");
        const tableData = TableUtil.collectTableData(elem);
        const datasets = [];
        if(type !== "doughnut" && type !== "pie")
        {
            for (let i = 0; i < tableData.headers.length; i++)
            {
                datasets.push
                (
                    {
                        label: tableData.headers[i],
                        data: tableData.values[i],
                        hidden: !Util.hasNonZeroValues(tableData.values[i])
                    }
                )
            }
        }
        else
        {
            const datasetData = [];
            for (let i = 0; i < tableData.headers.length; i++)
            {
                datasetData.push(tableData.values[i][0]);
            }
            datasets.push({data: datasetData});
        }
        const data =
        {
            labels: tableData.rowHeaders.length > 0 ? tableData.rowHeaders : tableData.headers,
            datasets: datasets,
            customColors: tableData.colors,
            customMeta:
            {
                type: type
            }
        }
        return data;
    }

    static updateChart(chart, data)
    {
        if (data === null)
        {
            return;
        }
        if
        (
            chart.data.labels.length === data.labels.length
            && chart.data.labels.every(function(val, ix){val === data.labels[ix]})
        )
        {
            for (let i = 0; i < data.datasets.length; i++)
            {
                chart.data.datasets[i].label = data.datasets[i].label;
                chart.data.datasets[i].data = data.datasets[i].data;
                chart.data.datasets[i].hidden = data.datasets[i].hidden;
            }
        }
        else
        {
            ChartUtil.decorateChartData(data, chart.config.type);
            chart.data = data;
        }
        chart.update();
    }

    static updateChartable(chartable)
    {
        const chart = ChartUtil.CHARTS.get(chartable.id);
        if (chart === undefined)
        {
            ChartUtil.createChart(chartable);
        }
        else
        {
            ChartUtil.updateChart(chart, ChartUtil.collectChartJSData(chartable))
        }
    }

    static observeChartables()
    {
        for(const chartable of document.getElementsByClassName("chartable"))
        {
            ChartUtil.CHARTABLE_OBSERVER.observe(chartable, ChartUtil.CHARTABLE_OBSERVER_CONFIG);
        }
    }

    static onChartableMutation(mutations, observer)
    {
        for(const mutation of mutations)
        {
            ChartUtil.updateChartable(mutation.target);
        }
    }

    static observeCharts()
    {
        for(const chart of document.querySelectorAll(".c-chart")) ChartUtil.CHART_OBSERVER.observe(chart, ChartUtil.CHART_OBSERVER_CONFIG);
    }

    static onChartMutation(mutations, observer)
    {
        for(mutation of mutations)
        {
            const style = mutation.target.getAttribute("style");
            if(!style.includes("width: 0") && !style.includes("height: 0"))
            {
                if(mutation.target.classList.contains("c-ref"))
                    ChartUtil.linkChartTabsHeight(mutation.target);
                ElementUtil.resolveElementPromise(mutation.target.id);
            }
        }
    }

    static linkChartTabsHeight(elem)
    {
        let maxHeight = 0;
        const tabs = elem.closest(".tab-content").querySelectorAll(":scope > .tab-pane");
        for(const relTab of tabs)
        {
            if(relTab.classList.contains("active")) continue;
            relTab.style.minHeight = null;
            maxHeight = Math.max(maxHeight, relTab.clientHeight);
        }
        for(const relTab of tabs) relTab.style.minHeight = maxHeight + "px";
    }

}

ChartUtil.CHARTS = new Map();

ChartUtil.CHARTABLE_OBSERVER_CONFIG =
    {
        attributes: true,
        childList: false,
        subtree: false
    }

ChartUtil.CHART_OBSERVER_CONFIG =
    {
        attributes: true,
        attributeFilter: ["style"],
        childList: false,
        subtree: false,
        characterData: false
    }

ChartUtil.CHARTABLE_OBSERVER = new MutationObserver(ChartUtil.onChartableMutation);
ChartUtil.CHART_OBSERVER = new MutationObserver(ChartUtil.onChartMutation);

Chart.defaults.lineVCursor = Chart.defaults.line;
Chart.controllers.lineVCursor = Chart.controllers.line.extend
({
    draw: function(ease)
    {
        Chart.controllers.line.prototype.draw.call(this, ease);
        if (this.chart.tooltip._active && this.chart.tooltip._active.length)
        {
            var activePoint = this.chart.tooltip._active[0],
            ctx = this.chart.ctx,
            x = activePoint.tooltipPosition().x,
            topY = this.chart.legend.bottom,
            bottomY = this.chart.chartArea.bottom;

            // draw line
            ctx.save();
            ctx.globalCompositeOperation='destination-over';
            ctx.beginPath();
            ctx.moveTo(x, topY);
            ctx.lineTo(x, bottomY);
            ctx.setLineDash([1, 2]);
            ctx.lineWidth = 1;
            ctx.strokeStyle = 'black';
            ctx.stroke();
            ctx.restore();
        }
    }
});
