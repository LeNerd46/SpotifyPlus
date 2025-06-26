/** Functions related to debugging */
declare namespace console {
    /**
     * Logs a message to both the Xposed log and the Android log
     * @param message The message to log
     */
    function log(message: string): void;
    /**
     * Logs a message to the Xposed log and the Android log with a warning level
     * @param message The warning to log
     */
    function warn(message: string): void;
    /**
     * Logs a message to the Xposed log and the Android log with an error level
     * @param message The error to log
     */
    function error(message: string): void;
}

/** Useful functions related to the player */
declare class SpotifyPlayer {
    /** Gets the currently playing track*/
    static getCurrentTrack(): SpotifyTrack;
}

/** Represents a Spotify Track */
declare class SpotifyTrack {
    /** The title of the track */
    title: string;
    /** The artist who created the track */
    artist: string;
    /** The album this song belongs to */
    album: string;
    /** The URI of the song (i.e. spotify:track:12M5uqx0ZuwkpLp5rJim1a) */
    uri: string;
}

/** Helper methods to store user preferences */
declare class Preferences {
    /** Gets a value from the stored preferences */
    get(property: string, defaultValue: any): any;

    /** Sets a value in the stored preferences */
    set(property: string, value: any): any;
}

/** Represents a section inside of your script's settings page */
declare class SettingSection {
    /**
     * Represents a section inside of your script's settings page
     * @param title The title of the section. This appears as a header in your script's settings page
     * @param items The items inside of the section
     */
    constructor(title: string, items: List<SettingItem>);

    /** The title of the section. This appears as a header in your script's settings page */
    title: string;
    /** The items inside of the section */
    items: List<SettingItem>;

    /** Adds your section to your script's settings page */
    register(): void;
}

/** Represents an item in a settings page */
declare class SettingItem {
    /**
     * Represents an item in a settings page
     * @param title The title that appears in the settings page
     * @param description The description that appears under the title
     * @param type The type of setting item it is
     */
    constructor(title: string, description: string, type: SettingType);
    
    /** The title that appears in the settings page */
    title: string;
    /** The description that appears under the title */
    description: string;
    /** The type of setting item it is */
    type: string;
    /** The value that the item holds */
    value: any;
    /** The minimum and maximum values, if this item is a slider */
    range: any;
    /** The available options to choose from, if this item is a dropdown */
    options: string[];
    /** Whether the option is enabled or not. If false, the user will not be able to modify the value, and it will appear grayed out */
    enabled: boolean;

    /**
     * Event callback for when the user changes the value of the item
     * @param value The new value of the item
     */
    onValueChanged: (value: any) => void;

    /**
     * Tells the module to handle value changes for you. It will automatically update the entry you tell it when the user changes the value
     * @param title The entry in the stored preferences
     * @param defaultValue The value to return if no entry was found
     */
    useDefaultHandling(title: string, defaultValue: any): void;
}

/** Represents a button in the side drawer */
declare class SideDrawerItem {
    /**
     * Represents a button in the side drawer
     * @param title The title of the button
     * @param callback Event callback for when the user presses your button
     */
    constructor(title: string, callback: () => void);

    /** The title of the button */
    title: string;
    
    /** Event callback for when the user presses your button */
    callback: () => void;
    /** Adds your button to the side drawer */
    register(): void;
}

/** Helper methods to create UI */
declare class ScriptUI {
    /**
     * Helpers methods to create UI
     * @param path The name of your script's UI APK
     * @param packageName The package name of your UI APK
     */
    constructor(path: string, packageName: string);

    /**
     * Shows your UI layout
     * @param resource The name of your layout
     */
    show(resource: string): void;

    /** Removes your UI from the root */
    hide(): void;

    /**
     * Sets the source for an ImageView
     * @param id The ID of the ImageView you want to set
     * @param resource The name of the image resource
     */
    setImage(id: string, resource: string): void;

    /**
     * Sets the event callback for when the user presses the button
     * @param resource The ID of the element to listen to clicks
     * @param callback Event callback for when the user presses the item
     */
    onClick(resource: string, callback: () => void): void;
}

/** Represents a item inside of a context menu */
declare class ContextMenuItem {
    /**
     * Represents a item inside of a context menu
     * @param title The title of the item
     * @param type What context menu to add this item to
     * @param callback Event callback for when the user presses the item
     */
    constructor(title: string, type: MediaType, callback: (id) => void);

    /** The title of the item */
    title: string;
    /** What context menu to add this item to */
    type: string;

    /** Adds the item to the context menu */
    register(): void;
}

/** Represents different types for setting items */
declare enum SettingType {
    Toggle = "TOGGLE",
    Slider = "SLIDER",
    TextInput = "TEXT_INPUT",
    /** Don't use this option */
    Navigation = "NAVIGATION",
    Button = "BUTTON",
    Dropdown = "DROPDOWN"    
}

declare enum MediaType {
    Track = "track",
    Album = "album",
    Artist = "artist"
}