console.log("Trying to subscribe to event now...");

events.subscribe("settingsOpened", (data) => {
    console.log("Settings page opened!");
});