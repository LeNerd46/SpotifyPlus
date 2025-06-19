console.log("Trying to subscribe to event now...");

events.subscribe("settingsOpened", (data) => {
    console.log("Settings page opened!");
});

const itemOne = new SettingItem("Item One", "This is the first item in the settings page", "Toggle");
const itemTwo = new SettingItem("Item Two", "This is the second item in the settings page", "Slider");

const section = new SettingSection("Script Test", [itemOne, itemTwo]);
section.register("Script Test");