import { MarketplaceItem } from "./types";

export const MARKETPLACE_ITEMS: MarketplaceItem[] = [
    {
        id: "beautiful-lyrics",
        title: "Beautiful Lyrics",
        author: "Lenerd",
        description: "A polished fullscreen lyrics experience with smoother scrolling, stronger focus effects, and a more immersive now playing screen.",
        version: "1.4.2",
        downloads: "42.8k",
        rating: "4.9",
        accentColor: "#7c3aed",
        githubUrl: "https://github.com/example/beautiful-lyrics",
        tags: ["Lyrics", "UI", "Popular"],
        features: [
            "Animated lyric focus states",
            "Improved fullscreen presentation",
            "Better synced lyric readability",
            "Designed for a native-feeling experience"
        ],
        readme: "Beautiful Lyrics upgrades SpotifyPlus with a cinematic lyric screen inspired by the best parts of modern music apps. It improves active line emphasis, spacing, fullscreen layout, and overall readability. This script is ideal if you want your now playing screen to feel premium and expressive without losing clarity."
    },
    {
        id: "amoled-everywhere",
        title: "AMOLED Everywhere",
        author: "Midnight Labs",
        description: "Turns the interface into a deeper black theme with cleaner contrast, subtler cards, and a more modern premium look.",
        version: "2.1.0",
        downloads: "31.1k",
        rating: "4.8",
        accentColor: "#111827",
        githubUrl: "https://github.com/example/amoled-everywhere",
        tags: ["Theme", "Visual", "AMOLED"],
        features: [
            "True black backgrounds",
            "Cleaner contrast for OLED displays",
            "Improved card and panel styling",
            "Pairs well with lyric-focused scripts"
        ],
        readme: "AMOLED Everywhere is a visual enhancement package focused on deeper blacks, sharper contrast, and a less gray-heavy interface. It keeps the Spotify feel intact while making everything look cleaner and more refined on OLED displays."
    },
    {
        id: "queue-tools",
        title: "Queue Tools Pro",
        author: "OpenWave",
        description: "Adds quick actions for your queue so you can reorder, clean up, and jump through tracks faster.",
        version: "0.9.7",
        downloads: "18.4k",
        rating: "4.7",
        accentColor: "#0f766e",
        githubUrl: "https://github.com/example/queue-tools-pro",
        tags: ["Productivity", "Queue", "Controls"],
        features: [
            "Quick queue management actions",
            "Fast remove and reorder shortcuts",
            "Smarter playback workflow",
            "Built for power users"
        ],
        readme: "Queue Tools Pro gives you more control over playback flow. Instead of digging through menus, you get direct shortcuts for common queue actions, making it easier to keep long listening sessions organized."
    },
    {
        id: "stats-overlay",
        title: "Stats Overlay",
        author: "Northbyte",
        description: "Displays compact listening stats, engagement info, and playback metadata right inside the app.",
        version: "1.0.3",
        downloads: "9.6k",
        rating: "4.6",
        accentColor: "#ea580c",
        githubUrl: "https://github.com/example/stats-overlay",
        tags: ["Stats", "Overlay", "Metadata"],
        features: [
            "Compact live metadata readout",
            "Listening session summaries",
            "Useful for debugging and experimentation",
            "Minimal visual footprint"
        ],
        readme: "Stats Overlay is perfect if you like seeing what is going on under the hood. It surfaces lightweight playback stats and metadata in a compact UI that stays out of the way while still being useful."
    },
    {
        id: "bookmark-hub",
        title: "Bookmark Hub",
        author: "Lenerd",
        description: "Save tracks, albums, and playlists into custom bookmark groups so you can come back to them later.",
        version: "1.2.1",
        downloads: "14.2k",
        rating: "4.8",
        accentColor: "#2563eb",
        githubUrl: "https://github.com/example/bookmark-hub",
        tags: ["Library", "Bookmarks", "Utility"],
        features: [
            "Custom bookmark collections",
            "Fast save and reopen actions",
            "Organized browsing for saved media",
            "Simple and clean interface"
        ],
        readme: "Bookmark Hub adds a practical save-for-later workflow to SpotifyPlus. Instead of losing tracks in your history or likes, you can create a curated set of bookmarks and revisit them whenever you want."
    }
];