// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class ChartUtil
{

    static createChart(chartable)
    {
        const config = {};
        config["type"] = chartable.getAttribute("data-chart-type");
        config["stacked"] = chartable.getAttribute("data-chart-stacked");
        config["title"] = chartable.getAttribute("data-chart-title");
        config["xTitle"] = chartable.getAttribute("data-chart-x-title");
        config["yTitle"] = chartable.getAttribute("data-chart-y-title");
        config["yReversed"] = chartable.getAttribute("data-chart-y-reversed");
        config["yMin"] = chartable.getAttribute("data-chart-y-min");
        config["yMax"] = chartable.getAttribute("data-chart-y-max");
        config["tooltipPercentage"] = chartable.getAttribute("data-chart-tooltip-percentage");
        config["tooltipSort"] = chartable.getAttribute("data-chart-tooltip-sort");
        config["performance"] = chartable.getAttribute("data-chart-performance");
        config["pointRadius"] = chartable.getAttribute("data-chart-point-radius");
        config["xType"] = chartable.getAttribute("data-chart-x-type");
        config["xTimeUnit"] = chartable.getAttribute("data-chart-x-time-unit");
        config["beginAtZero"] = chartable.getAttribute("data-chart-begin-at-zero");
        config["ctx"] = document.getElementById(chartable.getAttribute("data-chart-id")).getContext("2d");
        config["chartable"] = chartable.id;
        if (!Util.isMobile()) config["zoom"] = chartable.getAttribute("data-chart-zoom");
        config["data"] = ChartUtil.collectChartJSData(chartable);

        ChartUtil.CHARTS.set(chartable.id, ChartUtil.createGenericChart(config));
    }

    static createGenericChart(config)
    {
        ChartUtil.decorateChartData(config.data, config);
        const chart = new Chart
        (
            config.ctx,
            {
                type: config.type == "line" ? "lineVCursor" : config.type,
                data: config.data,
                options:
                {
                    normalized: true,
                    parsing: {xAxisKey: false, yAxisKey: false},
                    aspectRatio: ChartUtil.ASPECT_RATIO,
                    scales:
                    {
                        x:
                        {
                            title: {display: false, text: config.xTitle},
                            grid: {display: false},
                            ticks:
                            {
                                callback: function(value, valIx, vals)
                                {
                                    const val = this.getLabelForValue(vals[valIx].value);

                                    const indexOfStart = val.lastIndexOf("(");
                                    const indexOfEnd = val.lastIndexOf(")");
                                    if(indexOfStart == -1 || indexOfEnd == -1 || indexOfStart > indexOfEnd) return val;

                                    return val.substring(indexOfStart + 1, indexOfEnd);
                                },
                                minRotation: 0,
                                maxRotation: 0,
                                autoSkipPadding: 20,
                                ...(config.performance === "fast") && {sampleSize: 50}
                            },
                            stacked: config.stacked === "true" ? true : false,
                            offset: config.type === "bar" ? true : false,
                            ...(config.xType === "time") && {
                                type: "time",
                                time: {unit: config.xTimeUnit == "false" ? false : config.xTimeUnit}
                            }
                        },
                        y:
                        {
                            title: {display: false, text: config.yTitle},
                            grid:
                            {
                                color: Session.theme == THEME.DARK ? "#242a30" : "rgba(0,0,0,0.1)",
                                borderColor: Session.theme == THEME.DARK ? "#242a30" : "rgba(0,0,0,0.1)"
                             },
                            ticks:
                            {
                                callback: (val, valIx, vals)=>Util.NUMBER_FORMAT.format(val),
                            },
                            stacked: config.stacked === "true" ? true : false,
                            beginAtZero: config.beginAtZero === "true" ? true : false,
                            min: config.beginAtZero === "true" ? config.yMin : null,
                            max: config.beginAtZero === "true" ? config.yMax : null,
                            reverse: config.yReversed === "true" ? true : false
                        }
                    },
                    spanGaps: true,
                    hover:
                    {
                        mode: (config.data.customMeta.type === "pie" || config.data.customMeta === "doughnut")
                            ? "dataset"
                            : "index",
                        position: "nearest",
                        intersect: false
                    },
                    layout: {padding: {right: 15}},
                    animation: false,
                    plugins:
                    {
                        tooltip:
                        {
                            enabled: false,
                            external: ChartUtil.createHtmlTooltip,
                            bodyFontFamily: "'Liberation Mono', monospace",
                            mode: (config.data.customMeta.type === "pie" || config.data.customMeta === "doughnut")
                                ? "dataset"
                                : "index",
                            position: "nearest",
                            intersect: false,
                            callbacks:
                            {
                                beforeBody: ChartUtil.beforeBody,
                                label: config.tooltipPercentage === "true" ? ChartUtil.addTooltipPercentage : ChartUtil.formatTooltip
                            },
                            ...(config.tooltipSort === "reverse") && {itemSort: ChartUtil.sortTooltipReversed}
                        },
                        legend:
                        {
                            onClick:ChartUtil.onLegendClick
                        },
                        title:
                        {
                            display: config.title == null ? false : true,
                            text: config.title
                        },
                        ...(config.zoom) && {
                        zoom:
                        {
                            pan:
                            {
                                enabled: true,
                                mode: config.zoom,
                                onPan: ChartUtil.onZoom
                            },
                            zoom:
                            {
                                mode: config.zoom,
                                onZoom: ChartUtil.onZoom,
                                wheel:{enabled: true, modifierKey: "ctrl"},
                                drag:
                                {
                                    enabled: true,
                                    modifierKey: "shift",
                                    backgroundColor: "rgba(0, 176, 244, 0.15)",
                                    borderColor: "rgb(0, 176, 244)",
                                    borderWidth: "0.5"
                                },
                                pinch:{enabled: false}
                            },
                            limits: {x: {}, y: {}}
                        }}
                    },
                    elements: {line: {tension: config.performance === "fast" ? false : 0.4}}
                }
            }
        );
        chart.customConfig = config;
        if(config.zoom) ChartUtil.createZoomControls(chart);
        ChartUtil.updateChartZoomLimits(chart);
        return chart;
    }

    static createZoomControls(chart)
    {
        const zoomCtl = document.createElement("button");
        zoomCtl.id = "chart-zoom-ctl-" + chart.customConfig.chartable;
        zoomCtl.setAttribute("type", "button");
        zoomCtl.classList.add("btn", "btn-outline-info", "chart-zoom-ctl");
        zoomCtl.setAttribute("data-chartable-id", chart.customConfig.chartable);
        zoomCtl.textContent = "ctrl+wheel/shift+mouse drag to zoom, mouse drag to pan";
        zoomCtl.addEventListener("click", ChartUtil.resetZoom);
        chart.canvas.closest(".container-chart").prepend(zoomCtl);
    }

    static resetZoom(evt)
    {
        const ctl = evt.target;
        if(!ctl.classList.contains("active")) return;

        const chart = ChartUtil.CHARTS.get(ctl.getAttribute("data-chartable-id"));
        chart.resetZoom();
        ctl.textContent = "ctrl+mouse wheel/shift+mouse drag to zoom, mouse drag to pan";
        ctl.classList.remove("active");
        chart.customConfig.isZoomed = false;
    }

    static onZoom(chart)
    {
        document.getElementById("chartjs-tooltip-" + chart.chart.customConfig.chartable).style.opacity = 0;
        const ctl = document.getElementById("chart-zoom-ctl-" + chart.chart.customConfig.chartable);
        ctl.classList.add("active");
        ctl.textContent = "Reset zoom/pan";
        chart.chart.customConfig.isZoomed = true;
    }

    static onLegendClick(e, legendItem)
    {
        const tooltip = document.querySelector("#chartjs-tooltip");
        if(tooltip) tooltip.style.opacity = 0;
        var index = legendItem.datasetIndex;
        var ci = this.chart;
        var meta = ci.getDatasetMeta(index);
        meta.hidden = meta.hidden === null ? !ci.data.datasets[index].hidden : null;
        ci.update();
    }

    static beforeBody(chart)
    {
        return chart[0].chart.customConfig.data.customMeta.headers;
    }

    static formatTooltip(chart)
    {
        let label;
        let labels;
        const data = chart.chart.customConfig.data;
        if(data.customMeta.type === "pie" || data.customMeta === "doughnut")
        {
            labels = data.labels;
            label = labels[chart.dataIndex];
        }
        else
        {
            labels = data.datasets.map(ds=>ds.label);
            label = labels[chart.datasetIndex];
        }
        const rawData = ChartUtil.CHART_RAW_DATA.get(data.customMeta.id);
        if(rawData != null && rawData.additionalDataGetter)
        {
            const additional = rawData.additionalDataGetter(rawData.rawData, data, chart.dataIndex, chart.datasetIndex);
            if(additional.constructor === Array)
            {
                additional.unshift(label);
                label = additional;
            }
            else
            {
                label = [label, additional];
            }
        }
        else
        {
            label = [label, Util.NUMBER_FORMAT.format(data.datasets[chart.datasetIndex].data[chart.dataIndex])];
        }
        return label;
    }

    static addTooltipPercentage(chart, data)
    {
        let label;
        if(data.customMeta.type === "pie" || data.customMeta === "doughnut")
        {
            label = data.labels[chart.dataIndex];
        }
        else
        {
            label = data.datasets[chart.datasetIndex].label;
        }
        label += " "
            + Util.NUMBER_FORMAT.format(data.datasets[chart.datasetIndex].data[chart.dataIndex]);
        let sum = 0;
        for(const dataset of data.datasets) sum += dataset.data[chart.dataIndex];
        label += "\t(" + Util.calculatePercentage(data.datasets[chart.datasetIndex].data[chart.dataIndex], sum) + "%)";
        return label;
    }

    static sortTooltipReversed(a, b, data)
    {
        return a.datasetIndex !== b.datasetIndex
            ? (b.datasetIndex - a.datasetIndex)
            : (b.index - a.index);
    }

    static createHtmlTooltip(context)
    {
        const tooltipModel = context.tooltip;
        const tooltipEl = ChartUtil.getOrCreateTooltipElement(this._chart);
        if (tooltipModel.opacity === 0) {
            tooltipEl.style.opacity = 0;
            return;
        }
        // `this` will be the overall tooltip
        const position = this._chart.canvas.getBoundingClientRect();
        if(tooltipModel.caretX < 0 || tooltipModel.caretX > position.width
            || tooltipModel.caretY < 0 || tooltipModel.caretY > position.height) {
            tooltipEl.style.opacity = 0;
            return;
        }
        ChartUtil.injectTooltipTableHeaders(tooltipEl, tooltipModel);
        ChartUtil.injectTooltipTableData(tooltipEl, tooltipModel);
        ChartUtil.setTooltipPosition(tooltipEl, tooltipModel, context, position);
    }

    static getOrCreateTooltipElement(chart)
    {
        let tooltipEl = document.getElementById('chartjs-tooltip-' + chart.customConfig.chartable);
        if(!tooltipEl)
        {
            tooltipEl = document.createElement('div');
            tooltipEl.id = 'chartjs-tooltip-' + chart.customConfig.chartable;
            tooltipEl.classList.add("chartjs-tooltip");
            tooltipEl.innerHTML = '<h2></h2><table class="table table-sm"><thead></thead><tbody></tbody></table>';
            tooltipEl.style.position = 'absolute';
            tooltipEl.style.pointerEvents = 'none';
            chart.canvas.closest(".container-chart").appendChild(tooltipEl);
        }
        return tooltipEl;
    }

    static injectTooltipTableHeaders(tooltipEl, tooltipModel)
    {
        const thead = tooltipEl.querySelector(":scope table thead");
        ElementUtil.removeChildren(thead);
        if(tooltipModel.beforeBody && tooltipModel.beforeBody.length > 0)
        {
            const thr = thead.insertRow();
            TableUtil.createRowTh(thr).textContent = "L";
            for(const header of tooltipModel.beforeBody) TableUtil.createRowTh(thr).textContent = header;
        }
    }

    static injectTooltipTableData(tooltipEl, tooltipModel)
    {
        const tbody = tooltipEl.querySelector(":scope table tbody");
        ElementUtil.removeChildren(tbody);
        if (tooltipModel.body)
        {
            const titleLines = tooltipModel.title || [];
            const bodyLines = tooltipModel.body.map(bodyItem=>bodyItem.lines);

            titleLines.forEach(title=>tooltipEl.querySelector(":scope h2").textContent = title);

            bodyLines.forEach((body, i)=>{
                const row = tbody.insertRow();
                const legendColor = row.insertCell();
                legendColor.innerHTML ='<div class="legend-color" style="background-color: ' + tooltipModel.labelColors[i].backgroundColor + ';"></div>';
                const image = SC2Restful.IMAGES.get(body[0]);
                if(image) {
                    const cell = row.insertCell();
                    cell.classList.add("text-center");
                    cell.appendChild(image.cloneNode());
                } else {
                    row.insertCell().textContent = body[0];
                }
                for(let i = 1; i < body.length; i++) {
                    const l = body[i];
                    if(l.nodeType) {
                        row.insertCell().appendChild(l);
                    } else {
                        row.insertCell().textContent = l;
                    }
                }
            });
        }
    }

    static setTooltipPosition(tooltipEl, tooltipModel, context, position)
    {
        // Set caret Position
        tooltipEl.classList.remove('above', 'below', 'no-transform');
        if (tooltipModel.yAlign) {
            tooltipEl.classList.add(tooltipModel.yAlign);
        } else {
            tooltipEl.classList.add('no-transform');
        }

        const yAlign = tooltipModel.yAlign;
        const xAlign = tooltipModel.xAlign;

        tooltipEl.style.opacity = 1;
        const { height, width } = tooltipEl.getBoundingClientRect();

        const canvasRect = context.chart.canvas.getBoundingClientRect();
        const positionY = context.chart.canvas.offsetTop;
        const positionX = context.chart.canvas.offsetLeft;

        const caretY = tooltipModel.caretY;
        const caretX = tooltipModel.caretX;

        // Final coordinates
        let top = positionY + caretY - height;
        let left = positionX + caretX - width / 2;
        let space = 8; // This for making space between the caret and the element.

        if (yAlign === "top") {
          top += height + space;
        } else if (yAlign === "center") {
          top += height / 2;
        } else if (yAlign === "bottom") {
          top -= space;
        }
        if (xAlign === "left") {
          left = left + width / 2 - space / 2;
          if (yAlign === "center") {
            left = left + space * 2;
          }
        } else if (xAlign === "right") {
          left -= width / 2;
          if (yAlign === "center") {
            left = left - space;
          } else {
            left += space;
          }
        }
        if(left < 0) left = 0;
        if(left > window.innerWidth - width) left = window.innerWidth - width;

        tooltipEl.style.top = `${top}px`;
        tooltipEl.style.left = `${left}px`;
    }

    static decorateChartData(data, config)
    {
        for (let i = 0; i < data.datasets.length; i++)
        {
            const color = SC2Restful.getPredefinedOrRandomColor(data.customColors[i], i);
            if (config.type === "lineVCursor" || config.type === "line")
            {
                data.datasets[i]["borderWidth"] = 2;
                data.datasets[i]["pointRadius"] = config.performance == "fast"
                    ? ChartUtil.drawOnlyImagePoints
                    : (config.pointRadius != null ? parseFloat(config.pointRadius) : 0.01);
                data.datasets[i]["hoverPointRadius"] = 2;

                data.datasets[i]["borderColor"] = color;
                data.datasets[i]["pointBackgroundColor"] = color;
                //data.datasets[i]["pointBorderColor"] = SC2Restful.COLORS.get(data.customColors[i]);
                data.datasets[i]["backgroundColor"] = "rgba(0, 0, 0, 0)";
            }
            else if(config.type === "doughnut" || config.type === "pie")
            {
                const dataColors = [];
                const dataEmptyColors = [];
                for(let dataValIx = 0; dataValIx < data.datasets[i].data.length; dataValIx++)
                {
                    const color = SC2Restful.getPredefinedOrRandomColor(data.customColors[dataValIx], dataValIx);
                    dataColors.push(color);
                    dataEmptyColors.push("rgba(0, 0, 0, 0)");
                }
                data.datasets[i]["backgroundColor"] = dataColors;
                data.datasets[i]["borderColor"] = dataEmptyColors;
            }
            else
            {
                data.datasets[i]["backgroundColor"] = color;
                data.datasets[i]["borderColor"] = "rgba(0, 0, 0, 0)";
            }
        }
    }

    static drawOnlyImagePoints(context, options)
    {
        return options ? (options.pointStyle ? 0.5 : 0) : 0.5;
    }

    static collectChartJSData(elem)
    {
        const type = elem.getAttribute("data-chart-type");
        const stacked = elem.getAttribute("data-chart-stacked");
        const direct = elem.getAttribute("data-chart-direct");
        const tableData = direct === "true"
            ? ChartUtil.CHART_RAW_DATA.get(elem.id).data
            : TableUtil.collectTableData(elem);
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
                        hidden: !Util.hasNonZeroValues(tableData.values[i]),
                        ...(tableData.pointStyles) && {pointStyle: tableData.pointStyles[i]}
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
                id: elem.id,
                type: type,
                headers: elem.getAttribute("data-chart-tooltip-table-headers")
                    ? elem.getAttribute("data-chart-tooltip-table-headers").split(",")
                    : []
            }
        }
        return data;
    }

    static updateChart(chart, data)
    {
        if (data === null) return;

        chart.data.labels.length = 0;
        chart.data.datasets.length = 0;
        data.labels.forEach(l=>chart.data.labels.push(l));
        data.datasets.forEach(d=>chart.data.datasets.push(d));
        ChartUtil.decorateChartData(data, chart.customConfig);
        if(chart.customConfig.isZoomed)
            ChartUtil.resetZoom({target: document.querySelector("#chart-zoom-ctl-" + chart.customConfig.chartable)});
        chart.update();
        ChartUtil.updateChartZoomLimits(chart);
    }

    static updateChartZoomLimits(chart)
    {
        if(!chart.customConfig.zoom) return;

        chart.options.plugins.zoom.limits.x.min = chart.scales.x.min;
        chart.options.plugins.zoom.limits.x.max = chart.scales.x.max;
        chart.options.plugins.zoom.limits.y.min = chart.scales.y.min;
        chart.options.plugins.zoom.limits.y.max = chart.scales.y.max;
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

    static updateChartableTab(tab)
    {
        const chartables = document.querySelectorAll(tab.getAttribute("data-target") + " .chartable");
        if (chartables.length == 0) return;

        const content = document.querySelector(tab.getAttribute("data-target"));
        const updatedMax = ChartUtil.getChartableTabUpdatedMax(tab);
        if(updatedMax == 0 || updatedMax == content.getAttribute("data-chartable-last-updated")) return;

        for(const chartable of chartables)
        {
            if(chartable.getAttribute("data-last-updated") != null) ChartUtil.updateChartable(chartable);
        }
        ChartUtil.linkChartTabsHeight(document.getElementById(chartables[0].getAttribute("data-chart-id")));

        content.setAttribute("data-chartable-last-updated", updatedMax);
    }

    static getChartableTabUpdatedMax(tab)
    {
        var max = 0;
        for(const chartable of document.querySelectorAll(tab.getAttribute("data-target") + " .chartable"))
            max = Math.max(max, chartable.getAttribute("data-last-updated"));
        return max;
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
            if(mutation.target.closest(".tab-pane").classList.contains("active"))
                ChartUtil.updateChartable(mutation.target);
        }
    }

    static observeCharts()
    {
        for(const chart of document.querySelectorAll(".c-chart")) ChartUtil.CHART_OBSERVER.observe(chart, ChartUtil.CHART_OBSERVER_CONFIG);
    }

    static onChartMutation(mutations, observer)
    {
        for(const mutation of mutations)
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

    static enhanceZoomToggles()
    {
        document.querySelectorAll(".chart-zoom-toggle").forEach(t=>{
            const chartable = document.getElementById(t.getAttribute("data-chartable"));
            ChartUtil.changeZoomState(chartable, t.checked);
            t.addEventListener("change", e=>{
                const chartable = document.getElementById(e.target.getAttribute("data-chartable"));
                ChartUtil.changeZoomState(chartable, t.checked);
            });
        });
    }

    static changeZoomState(chartable, zoom)
    {
        const chart = ChartUtil.CHARTS.get(chartable.id);
        if(!zoom) {
            if(chart) {
                chart.options.scales.y.beginAtZero = true;
                chart.options.scales.y.min = chart.customConfig.yMin;
                chart.options.scales.y.max = chart.customConfig.yMax;
            } else {
                chartable.setAttribute("data-chart-begin-at-zero", "true");
            }
        }
        else {
            if(chart) {
                chart.options.scales.y.beginAtZero = false;
                chart.options.scales.y.min = undefined;
                chart.options.scales.y.max = undefined;
            } else {
                chartable.setAttribute("data-chart-begin-at-zero", "false");
            }
        }
        if(chart) {
            chart.update();
            ChartUtil.resetZoom({target: document.querySelector("#chart-zoom-ctl-" + chartable.id)});
            ChartUtil.updateChartZoomLimits(chart);
        }
    }

    static enhanceHeightControls()
    {
        const handler = e=>window.setTimeout(ChartUtil.updateAspectRatioFromLocalStorage, 1);
        document.querySelector("#chart-height-high").addEventListener("click", handler);
        document.querySelector("#chart-height-medium").addEventListener("click", handler);
        document.querySelector("#chart-height-low").addEventListener("click", handler);
    }

    static updateAspectRatioFromLocalStorage()
    {
        if(localStorage.getItem("chart-height-high") == "true") {
            ChartUtil.ASPECT_RATIO = 2;
        } else if(localStorage.getItem("chart-height-low") == "true") {
            ChartUtil.ASPECT_RATIO = 4;
        } else {
            ChartUtil.ASPECT_RATIO = 2.5;
        }
        ChartUtil.updateAspectRatio();
    }

    static updateAspectRatio()
    {
        for(const chart of ChartUtil.CHARTS.values()) {
            chart.config.options.aspectRatio = ChartUtil.ASPECT_RATIO;
            chart.aspectRatio = ChartUtil.ASPECT_RATIO;
            chart.update();
        }
    }

}

ChartUtil.CHARTS = new Map();
ChartUtil.CHART_RAW_DATA = new Map();

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
ChartUtil.ASPECT_RATIO = 2.5;

class ChartLineVCursor extends Chart.LineController
{
    draw()
    {
        super.draw(arguments);
        if (this.chart.tooltip._active && this.chart.tooltip._active.length)
        {
            var activePoint = this.chart.tooltip._active[0],
            ctx = this.chart.ctx,
            x = activePoint.element.x,
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
            ctx.strokeStyle = Session.theme == THEME.DARK ? '#d3d3d3' : "black";
            ctx.stroke();
            ctx.restore();
        }
    }
}
ChartLineVCursor.id = "lineVCursor";
ChartLineVCursor.defaults = Chart.LineController.defaults;
Chart.register(ChartLineVCursor);
