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

    static getFormData(page = 0, form)
    {
        if(!form) form = document.getElementById("form-ladder");
        const fd = new FormData(form);
        if (page >= 0) fd.set("page", page);
        return fd;
    }

    static getFormParameters(page = 0, form)
    {
        return Util.urlencodeFormData(Util.getFormData(page, form));
    }

    static mapToUrlSearchParams(map)
    {
        const params = new URLSearchParams();
        for(const [key, vals] of map instanceof Map ? map.entries() : Object.entries(map))
            for(const val of vals) params.append(key, val);
        return params;
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
                    Util.showGlobalError(error != null ? error : {message: errorText});
                }
                if(Session.currentRequests > 0) return;
                ElementUtil.setElementsVisibility(document.getElementsByClassName("status-generating-begin"), false);
                ElementUtil.setElementsVisibility(document.getElementsByClassName("status-generating-" + status.name), true);
                Session.isHistorical = false;
            break;
        }
    }

    static showGlobalError(error)
    {
        if(DEBUG == true) console.log(error);
        document.body.classList.add("js-error-detected");
        document.getElementById("error-generation-text").textContent = Util.ERROR_MESSAGES.get(error.message.trim()) || error.message;
        if(!Session.isSilent) $("#error-generation").modal();
    }

    static successStatusPromise(e)
    {
        Util.setGeneratingStatus(STATUS.SUCCESS);
        return Promise.resolve(e);
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

    static calculateProgress(min, max, val)
    {
        return (Math.abs(val - min) / Math.abs(max - min)) * 100;
    }

    static stDev(arr, usePopulation = false)
    {
        const mean = arr.reduce((acc, val) => acc + val, 0) / arr.length;
        return Math.sqrt(
            arr.reduce((acc, val) => acc.concat((val - mean) ** 2), []).reduce((acc, val) => acc + val, 0)
            / (arr.length - (usePopulation ? 0 : 1))
        );
    };

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

    static kebabCaseToCamelCase(str)
    {
        return str.toLowerCase().replace(/([-][a-z])/g, group=>group.toUpperCase().replace('-', ''));
    }

    static camelCaseToKebabCase(str)
    {
        return str.replace(/[A-Z]+(?![a-z])|[A-Z]/g, ($, ofs)=>(ofs ? "-" : "") + $.toLowerCase());
    }

    static snakeCaseToCamelCase(str)
    {
        return str.toLowerCase().replace(/[-_][a-z]/g, (group) => group.slice(-1).toUpperCase());
    }

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
        return !rank;
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

    static parseIsoDateOrDateTime(str)
    {
        return str.length == Util.ISO_DATE_STRING_LENGTH ? Util.parseIsoDate(str) : Util.parseIsoDateTime(str);
    }

    static currentISODateString()
    {
        return new Date().toISOString().substring(0, 10);
    }

    static currentISODateTimeString()
    {
        return new Date().toISOString();
    }

    static forObjectValues(obj, func)
    {
        for(const [key, val] of Object.entries(obj))
        {
            if(val == null) continue;

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

    static groupByObject(list, pathGetter)
    {
        const result = {};
        list.forEach((item) =>
        {
            const path = pathGetter(item);
            let root = result;
            path.forEach(pathPart=>{
                let nextNode = root[pathPart];
                if(!nextNode) {
                    nextNode = {};
                    root[pathPart] = nextNode;
                }
                root = nextNode;
            });
            if(root.values == null) root.values = [];
            root.values.push(item);
        });
        return result;
    }

    static emptyClone(src)
    {
        const clone = {};
        for(const key of Object.keys(src)) clone[key] = null;
        return clone;
    }

    static addObjects(objects, propertyNames = null)
    {
        if(propertyNames == null) propertyNames = Array.from(Object.keys(objects[0]));
        const sum = {};
        propertyNames.forEach(name=>sum[name] = null);
        objects.forEach(obj=>propertyNames.forEach(name=>sum[name] += obj[name]));
        return sum;
    }

    static addObjectColumns(objects2DArray, propertyNames = null)
    {
        if(objects2DArray.length == 0) return [];

        if(propertyNames == null) propertyNames = Array.from(Object.keys(objects2DArray[0][0]));
        const emptyObject = {};
        propertyNames.forEach(name=>emptyObject[name] = null);
        const result = new Array(objects2DArray[0].length);
        for(let i = 0; i < result.length; i++) {
            const objectGroup = objects2DArray.map(row=>row[i] ? row[i] : emptyObject);
            result[i] = Util.addObjects(objectGroup, propertyNames)
        }
        return result;
    }

    static mergeObjects(objects, propertyNames, factor)
    {
        const merged = new Array(Math.ceil(objects.length / factor));
        for(let i = 0; i < merged.length; i++)
            merged[i] = Util.addObjects(objects.slice(i * factor, Math.min(i * factor + factor, objects.length)), propertyNames);
        return merged;
    }

    static concatObject(src, dest)
    {
        for(const [name, val] of Object.entries(src)) {
            if(Array.isArray(val)) {
                if(dest[name] == null) dest[name] = [];
                dest[name] = dest[name].concat(val);
            } else if(typeof val === "object") {
                if(dest[name] == null) dest[name] = {};
                for(const [valName, valVal] of Object.entries(val))
                    dest[name][valName] = valVal;
            } else {
                dest[name] = val;
            }
        }
    }

    static toMap(items, keyMapper, valueMapper=(item)=>item)
    {
        const map = new Map();
        items.forEach((item) =>{
            map.set(keyMapper(item), valueMapper(item));
        });
        return map;
    }

    static addAllCollections(src, dest)
    {
        for(const [objKey, objVal] of Object.entries(src)) {
            if(objVal instanceof Map) {
                if(!dest[objKey]) dest[objKey] = new Map();
                for(const [mapKey, mapVal] of objVal.entries()) dest[objKey].set(mapKey, mapVal);
            } else if(objVal instanceof Array) {
                if(!dest[objKey]) dest[objKey] = [];
                dest[objKey] = dest[objKey].concat(objVal);
            }
        }
        return dest;
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

    static divideColor(color, divisor)
    {
        const colors = color.substring(color.indexOf("(") + 1, color.length - 1).split(",");
        for(let i = 0; i < 3; i++)
            colors[i] = Math.round(colors[i].trim() / divisor)
        return (colors.length == 4 ? "rgba(" : "rgb(") + colors.join() + ")";
    }

    static addCsrfHeader(options)
    {
        if(!options.headers) options.headers = {};
        options.headers["X-XSRF-TOKEN"] = Util.getCookie("XSRF-TOKEN");
        return options;
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

    static cloneObject(src)
    {
        const dest = {};
        return Object.assign(dest, src);
    }

    static addParams(params, name, vals)
    {
        if(!vals) return;
        for(const val of vals) params.append(name, val);
    }

    static convertFakeName(member, name)
    {
        return name == FAKE_NAME ? member.character.id : name;
    }

    static reload(id, ifLoaded = true)
    {
        ElementUtil.INPUT_TIMEOUTS.set(id, window.setTimeout(e=>{
                if(!ifLoaded || Session.currentRequests < 1) {
                    document.location.reload();
                } else {
                    Util.reload(id);
                }
            }, SC2Restful.REDIRECT_PAGE_TIMEOUT_MILLIS)
        );
    }

    static rectContains(rect, x, y)
    {
        return rect.x <= x && x <= rect.x + rect.width
            && rect.y <= y && y <= rect.y + rect.height;
    }

    static countryCodeToEmoji(iso)
    {
        const codePoints = [...iso].map(c=>c.codePointAt() + 127397);
        return String.fromCodePoint(...codePoints);
    }

    static load(container, lazyPromise, showErrors = false)
    {
        return ElementUtil.executeTask(container.id, ()=>Util.doLoad(container, lazyPromise, showErrors));
    }

    static doLoad(container, lazyPromise, showErrors = false)
    {
        if(container.classList.contains(LOADING_STATUS.COMPLETE.className)
            || container.classList.contains(LOADING_STATUS.IN_PROGRESS.className)) return Promise.resolve();

        ElementUtil.setLoadingIndicator(container, LOADING_STATUS.IN_PROGRESS);
        return lazyPromise()
            .then(result=>{
                ElementUtil.setLoadingIndicator(container, result.status);
                if(result.status != LOADING_STATUS.COMPLETE) {
                    const infiniteScrollElem = container.querySelector(":scope .indicator-loading-scroll-infinite");
                    if(infiniteScrollElem
                        && ElementUtil.isElementVisible(infiniteScrollElem)
                        && ElementUtil.isElementInViewport(infiniteScrollElem))
                            return Util.load(container, lazyPromise);
                }
            })
            .catch(error=>{
                ElementUtil.setLoadingIndicator(container, LOADING_STATUS.ERROR);
                if(DEBUG == true && !showErrors) console.log(error);
                if(showErrors) Util.showGlobalError(error);
            });
    }

    static resetLoadingIndicatorTree(container)
    {
        Util.resetLoadingIndicator(container);
        Util.resetNestedLoadingIndicators(container);
    }

    static resetNestedLoadingIndicators(container)
    {
        container.querySelectorAll(".container-loading").forEach(Util.resetLoadingIndicator);
    }

    static resetLoadingIndicator(container)
    {
        return ElementUtil.executeTask(container.id, ()=>ElementUtil.setLoadingIndicator(container, LOADING_STATUS.NONE));
    }

    static getAllSettledLoadingStatus(results, fulfilledStatus = LOADING_STATUS.COMPLETE)
    {
        return results.some(result=>result.status === "rejected") ? LOADING_STATUS.ERROR : fulfilledStatus;
    }

    static throwFirstSettledError(results)
    {
        const reason = results.map(result=>result.reason).find(reason=>reason != null);
        if(reason != null) {
            if(DEBUG == true) console.log(reason);
            throw new Error(reason);
        }
    }

    static getHrefUrlSearchParams(element)
    {
        const href = element.getAttribute("href");
        const paramIx = href.indexOf("?");
        if(paramIx == -1) return new URLSearchParams();

        const hashIx = href.indexOf("#");
        return new URLSearchParams(href.substring(paramIx, hashIx > 0 ? hashIx : href.length));
    }

    static deleteSearchParams(params, names = ["type", "m"])
    {
        for(const name of names) params.delete(name);
        return params;
    }

    static getPreferredLanguages()
    {
        return window.navigator.languages || [window.navigator.language || window.navigator.userLanguage];
    }

    static parseMatchUp(matchUp)
    {
        const prefixes = matchUp.split("v");
        return [EnumUtil.enumOfNamePrefix(prefixes[0], RACE), EnumUtil.enumOfNamePrefix(prefixes[1], RACE)];
    }

    static isErrorDetails(json)
    {
        return json != null && json.status != null && json.type != null;
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
Util.SECURE_URI_REGEX = new RegExp(/^(?!.*[%;/\\])(?!^(\.)\1*$).*$/);
Util.NUMBER_FORMAT = new Intl.NumberFormat(navigator.language);
Util.NUMBER_FORMAT_DIFF = new Intl.NumberFormat(navigator.language, {signDisplay: "exceptZero"});
Util.DECIMAL_FORMAT = new Intl.NumberFormat(navigator.language, {minimumFractionDigits: 2, maximumFractionDigits: 2});
Util.MONTH_DATE_FORMAT = new Intl.DateTimeFormat(navigator.language, {month: "2-digit", year: "numeric"});
Util.DATE_FORMAT = new Intl.DateTimeFormat(navigator.language, {day: "2-digit", month: "2-digit", year: "numeric"});
Util.DATE_TIME_FORMAT = new Intl.DateTimeFormat(navigator.language, {day: "2-digit", month: "2-digit", year: "numeric",
    hour: "2-digit", minute: "2-digit", second: "2-digit"});
Util.DAY_MILLIS = 86400000;
Util.ISO_DATE_STRING_LENGTH = 10;
Util.ERROR_MESSAGES = new Map([
    ["409", "409 Conflict. "
        + "The entity has already been modified by someone else. "
        + "Please reload the entity and verify changes."]
]);
Util.LANGUAGE_NAMES = new Intl.DisplayNames([], { type: "language" });
