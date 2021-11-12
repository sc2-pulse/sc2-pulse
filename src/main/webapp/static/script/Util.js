// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class Util
{

    static urlencodeFormData(fd)
    {
        let s = '';

        for(const pair of fd.entries()){
           if(typeof pair[1]=='string'){
               s += (s?'&':'') + Util.encodeSpace(pair[0])+'='+ Util.encodeSpace(pair[1]);
           }
        }
        return s;
    }

    static getFormParameters(page = 0)
    {
        const fd = new FormData(document.getElementById("form-ladder"));
        if (page >= 0) fd.set("page", page);
        return Util.urlencodeFormData(fd);
    }

    static setGeneratingStatus(status, errorText = "Error", error = null)
    {
        switch(status)
        {
            case STATUS.BEGIN:
                Session.currentRequests++;
                if (Session.currentRequests > 1) return;
                ElementUtil.setElementsVisibility(document.getElementsByClassName("status-generating-begin"), true);
                ElementUtil.setElementsVisibility(document.getElementsByClassName("status-generating-success"), false);
                ElementUtil.setElementsVisibility(document.getElementsByClassName("status-generating-error"), false);
            break;
            case STATUS.SUCCESS:
            case STATUS.ERROR:
                Session.currentRequests--;
                if(status === STATUS.ERROR)
                {
                    if(DEBUG == true) console.log(error);
                    document.getElementById("error-generation-text").textContent = errorText;
                    if(!Session.isSilent) $("#error-generation").modal();
                }
                if(Session.currentRequests > 0) return;
                ElementUtil.setElementsVisibility(document.getElementsByClassName("status-generating-begin"), false);
                ElementUtil.setElementsVisibility(document.getElementsByClassName("status-generating-" + status.name), true);
                Session.isHistorical = false;
            break;
        }
    }

    static getCookie(cname)
    {
        var name = cname + "=";
        var decodedCookie = decodeURIComponent(document.cookie);
        var ca = decodedCookie.split(';');
        for(var i = 0; i <ca.length; i++)
        {
            var c = ca[i];
            while (c.charAt(0) == ' ') c = c.substring(1);
            if (c.indexOf(name) == 0) return c.substring(name.length, c.length);
        }
        return "";
    }

    static compareValueArrays(a, b)
    {
        var compare = 0;
        for(var i = 0; i < a.length; i++)
        {
            compare = Util.compareValues(a[i], b[i]);
            if(compare !== 0) return compare;
        }
        return compare;
    }

    static compareValues(v1, v2)
    {
        return v1 !== '' && v2 !== '' && !isNaN(v1) && !isNaN(v2) ? v1 - v2 : v1.toString().localeCompare(v2);
    }

    static scrollIntoViewById(id)
    {
        document.getElementById(id).scrollIntoView();
    }

    static calculatePercentage(val, allVal)
    {
        return Math.round((val / allVal) * 100);
    }

    static hasNonZeroValues(values)
    {
        for (let i = 0; i < values.length; i++)
        {
            const val = values[i];
            if (!isNaN(val) && val != 0)
            {
                return true;
            }
        }
        return false;
    }

    static encodeSpace(s){ return encodeURIComponent(s).replace(/%20/g,'+'); }

    static calculateRank(searchResult, i)
    {
        return (searchResult.meta.page - 1) * searchResult.meta.perPage + i + 1;
    }

    static translateUnderscore(str)
    {
        return str.replace(/_/g, " ").trim();
    }

    static addStringTail(str, strs, tail)
    {
        const maxLen = Math.max(...strs.map(s=>s.length));
        return str + Array(maxLen - str.length).fill(tail).join("");
    }

    static needToUnmaskName(name, proNickname, battleTag)
    {
        return proNickname != null || (Util.BARCODE_REGEX.test(name) && !Util.isFakeBattleTag(battleTag));
    }

    static unmaskName(member)
    {
        const maskedName = member.character.name.substring(0, member.character.name.indexOf("#"));
        const maskedTeam = member.clan ? member.clan.tag : null;
        if(member.proNickname != null)
            return {
                maskedName: maskedName,
                maskedTeam: maskedTeam,
                unmaskedName: member.proNickname,
                unmaskedTeam: member.proTeam ? member.proTeam : maskedTeam
            };

        return {
            maskedName: maskedName,
            maskedTeam: maskedTeam,
            unmaskedName: Util.needToUnmaskName(maskedName, member.proNickname, member.account.battleTag)
                ? member.account.battleTag.substring(0, member.account.battleTag.indexOf("#"))
                : maskedName,
            unmaskedTeam: maskedTeam
        };
    }

    static isUndefinedRank(rank)
    {
        return rank == SC2Restful.UNDEFINED_RANK;
    }

    static isFakeBattleTag(btag)
    {
        return btag.startsWith("f#");
    }

    static escapeHtml(string)
    {
        return String(string).replace(/[&<>"'`=\/]/g, function (s) {
            return entityMap[s];
        });
    }

    static parseIsoDate(str)
    {
        const split = str.split("-");
        //-1 cause of Date constructor accepts month index but day number
        return new Date(split[0], split[1] - 1, split[2]);
    }

    static parseIsoDateTime(str)
    {
        return new Date(str);
    }

    static currentISODateString()
    {
        return new Date().toISOString().substring(0, 10);
    }

    static forObjectValues(obj, func)
    {
        for(const [key, val] of Object.entries(obj))
        {
            if(typeof val !== "object")
            {
                obj[key] = func(val);
            }
            else
            {
                Util.forObjectValues(obj[key], func);
            }
        }
        return obj;
    }

    static getCurrentPathInContext()
    {
        return "/" + window.location.pathname.substring(ROOT_CONTEXT_PATH.length);
    }

    static groupBy(list, keyGetter)
    {
        const map = new Map();
        list.forEach((item) =>
        {
             const key = keyGetter(item);
             const collection = map.get(key);
             if (!collection)
             {
                 map.set(key, [item]);
             } else
             {
                 collection.push(item);
             }
        });
        return map;
    }

    static getRandomRgbColorString()
    {
        const r = Math.floor(Math.random() * 255);
        const g = Math.floor(Math.random() * 255);
        const b = Math.floor(Math.random() * 255);
        return "rgb(" + r + "," + g + "," + b + ")";
    }

    static getTeamFormatAndTeamTypeString(teamFormat, teamType)
    {
        return teamFormat.name + (teamFormat == TEAM_FORMAT._1V1 ? "" : " " + teamType.secondaryName);
    }

    static isMobile()
    {
        return (navigator.maxTouchPoints || 'ontouchstart' in document.documentElement);
    }

    static formatDateTimes()
    {
        document.querySelectorAll(".datetime-iso").forEach(t=>{
            t.textContent = Util.DATE_TIME_FORMAT.format(Util.parseIsoDateTime(t.textContent));
            t.classList.remove("datetime-iso");
        });
    }

    static changeFullRgbaAlpha(color, alpha)
    {
        return color.startsWith("rgba") ? color.replace("1)", alpha + ")") : color;
    }

    static addCsrfHeader(options)
    {
        if(!options.headers) options.headers = {};
        options.headers["X-XSRF-TOKEN"] = Util.getCookie("XSRF-TOKEN");
        return options;
    }

    static updateCsrfForm(form)
    {
        form.querySelector(':scope [name="_csrf"]').value = Util.getCookie("XSRF-TOKEN");
    }

    static matchUpComparator(a, b)
    {
        const aRace = a.split("v");
        aRace[0] = EnumUtil.enumOfNamePrefix(aRace[0], RACE);
        aRace[1] = EnumUtil.enumOfNamePrefix(aRace[1], RACE);
        const bRace = b.split("v");
        bRace[0] = EnumUtil.enumOfNamePrefix(bRace[0], RACE);
        bRace[1] = EnumUtil.enumOfNamePrefix(bRace[1], RACE);
        const compareRace = aRace[0].order - bRace[0].order;
        if(compareRace != 0) return compareRace;
        return aRace[1].order - bRace[1].order;
    }

}

Util.HTML_ENTITY_MAP =
{
   '&': '&amp;',
   '<': '&lt;',
   '>': '&gt;',
   '"': '&quot;',
   "'": '&#39;',
   '/': '&#x2F;',
   '`': '&#x60;',
   '=': '&#x3D;'
 };
Util.BARCODE_REGEX = new RegExp("^[lI]+$");
Util.NUMBER_FORMAT = new Intl.NumberFormat(navigator.language);
Util.DECIMAL_FORMAT = new Intl.NumberFormat(navigator.language, {minimumFractionDigits: 2, maximumFractionDigits: 2});
Util.MONTH_DATE_FORMAT = new Intl.DateTimeFormat(navigator.language, {month: "2-digit", year: "numeric"});
Util.DATE_FORMAT = new Intl.DateTimeFormat(navigator.language, {day: "2-digit", month: "2-digit", year: "numeric"});
Util.DATE_TIME_FORMAT = new Intl.DateTimeFormat(navigator.language, {day: "2-digit", month: "2-digit", year: "numeric",
    hour: "2-digit", minute: "2-digit", second: "2-digit"});
Util.DAY_MILLIS = 86400000;
