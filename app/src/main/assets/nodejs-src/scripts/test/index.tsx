import React from "react";
import App from "./test-overlay";

//@ts-expect-error
SpotifyPlus.Surfaces.register('lyrics-view', (surface: any) => {
    return <App />;
});

//@ts-expect-error
const sideDrawer = new SpotifyPlus.SideDrawer('React Item!', () => {
    return <App />;
}).register();