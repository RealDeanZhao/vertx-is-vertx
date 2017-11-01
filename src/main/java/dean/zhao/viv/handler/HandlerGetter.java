package dean.zhao.viv.handler;

public class HandlerGetter {
    public static FnHandler getHandler(final Class clazz) {
        try {
            final AbstractHandler yo = (AbstractHandler) clazz.newInstance();
            return yo.handler;
        } catch (final Exception e) {
            return null;
        }
    }
}
