# 🃏 Blackjack Counter

**Blackjack Counter** est une application Android moderne développée en **Kotlin** et **Jetpack Compose**, conçue pour s'entraîner au comptage de cartes au Blackjack selon la méthode **Hi-Lo** et mettre en pratique ses compétences dans un simulateur de jeu interactif.

---

## ✨ Fonctionnalités principales

### 🎯 Mode Entraînement (Drill)
* **Configuration sur mesure** : Choix du nombre de cartes, de la vitesse de défilement (de 300 ms à 2 000 ms) et du nombre de jeux dans le sabot (1 à 8 decks).
* **Animations fluides** : Défilement dynamique des cartes avec effets 3D.
* **Évaluation des résultats** : Saisie et vérification du *Running Count* (RC) et du *True Count* (TC) calculé à la fin de la session.

### 🎰 Mode Jeu (Simulateur Blackjack)
* **Parties réelles** : Jouez des mains contre le croupier avec gestion d'un bankroll et d'un système de jetons/mises.
* **Compteur d'aide (Helper)** : Option d'affichage du *Running Count* et du *True Count* en temps réel pour valider ses calculs en situation réelle.

### 📊 Statistiques & Série (Streak)
* **Série quotidienne (Streak 🔥)** : Suivi des jours consécutifs d'entraînement (style Duolingo).
* **Historique détaillé** : Calcul des taux de réussite (précision RC et TC), de la vitesse moyenne de comptage et du volume de cartes.

### 🌍 Support Multilingue
* Disponible en 5 langues : **Français**, **English**, **Español**, **Italiano** et **Português**.

### 🎨 Design & Expérience Utilisateur
* Interface Material Design 3 avec support du **Mode Sombre** / **Mode Clair**.
* Effets sonores intégrés (gestion du son personnalisable).
* Guide et rappel des règles du comptage Hi-Lo intégrés directement dans l'application.

---

## 🛠️ Stack Technique

* **Langage** : [Kotlin](https://kotlinlang.org/)
* **UI Framework** : [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material 3)
* **Stockage de données** : [DataStore Preferences](https://developer.android.com/topic/libraries/architecture/datastore)
* **Tâches en arrière-plan** : [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)
* **Asynchronisme** : Coroutines & Kotlin Flow
* **Android Min SDK** : 24 (Android 7.0 Nougat)

---

## 🚀 Lancement du projet

1. **Cloner le dépôt** :
   git clone https://github.com/VOTRE_PSEUDO/BlackjackCounter.git

2. Ouvrez le projet dans **Android Studio**.
3. Laissez Gradle synchroniser les dépendances.
4. Lancez l'application sur un émulateur ou un appareil Android connecté.

---

## 📄 Licence

Ce projet est sous licence MIT.
   
