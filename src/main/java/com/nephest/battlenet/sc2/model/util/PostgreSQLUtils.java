package com.nephest.battlenet.sc2.model.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PostgreSQLUtils
{

    private final JdbcTemplate template;

    public PostgreSQLUtils(@Autowired JdbcTemplate template)
    {
        this.template = template;
    }

    public static String escapeLikePattern(String pattern)
    {
        return pattern.replaceAll("\\\\", "\\\\\\\\")
            .replaceAll("%", "\\\\%")
            .replaceAll("_", "\\\\_");
    }

    public void analyze()
    {
        template.execute("ANALYZE");
    }

    public void vacuum()
    {
        template.execute("VACUUM");
    }

}
