package dean.zhao.viv.handler;

public abstract class AbstractHandler {
    FnHandler handler;

    AbstractHandler() {
        setHandler();
    }

    abstract void setHandler();


}
