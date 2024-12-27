<h1>Custom Map Markers</h1>

<p>Hello! I am Ilya Prokofiev, a developer from Russia with a strong desire to help everyone suffering from the consequences of war.</p>

<p>This project provides a small piece of code that simplifies rendering custom markers on a map. It allows you to:</p>
<ul>
    <li>Render any markers in XML format based on Google Icons.</li>
    <li>Customize marker size and color to suit your needs.</li>
    <li>Enjoy faster rendering using efficient system methods with lightweight image caching.</li>
</ul>

<h2>Features</h2>
<ul>
    <li><strong>Customizable Markers:</strong> Easily adjust the size and color of the markers.</li>
    <li><strong>Performance Optimization:</strong> Markers are rendered quickly thanks to system-based drawing methods and caching.</li>
    <li><strong>Versatile Usage:</strong> Perfect for enhancing navigation with visually distinct markers.</li>
</ul>

<p>Here are some examples of how the markers look:</p>

<div style="display: flex; overflow-x: auto; gap: 20px; padding: 10px 0;">
    <div style="flex: 0 0 auto;">
        <h3>Example 1</h3>
        <img src="https://github.com/user-attachments/assets/bb01e023-c8ab-4c80-914c-8b99a36d3a42" width="300" height="533" alt="Example 1">
    </div>
    <div style="flex: 0 0 auto;">
        <h3>Example 2</h3>
        <img src="https://github.com/user-attachments/assets/a1bd6485-c54c-4a8a-b7e5-fb3c943624f8" width="300" height="533" alt="Example 2">
    </div>
    <div style="flex: 0 0 auto;">
        <h3>Example 3</h3>
        <img src="https://github.com/user-attachments/assets/b1f4f336-e3bc-45a6-8a01-3ff7a1b49761" width="300" height="533" alt="Example 3">
    </div>
</div>


How It Works

Here’s a visual guide to drawing your markers:


![image](https://github.com/user-attachments/assets/b4dd46c9-7df9-441a-a3a3-a11e595db121)


<h1> <a href="https://redalert.me/" target="_blank"><img src="img/logo_big.png" align="right" height="40"></a> RedAlert for Android</h1>

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/eladnava/redalert-android?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)

RedAlert was developed by volunteers to provide real-time emergency alerts for Israeli citizens.

* [Official Site](https://redalert.me)
* [App Store Listing](https://apps.apple.com/il/app/zb-dwm-htr-wt-bzmn-mt/id937914925)
* [Google Play Listing](https://play.google.com/store/apps/details?id=com.red.alert)
* [Android APK Download](https://github.com/eladnava/redalert-android/releases/latest/download/app-release.apk)

The app relays real-time safety alerts published by the Home Front Command (Pikud Haoref) using the [pikud-haoref-api](https://github.com/eladnava/pikud-haoref-api) Node.js package.

## Screenshots

<img src="img/screenshot1.png" width="250"> <img src="img/screenshot2.png" width="250">

## Achievements

* **3,000,000+** downloads
* Published by **Geektime** as [the fastest rocket alert app](http://www.geektime.co.il/push-notifications-at-protective-edge/)
* Featured by the Israeli government on their [Google+ page](https://plus.google.com/+Israel/posts/U3juWS1YPK4)
* Ranked **1st place** on **Google Play's Top Free** in Israel for 4 weeks during Operation Protective Edge
* Won **2nd place** in the [Ford SYNC AppLink TLV](https://eladnava.com/how-we-won-2nd-place-ford-tel-aviv-hackathon/) hackathon for integrating the app with Ford cars

## Features

#### The fastest, most reliable emergency alert app in Israel.

* Speed & reliability - alerts are received before / during the official siren thanks to dedicated notification servers
* Location-based alerts - receive emergency alerts on the move in addition to city / region selection
* Threat types - receive alerts about rocket fire, hostile aircraft intrusion, terrorist infiltration, and more
* Alert history - see the list of recent alerts, their location, and time of day (in your local time)
* Connectivity test - check, at any time, whether your device is able to receive alerts via the "self-test" option
* Sound selection - choose from 15 unique sounds for alerts or choose a custom sound
* Silent mode override - the application will override silent / vibrate mode to sound alerts
* Vibration - your phone will vibrate in addition to playing the selected alert sound
* Area selection - select preferred alert cities / regions by searching for them
* Countdown - alerts will display the estimated time until impact
* I'm safe - let your friends and family know you are safe by sending an "I'm safe" message via the app
* Localization - the app has been translated to multiple languages (Hebrew, English, Arabic, Russian, Italian, Spanish, French, and German)

## Requirements
* Android SDK
* Android Studio with Gradle Plugin
* A physical device to test on (recommended) running Android 2.3+ with Google APIs (optional)

## Collaborating

* If you find a bug or wish to make some kind of change, please create an issue first
* Make your commits as tiny as possible - one feature or bugfix at a time
* Write detailed commit messages, in-line with the project's commit naming conventions
* Make sure your code conventions are in-line with the project

## Donations

The application was developed by volunteers to protect Israeli citizens. 
Your donation is greatly appreciated.

* [Donate via Paypal](https://www.paypal.com/cgi-bin/webscr?cmd=_donations&business=eladnava@gmail.com&lc=US&item_name=RedAlert&no_note=0&cn=&curency_code=USD&bn=PP-DonationsBF:btn_donateCC_LG.gif:NonHosted)

## Special Thanks

* Thanks to Ilana Badner for the Russian translation
* Thanks to Rodolphe Moulin for the French translation
* Thanks to Matteo Villosio for the Italian translation
* Thanks to David Halbani for the German translation
* Thanks to Nathan Allenberg and Noam Hashmonai for the Spanish translation
* Thanks to Eden Glant for the "Siren 1" and "Siren 2" sounds
* Thanks to the developers of the Tzofar app for the map polygon data

## License

Apache 2.0
