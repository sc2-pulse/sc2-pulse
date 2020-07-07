// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class RichDataUtil
{

    static generateDataSet(params)
    {
        const script = document.createElement("script");
        script.setAttribute("type", "application/ld+json");
        const content =
           `{
                 "@context":"https://schema.org/",
                 "@type":"Dataset",
                 "name":"StarCraft2 ${ElementUtil.generateLadderTitle(params, false)}",
                 "description":"StarCraft2 ${ElementUtil.generateLadderDescription(params, false)}
                      Parameters: player count, team count, games played.
                      The first data point is season 29. Dataset is compiled from BattleNet API ladders.",
                 "url": "${window.location.protocol}//${window.location.host}${ROOT_CONTEXT_PATH}?${params.toString()}",
                 "keywords": "StarCraft2, StarCraft II, stats, distribution",
                 "license" : "https://creativecommons.org/publicdomain/zero/1.0/",
                 "creator":{
                    "@type": "Person",
                    "givenName": "Oleksandr",
                    "familyName": "Masniuk",
                    "name": "Oleksandr Masniuk"
                 },
                "temporalCoverage": "2016-10-18/..",
                "distribution":{
                     "@type":"DataDownload",
                     "encodingFormat":"JSON",
                     "contentUrl":"${window.location.protocol}//${window.location.host}${ROOT_CONTEXT_PATH}api/ladder/stats?${params.toString()}"
                }
            }`
        ;
        script.appendChild(document.createTextNode(content));
        return script;
    }

    static enrich(params)
    {
        for(rich of document.querySelectorAll('*[type="application/ld+json"]')) rich.parentElement.removeChild(rich);
        const tabs = params.getAll("t");
        if(tabs.includes("stats-region") || tabs.includes("stats-league") || tabs.includes("stats-race") || tabs.includes("stats-global"))
            document.querySelector("head").appendChild(RichDataUtil.generateDataSet(params));
    }

}