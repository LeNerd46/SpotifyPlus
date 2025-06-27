let savedTracks: string[] = [];

if(Storage.exists("bookmarks.json")) {
    savedTracks = JSON.parse(Storage.read("bookmarks.json"));
}

const menuItem = new ContextMenuItem("Add to Bookmarks", MediaType.Track, function(id: string) {
    // Spotify is very inconsistent with their capitalization in the context menu, not sure how to capitalize mine
    savedTracks.push(id);
    Storage.write("bookmarks.json", JSON.stringify(savedTracks, null, 2));
});

menuItem.register();
// const ui: ScriptUI = new ScriptUI("bookmarks", "com.lenerd46.bookmarksscript");

const button: SideDrawerItem = new SideDrawerItem("Bookmarks", function() {
    // ui.show("bookmark_page");
    savedTracks.forEach(item => {
        console.log(item);
    });
});

button.register();
