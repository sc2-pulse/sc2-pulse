// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

class MetaUtil
{

    static getPatches(buildMin)
    {
        const request = `${ROOT_CONTEXT_PATH}api/patches?buildMin=${encodeURIComponent(buildMin)}`;
        return Session.beforeRequest()
           .then(n=>fetch(request))
           .then(resp=>Session.verifyJsonResponse(resp, [200, 404]));
    }

    static loadPatches()
    {
        let patches = JSON.parse(localStorage.getItem("internal-meta-patches") || "[]");
        const buildMin = parseInt(localStorage.getItem("internal-meta-patches-build-last") || -1) + 1;
        return MetaUtil.getPatches(buildMin)
            .then(newPatches=>{
                if(newPatches && newPatches.length > 0) {
                    patches = newPatches.concat(patches);
                    localStorage.setItem("internal-meta-patches", JSON.stringify(patches));
                }
                if(patches.length > 0) localStorage.setItem("internal-meta-patches-build-last", patches[0].patch.build);
                MetaUtil.PATCHES = patches;
                return patches;
            });
    }

    static loadPatchesIfNeeded()
    {
        MetaUtil.resetPatchesIfNeeded();
        if(PATCH_LAST_BUILD == 0) return Promise.resolve();
        if(localStorage.getItem("internal-meta-patches-build-last") < PATCH_LAST_BUILD) return MetaUtil.loadPatches();

        if(MetaUtil.PATCHES.length == 0) MetaUtil.PATCHES =
            JSON.parse(localStorage.getItem("internal-meta-patches") || "[]");
        return Promise.resolve(MetaUtil.PATCHES);
    }

    static resetPatchesIfNeeded()
    {
        const lastReset = localStorage.getItem("internal-meta-patches-reset");
        if(!lastReset || Date.now() - lastReset > MetaUtil.PATCH_RESET_PERIOD) {
            MetaUtil.PATCHES = [];
            localStorage.removeItem("internal-meta-patches-build-last");
            localStorage.removeItem("internal-meta-patches");
            localStorage.setItem("internal-meta-patches-reset", Date.now());
        }
    }

}

MetaUtil.PATCHES = [];
MetaUtil.PATCH_RESET_PERIOD = 60 * 60 * 24 * 7 * 1000;
