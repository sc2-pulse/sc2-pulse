class Buffer
{

    constructor(toggleClass, attribute, itemGetter, updateModel, updateView)
    {
        this.toggleClass = toggleClass;
        this.attribute = attribute;
        this.updateModel = updateModel;
        this.updateView = updateView;
        this.itemGetter = itemGetter;
        this.buffer = new Map();
    }

    update()
    {
        this.updateModel(this);
        this.updateView(this);
    }

    add(item)
    {
        this.buffer.set(item.id, item);
        this.update();
    }

    remove(item)
    {
        this.buffer.delete(item.id);
        this.update();
    }

    clear()
    {
        this.buffer.clear();
        document.querySelectorAll("." + this.toggleClass + '.remove').forEach(e=>e.classList.replace("remove", "add"));
        this.update();
    }

    static toggle(buf, evt)
    {
        const item = buf.itemGetter(evt.target);
        const remove = evt.target.classList.contains("remove");
        const selector = '[' + buf.attribute + '="' + item.id + '"] .' + buf.toggleClass;
        if(remove) {
            buf.remove(item);
            document.querySelectorAll(selector).forEach(e=>{
                e.classList.remove("remove");
                e.classList.add("add");
            });
        } else {
            buf.add(item);
            document.querySelectorAll(selector).forEach(e=>{
                e.classList.add("remove");
                e.classList.remove("add");
            });
        }
    }

}
