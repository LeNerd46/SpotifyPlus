const button = new SideDrawerItem("Here I Am!", function() {
    console.log("I was opened!");

    const ui = new ScriptUI("test", "com.lenerd46.bookmarksscript");
    ui.show("test");
    ui.setImage("imageView", "library_add");

    ui.onClick("textThing", function() {
        ui.hide();
    });
});

button.register();

const item = new ContextMenuItem("Really Cool Button!", "track", function(uri) {
    console.log("Look at this really cool message! Here is the song: " + uri);
});

item.register();