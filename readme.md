<div align="center">
  
# Spotify Plus
Spotify Plus is the ultimate enhancement mod for Spotify

Word by word lyrics, new features, customizations, and much more, all in one module

![GitHub Downloads](https://img.shields.io/github/downloads/Xposed-Modules-Repo/com.lenerd46.spotifyplus/total)
![GitHub Repo stars](https://img.shields.io/github/stars/LeNerd46/SpotifyPlus)
<a href="https://t.me/spotifypluscool" target="_blank">
![Telegram Badge](https://telegram-badge.vercel.app/api/telegram-badge?channelId=@spotifypluscool)
</a>
![Static Badge](https://img.shields.io/badge/Spotify%20Version-9.1.68.1888-green?logo=spotify)
![GitHub Release](https://img.shields.io/github/v/release/LeNerd46/SpotifyPlus?color=lightgreen)

</div>

## What Is Spotify Plus

Spotify Plus is an Xposed module that enhances Spotify with new functionality, many quality of life improvements, and more customization, while preserving the native Spotify feel and experience.

> [!IMPORTANT]
> The latest recommended version of Spotify to use is v9.1.68.1888. The module is not guaranteed to work past this version

## Features

<details>
<summary><b>🎵 Beautiful Lyrics!</b></summary>

<br/>
Replaces Spotify's boring line by line lyrics with a more beautiful experience! Enjoy word by word lyrics with a dynamic background, background vocals, and duet lines. It's also very customizable! You can change things like font, interlude duration, line spacing, and more! This works for the small lyrics that appear above the song title in the now playing view as well!

</details>

<details>
<summary><b>🌐 Lyric Translations</b></summary>

<br/>
Translate any song to English, making listening to songs in any language even better! There are also a few options to make it just how you want it. You can swap the translated line with the original line, or you can just hide the original line all together! It's completely up to you

</details>

<details>
<summary><b>🎵✏️ Built In Lyrics Editor</b></summary>

<br/>
You can sync any line by line song you would like. Once you create your word by word lyrics, you can submit it for everyone else to enjoy! And the best part is, it's built right into Spotify. So you don't have to go messing around with other websites or random files or whatever. Just sync, submit, and enjoy!
</details>

<details>
<summary><b>📻 Last.fm Integration</b></summary>

<br/>

Do you use [Last.fm](https://www.last.fm/)? Want to know how many times you've listened to a song directly inside of Spotify? Well you can with Spotify Plus! Just set your username in the Spotify Plus settings, and open the context menu on any song (the three dots)! It will tell you your scrobbles right there in the header. There will also be a button that will bring you directly to that song on the last.fm website

</details>

<details>
<summary><b>😴 Custom Sleep Timer</b></summary>

<br/>
You can set a custom duration for your sleep timers! You can set a one time timer, or you can save a custom amount to always show up in the list of items. You can even rearrange any of the timers!
</details>

<details>
<summary><b>🔒 Persistent Private Session</b></summary>

<br/>
Do you like having private session enabled? Tired of it turning off every six hours? Well you don't have to worry about that anymore! With this setting, you can keep it enabled forever!

</details>

<details>
<summary><b>🎨 Custom Themes</b></summary>

<br/>
Yeah, Spotify's default black background is pretty boring, isn't it? Why not spruce it up with some color! You can choose any color you want and get a theme for it, or you can specify a specific color for any part of Spotify's UI. Feel free to change it whenever you want!

</details>

<details>
<summary><b>🎨✨ Auto Theme</b></summary>

<br/>
Automatically generate a theme from the currently playing song! It takes the colors from the album artwork, and generates a theme every time the song changes based on that artwork. This makes Spotify always feel fresh and in sync with whatever song you're listening to.

</details>

<details>
<summary><b>✨ Animated Album Artwork</b></summary>

<br/>
Static album artwork images are pretty boring and outdated, right? Well fear no more, with the animated album artwork setting enabled, it will show you any album that has animated album artwork. This works in both the album's page, as well as the now playing view if you have canvas (Spotify's short, looping video) disabled.

</details>

<details>
<summary><b>☰ Play Next</b></summary>

<br/>

Unlike Spotify's normal queue which removes songs after they're done playing, Play Next keeps songs in your playback history permanently, allowing you to easily jump back to them with the skip previous button.

</details>

## Installing

Requirements:

- A rooted phone with LSPosed installed
- LSPatch for non rooted phones
  <br/><br/>

For Rooted Phones:

1. Install the APK from the releases page
2. Turn on the module in LSPosed and enable it for Spotify
3. Force stop Spotify
4. Play a song, open the full screen lyrics page, and enjoy the lyrics!
   <br/><br/>

---

For Non Rooted Phones:

1. Install the APK from the releases page
2. Download a Spotify APK (you can get it [here](https://spotify.en.uptodown.com/android/download))
3. Setup LSPatch (if using Android 15 or Android 16, using [this fork](https://github.com/JingMatrix/LSPatch). Shizuku not working? Try using [this fork](https://github.com/thedjchi/Shizuku))
4. Press the plus button -> Select apk from storage -> and find your Spotify APK file
5. Select Local, start the patch, and install the patched app
6. Inside of LSPatch, tap on Spotify, select module scope, and select Spotify Plus
7. Force stop Spotify
8. Enjoy the new features!

---

## FAQ

**Spotify is telling me I can only use Spotify abroad for 14 days when trying to log in?**
If Spotify tells you this, you have to sign in without your password. Spotify will send you an email to login. This should fix the issue. Make sure you have Spotify links set to open inside of the Spotify app inside of your Android settings.

**Spotify Plus settings button is not showing up in the menu?**
Check what the latest supported Spotify version is (see above). This is often one of the things that disappears when you have too new of a version.

**Still have questions?**
Feel free to join our [Telegram group](https://t.me/spotifypluscool). We'd be happy to help!

## Community

Join the Spotify Plus community! We have a Telegram channel where you can discuss the module and give feedback and discuss directly. You can join [here](https://t.me/spotifypluscool)

## Credit

- [JingMatrix/Vector](https://github.com/JingMatrix/Vector) - Main Xposed framework
- [LuckyPray/DexKit](https://github.com/LuckyPray/DexKit) - Dex parsing library, used for wide Spotify compatibility
- [surfbryce/beautiful-lyrics](https://github.com/surfbryce/beautiful-lyrics) - The project that made me want to develop this module, also helped structure the lyrics

## Contributing

Spotify Plus is an ambitious project, and help is always welcome. Whether you found a bug, have a feature you would love to see, or if you have a pull request, feel free to contribute however you can!

Want to collaborate more closely? Feel comfortable in Java and TypeScript? Reach out to me through my email or on the Telegram and let's talk about it! Spotify Plus has grown into a much larger project than I ever expected. I'm working on a rewrite that will lay the groundwork for future extensions and a marketplace, very similar to spicetify, but for mobile. I would love to collaborate with other developers who are interested in helping out and are familiar with Java and/or TypeScript!

## Donating

I started developing Spotify Plus because I thought Spotify could be so much more. It all started when I used the beautiful lyrics extension by surfbryce. It changed how I viewed music and how we can experience it, and from then on, I wanted to bring that experience to mobile.

Spotify Plus is developed in my free time, and it will always remain free and open source. If you like my mission and what I'm building, and want to help support development, you can sponsor me on GitHub. By no means is it required, but any amount is appreciated ♥

---

<div align="center">
    <sub>Made with ♥ and 🎵 by Devon Shoutz</sub><br/>
    <sub>Spotify Plus is not affiliated with Spotify.</sub>
</div>
