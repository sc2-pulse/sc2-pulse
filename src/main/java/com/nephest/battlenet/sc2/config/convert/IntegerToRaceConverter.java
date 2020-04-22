package com.nephest.battlenet.sc2.config.convert;

import com.nephest.battlenet.sc2.model.Race;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class IntegerToRaceConverter
implements Converter<Integer, Race>
{

    @Override
    public Race convert(Integer id)
    {
        return Race.from(id);
    }

}
