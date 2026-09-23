<div align="center">
  <img src="https://img.shields.io/badge/Status-Active-success.svg?style=for-the-badge" alt="Status">
  <img src="https://img.shields.io/badge/Java-21-orange.svg?style=for-the-badge" alt="Java">
  <img src="https://img.shields.io/badge/Spring_Boot-3.2.4-brightgreen.svg?style=for-the-badge" alt="Spring Boot">
  <img src="https://img.shields.io/badge/AI-Gemini_1.5_Flash-blue.svg?style=for-the-badge" alt="Gemini AI">
  <h1>🏏 PitchIQ: Where Data Meets Cricket</h1>
  <p><em>Real-Time Match Simulation, Predictive Telemetry, and AI-Powered Cricket Analytics</em></p>
  <p><strong>Live Demo: <a href="https://pitchiq-swart.vercel.app">https://pitchiq-swart.vercel.app</a></strong></p>
</div>

---

## 🚀 Overview

**PitchIQ** is a Monte Carlo simulation engine and analytics suite designed to provide real-time predictive insights for T20 Cricket matches.

Built with a focus on **Clean Architecture, O(1) memory footprint during simulations, and low-latency performance**, PitchIQ demonstrates backend rigor alongside a real-time telemetry frontend.

Unlike typical sports predictors, PitchIQ features a strict **"Explainable AI"** boundary: The core Monte Carlo engine (pure Java) calculates raw statistical probabilities based on historical `Cricsheet` telemetry, while a tightly scoped integration with **Google Gemini 1.5 Flash** translates those cold, hard numbers into dynamic, persona-driven commentary (Analyst, Coach, or Fan) *without ever hallucinating its own predictions*.

---

## ✨ Key Features & Engineering Highlights

*   **⚡ Zero-Allocation Monte Carlo Engine:** Simulates 10,000 matches per request efficiently. Employs a mutable `MatchState` object to minimize Garbage Collection (GC) pauses during the simulation loop.
*   **🎯 O(log N) Weighted Random Selection:** Uses a `NavigableMap` (TreeMap) for rapid, weighted outcome resolution instead of primitive array iteration, ensuring mathematically sound probability distribution.
*   **🧠 Bounded AI Integration:** Gemini AI is injected as a translation layer. The LLM receives pre-calculated analytics (Win Probability, Momentum, Expected Runs) and generates formatted insights, anchoring responses to statistical data.
*   **🗄️ Resilient Database Architecture:** Uses a highly normalized MySQL schema accessed via Spring Data JPA. Tuned with a custom `HikariCP` connection pool configuration to manage idle timeouts and maximum lifetimes.
*   **🏎️ Telemetry Dashboard:** A responsive frontend built with plain HTML/JS/CSS. Features CSS transitions, interactive SVGs, `tsParticles`, and fallback mock-data mechanisms to handle backend cold-starts.

---

## 🏗️ Architecture

PitchIQ follows strict Clean Architecture principles, completely decoupling the domain logic from the framework layer.

```text
├── engine/ (Domain Layer - Pure Java)
│   ├── MonteCarloSimulator.java   # The core 10,000-iteration engine
│   ├── ProbabilityDistribution.java # O(log N) weighted randomizer
│   ├── MatchState.java            # Mutable state to prevent GC spikes
│   └── etl/                       # Cricsheet JSON Parsers and Validators
├── entity/ & repository/ (Data Layer)
│   └── JPA Entities mapping to a normalized MySQL schema
├── service/ (Application Layer)
│   ├── SimulationService.java     # Orchestrates DB -> Engine -> DTO
│   └── AiCommentaryService.java   # Handles Google Gemini REST API Calls
└── controller/ (Presentation Layer)
    ├── SimulationController.java  # Exposes the /api/v1/analyze endpoint
    └── GlobalExceptionHandler.java# Ensures clean JSON errors globally
```

---

## 🛠️ Quick Start

### 1. Backend Setup
1. Ensure Java 21+ and Maven are installed.
2. The application currently defaults to an in-memory **H2 Database** for immediate local testing without MySQL configuration. 
3. Start the application:
```bash
cd backend
mvn spring-boot:run
```
*The API will be available at `http://localhost:8080/api/v1/analyze`.*

### 2. Frontend Setup
1. No build step required! The frontend is pure Vanilla HTML/CSS/JS.
2. Simply serve the `frontend/` directory using any static web server:
```bash
# Using Python
cd frontend
python -m http.server 3000

# Using Node (npx)
npx serve -p 3000
```
3. Open `http://localhost:3000` in your browser. Enter current match stats, select an AI Persona, and watch the telemetry come to life!

---

## 🧪 One-Time Database Seeding (ETL) & Data Setup

PitchIQ uses external historical cricket datasets from **Cricsheet** to calculate true probability weights. Due to size constraints and repository hygiene, this dataset is not committed to version control.

### Setup Instructions
1. Download the T20s JSON dataset from [Cricsheet](https://cricsheet.org/downloads/t20s_json.zip).
2. Extract the JSON files into a directory named `cricsheet_data/` at the root of this project. (This folder is intentionally ignored by `.gitignore`).
3. PitchIQ includes an isolated `CommandLineRunner` to parse these files and populate the database.
4. Run the application with the `seed-data` profile:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=seed-data
```
*The script will locate the `cricsheet_data/` directory, process the JSONs, populate the SQL tables, and safely exit the process.*

---

<div align="center">
  <i>"Where Data Meets Cricket"</i><br>
  Built with ❤️ for Technical Excellence
</div>
