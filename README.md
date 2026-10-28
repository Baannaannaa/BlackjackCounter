# 🃏 Blackjack Counter

**Blackjack Counter** is a modern Android application built with **Kotlin** and **Jetpack Compose**, designed to practice card counting in Blackjack using the **Hi-Lo** system and test your skills in an interactive game simulator.

---

## ✨ Key Features

### 🎯 Practice Mode (Drill)
* **Customizable Setup**: Choose the number of cards, card flip speed (from 300 ms to 2,000 ms), and number of decks in the shoe (1 to 8 decks).
* **Smooth Animations**: Dynamic card flipping with 3D visual effects.
* **Accuracy Evaluation**: Input and verify your calculated *Running Count* (RC) and *True Count* (TC) at the end of each session.

### 🎰 Game Mode (Blackjack Simulator)
* **Real Gameplay**: Play hands against the dealer with bankroll management and a chip betting system.
* **Live Count Helper**: Optional real-time overlay displaying the *Running Count* and *True Count* to validate your calculations during play.

### 📊 Stats & Daily Streaks
* **Daily Streak Tracker (Streak 🔥)**: Track your consecutive practice days (Duolingo-style).
* **Detailed History**: View win rates (RC and TC accuracy), average counting speed, and total cards counted.

### 🌍 Multi-language Support
* Available in 5 languages: **English**, **French**, **Spanish**, **Italian**, and **Portuguese**.

### 🎨 Design & User Experience
* Modern Material Design 3 interface with **Dark Mode** / **Light Mode** support.
* Custom card sound effects and audio controls.
* Integrated rules guide explaining the Hi-Lo counting system, Running Count, and True Count.

---

## 🛠️ Tech Stack

* **Language**: [Kotlin](https://kotlinlang.org/)
* **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material 3)
* **Data Storage**: [DataStore Preferences](https://developer.android.com/topic/libraries/architecture/datastore)
* **Background Tasks**: [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)
* **Asynchrony**: Coroutines & Kotlin Flow
* **Min Android SDK**: 24 (Android 7.0 Nougat)

---

## 🚀 Getting Started

1. **Clone the repository**:
   git clone https://github.com/YOUR_USERNAME/BlackjackCounter.git

2. Open the project in **Android Studio**.
3. Allow Gradle to sync dependencies.
4. Run the application on an Android emulator or physical device.

---

## 📄 License

This project is licensed under the MIT License.
