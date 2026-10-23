// Copyright (C) 2026  Henrique Almeida <me@h3nc4.com>
//
// This file is part of HyperGesture.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.

import { facts, formatSize, links } from "../facts";

// Strings holding markup are rendered with set:html, so they are written here and never taken from input.
const path = (text: string) => `<span class="path">${text}</span>`;

export const en = {
  lang: "en",
  locale: "en-US",
  home: "/",
  title: "HyperGesture: gesture navigation for HyperOS, with any launcher",
  description:
    "Free, open-source app that brings Back, Home and Recents gestures back to HyperOS when you use Nova, Niagara, Lawnchair or any other launcher. It works without root, ADB or a computer.",
  skip: "Skip to content",
  brandLabel: "HyperGesture home",
  nav: { label: "Sections", install: "Install", privacy: "Privacy", faq: "FAQ", github: "GitHub" },
  language: { label: "Language", current: "English" },

  hero: {
    title: "Your launcher, your gestures",
    lede: "HyperOS drops gestures when you switch launcher. HyperGesture brings them back without root, ADB or a computer.",
    download: "Download APK",
    downloadFacts: `v${facts.version}, ${formatSize(facts.apkBytes, "en-US")}, Android ${facts.minAndroid}+`,
    howTo: "How to install",
    qrLabel: "QR code that opens the APK download",
    qrText: "On a computer? Scan with your phone camera to open the download there.",
    caption: "A side swipe shows the same dark bulge and arrow as HyperOS's own Back.",
  },

  gestures: [
    { name: "Back", how: "Swipe in from the left or right edge" },
    { name: "Home", how: "Swipe up from the bottom edge" },
    { name: "Recent apps", how: "Swipe up from the bottom and hold" },
    { name: "Previous app", how: "Swipe right along the bottom edge" },
    { name: "Next app", how: "Swipe left along the bottom edge" },
  ],

  facts: {
    version: "Version",
    size: "Size",
    needs: "Needs",
    needsValue: `Android ${facts.minAndroid} or newer`,
    license: "License",
    licenseValue: "Free, GPL-3.0-or-later",
    cert: "Signing certificate SHA-256",
    affiliation: "Affiliation",
    affiliationValue: `Not affiliated with Xiaomi. <a href="${links.repo}">Source on GitHub</a>`,
  },

  why: {
    title: "HyperOS turned your gestures off. This turns them back on.",
    big: "HyperOS ties full-screen gestures to the stock launcher. Switch to Nova, Niagara or Lawnchair and the phone drops you back to three buttons.",
    body: `HyperGesture is a free, open-source app that brings the gestures back. It works without root, ADB, Shizuku or a computer, and it also works on any phone with Android ${facts.minAndroid} or newer.`,
    limitsTitle: "What it can't do",
    limits:
      "It can't switch on HyperOS's own gesture navigation, only the system is allowed to do that. It can open the navigation settings so you can hide the three-button bar. If HyperOS resets the navigation mode after a reboot, the app shows it but can't change it back.",
  },

  moves: {
    title: "Back, Home and Recents gestures for HyperOS",
    optional: "Turn on App switching",
    note: "Pull your finger back before lifting and nothing happens. Every distance and delay is adjustable in Gesture settings.",
  },

  install: {
    title: "Install",
    intro: "HyperGesture is not on the Play Store, so Android asks a few extra questions the first time. It takes about five minutes.",
    more: `Prefer automatic updates? <a href="${links.obtainium}">Add it to Obtainium</a>, or see every build on the <a href="${links.releases}">releases page</a>.`,
    optional: "(optional)",
    steps: [
      {
        title: "Download the APK",
        body: ["Tap Download on your phone. If your browser asks, allow it to install unknown apps, then open <code>hypergesture.apk</code>."],
      },
      {
        title: "Get past Play Protect",
        body: ["HyperGesture is not on the Play Store, so Google has not seen it before. You may see one of these."],
        dialogs: [
          { name: "App scan recommended", action: "Tap Scan app." },
          { name: "Send app for security check", action: "Tap Send this time or Don't send." },
          {
            name: "App blocked to protect your device",
            action:
              "Shown in Brazil, India and some other markets for apps with an accessibility service. Turn Play Protect off for the install. Open the Play Store, tap your profile picture, then Play Protect, the settings gear, and turn off Scan apps with Play Protect. Install HyperGesture, then turn the scan back on.",
          },
        ],
      },
      {
        title: "Allow restricted settings",
        body: [
          "Android blocks accessibility access for apps installed from a browser until you allow it. Try step 4 once first so Android shows the option.",
          `Then ${path("Settings &gt; Apps &gt; HyperGesture &gt; menu &gt; Allow restricted settings")}`,
        ],
      },
      {
        title: "Turn it on",
        body: [path("Settings &gt; Accessibility &gt; HyperGesture &gt; on"), "Open HyperGesture. The first card should say Active. Try a swipe."],
      },
      {
        title: "On HyperOS, keep it running",
        body: ["In the Keep me running card, tap Autostart and allow it. Then tap App info, open Power and pick No restrictions, which lets it run in the background."],
      },
      {
        title: "Switch apps",
        optional: true,
        body: ["Turn on the sideways swipe, tap Grant and allow Usage access."],
      },
    ],
  },

  privacy: {
    title: "Privacy",
    strips: "It listens only in thin strips along the left, right and bottom edges.",
    claim: "HyperGesture has no internet permission, so it cannot send anything anywhere.",
    body: [
      "Gestures need an accessibility service, which is a powerful thing to grant. This one only watches for touches in thin strips along the screen edges and turns a swipe into Back, Home, Recents or a switch to another app.",
      "It never reads what is on your screen, what you type or your keystrokes. With app switching on, it reads which apps you used in the last six hours, and that list stays in memory on your phone.",
      "Like the app, this website works without ads, trackers or an account.",
      `The code is public under GPL-3.0, and every release is built from it. <a href="${links.repo}">Read the source</a>.`,
    ],
  },

  faq: {
    title: "Questions",
    items: [
      {
        q: "Is it safe to turn on an accessibility service?",
        a: "It is a powerful permission. The app's setup refuses the parts that read the screen. It sees touches near the edges and nothing else, and it has no internet permission. The code is public under GPL-3.0, and every release is built from it.",
      },
      {
        q: "Why is it not on the Play Store?",
        a: "It is a free, open-source project distributed on GitHub. That is why Android shows the Play Protect and restricted settings steps on the first install.",
      },
      {
        q: "The Accessibility switch does nothing.",
        a: `Allow restricted settings first (${path("Settings &gt; Apps &gt; HyperGesture &gt; menu")}), then turn the switch on again.`,
      },
      {
        q: "My gestures stop after a while or after a reboot.",
        a: "HyperOS is closing the app in the background. Allow Autostart, and choose No restrictions under Power in App info.",
      },
      {
        q: "Accessibility says “This service is malfunctioning”.",
        a: `Turn HyperGesture off and on again under ${path("Settings &gt; Accessibility")}.`,
      },
      {
        q: "Does it work on phones that aren't Xiaomi, or with my launcher?",
        a: `Yes, on any phone with Android ${facts.minAndroid} or newer and with any launcher.`,
      },
      {
        q: "How do I update?",
        a: `Download the new APK and install it over the old one. Your settings stay. <a href="${links.obtainium}">Obtainium</a> can do it for you.`,
      },
    ],
  },

  footer: {
    blurb: `Free and open source, built with <a href="${links.contributors}">its contributors</a>. <a href="mailto:me@h3nc4.com">me@h3nc4.com</a>`,
    project: "Project",
    source: "Source code",
    releases: "Releases",
    report: "Report a problem",
    license: "License GPL-3.0-or-later. No trackers on this site.",
    trademarks:
      "Xiaomi, HyperOS and MIUI are trademarks of Xiaomi. HyperGesture is not affiliated with Xiaomi. Android is a trademark of Google LLC.",
  },

  notFound: {
    title: "Page not found",
    body: "This page does not exist.",
    back: "Go to the HyperGesture home page",
  },
};

export type Strings = typeof en;
