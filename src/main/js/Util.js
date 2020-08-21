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

    static setGeneratingStatus(status, errorText = "Error")
    {
        switch(status)
        {
            case "begin":
                Session.currentRequests++;
                if (Session.currentRequests > 1) return;
                ElementUtil.setElementsVisibility(document.getElementsByClassName("status-generating-begin"), true);
                ElementUtil.setElementsVisibility(document.getElementsByClassName("status-generating-success"), false);
                ElementUtil.setElementsVisibility(document.getElementsByClassName("status-generating-error"), false);
            break;
            case "success":
            case "error":
                Session.currentRequests--;
                if(status === "error")
                {
                    document.getElementById("error-generation-text").textContent = errorText;
                    $("#error-generation").modal();
                }
                if(Session.currentRequests > 0) return;
                ElementUtil.setElementsVisibility(document.getElementsByClassName("status-generating-begin"), false);
                ElementUtil.setElementsVisibility(document.getElementsByClassName("status-generating-" + status), true);
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

    static isBarcode(name)
    {
        return  Util.BARCODE_REGEX.test(name);
    }

    static unmaskBarcode(character, account)
    {
        const name = character.name.substring(0, character.name.indexOf("#"));
        return Util.isBarcode(name)
            ? account.battleTag.substring(0, account.battleTag.indexOf("#"))
            : name;
    }

    static isUndefinedRank(rank)
    {
        return rank == SC2Restful.UNDEFINED_RANK;
    }

    static escapeHtml(string)
    {
        return String(string).replace(/[&<>"'`=\/]/g, function (s) {
            return entityMap[s];
        });
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
