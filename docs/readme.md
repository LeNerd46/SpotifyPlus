# Introduction

Welcome to the documentation for the scripting API of the SpotifyPlus Xposed module. This API enables developers to extend and customize Spotify's functionality by writing custom scripts. With this API, you can modify app behavior and create powerful customizations.

This guide provides an overview of the available methods, properties, and examples to help you get started. Whether you're a seasoned developer or new to scripting, this documentation will serve as your reference for building with the SpotifyPlus API.

## Setup
In order to get started, just create a new file. For this tutorial, I will be using TypeScript. Just set up TypeScript as you normally would, but make sure in your `tsconfig.json` file to set the language to ES5. 

All scripts get loaded on app start, and will execute from the top automatically. For example, if you wanted to log saying that your script is loaded, this is all you would need to add

```typescript
Debug.log("Script loaded!");
```

This would log to the console:
```
[SpotifyPlus] [index.js] Script loaded!
```

From this point, you can start subscribing to events to load whatever you want your script to do