"use strict";
var savedTracks = [];
if (Storage.exists("bookmarks.json")) {
    savedTracks = JSON.parse(Storage.read("bookmarks.json"));
}
var menuItem = new ContextMenuItem("Add to Bookmarks", "track", function (id) {
    // Spotify is very inconsistent with their capitalization in the context menu, not sure how to capitalize mine
    savedTracks.push(id);
    Storage.write("bookmarks.json", JSON.stringify(savedTracks, null, 2));
});
menuItem.register();
// const ui: ScriptUI = new ScriptUI("bookmarks", "com.lenerd46.bookmarksscript");
var button = new SideDrawerItem("Bookmarks", function () {
    // ui.show("bookmark_page");
    savedTracks.forEach(function (item) {
        console.log(item);
    });
});
button.register();
