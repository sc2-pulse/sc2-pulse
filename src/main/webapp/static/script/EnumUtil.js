// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class EnumUtil
{

    static enumOfId(id, enumObj)
    {
        for(const curEnum of Object.values(enumObj)) if(curEnum.code == id) return curEnum;
        throw new Error("Invalid id");
    }

    static enumOfName(name, enumObj, throwError = true)
    {
        name = name.toLowerCase();
        for(const curEnum of Object.values(enumObj)) if(curEnum.name.toLowerCase() == name) return curEnum;
        if(throwError) throw new Error("Invalid name");
        return null;
    }

    static enumOfFullName(fullName, enumObj)
    {
        fullName = fullName.toLowerCase();
        for(const curEnum of Object.values(enumObj)) if(curEnum.fullName.toLowerCase() == fullName) return curEnum;
        throw new Error("Invalid full name");
    }

    static enumOfStoredFullName(itemName, enumObj, defaultValue)
    {
        const fullName = localStorage.getItem(itemName);
        if(fullName == null) return defaultValue;

        return EnumUtil.enumOfFullName(fullName, enumObj);
    }

    static enumOfNamePrefix(prefix, enumObj)
    {
        prefix = prefix.toLowerCase();
        for(const curEnum of Object.values(enumObj)) if(curEnum.name.toLowerCase().startsWith(prefix)) return curEnum;
        throw new Error("Invalid name");
    }

    static getMemberCount(teamFormat, teamType)
    {
        if(teamType === TEAM_TYPE.RANDOM) return 1;
        return teamFormat.memberCount;
    }

}
