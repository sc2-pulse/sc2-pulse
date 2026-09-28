package com.nephest.battlenet.sc2.model.navigation;

import java.util.List;
import org.springframework.data.domain.Window;

public record WindowWithPayload<W, P>
(
    Window<W> window,
    P payload
)
{

    private static final WindowWithPayload<Object, Object> EMPTY =
        new WindowWithPayload<Object, Object>(CursorUtil.emptyWindow(), null);

    @SuppressWarnings("unchecked")
    public static <W, P> WindowWithPayload<W, P> empty()
    {
        return (WindowWithPayload<W, P>) EMPTY;
    }

    public static <W> WindowWithPayload<W, List<W>> from(Window<W> window)
    {
        return new WindowWithPayload<>(window, window.getContent());
    }

}
