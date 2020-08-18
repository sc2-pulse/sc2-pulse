// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class Model
{

    static init()
    {
        for(const view of Object.values(VIEW)) Model.DATA.set(view, new Map());
    }

}

Model.DATA = new Map();

