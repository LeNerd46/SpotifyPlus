"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
var spotifyplus_1 = require("spotifyplus");
var app_1 = require("./app");
spotifyplus_1.SpotifyPlus.log('This script is running!');
spotifyplus_1.SpotifyPlus.Surfaces.register('lyrics-view', function (surface) {
    return <app_1.default />;
});
