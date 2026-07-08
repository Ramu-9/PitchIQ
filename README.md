<div align="center">
  <img src="https://img.shields.io/badge/Status-Active-success.svg?style=for-the-badge" alt="Status">
  <img src="https://img.shields.io/badge/Java-21-orange.svg?style=for-the-badge" alt="Java">
  <img src="https://img.shields.io/badge/Spring_Boot-3.2.4-brightgreen.svg?style=for-the-badge" alt="Spring Boot">
  <img src="https://img.shields.io/badge/AI-Gemini_1.5_Flash-blue.svg?style=for-the-badge" alt="Gemini AI">
  <h1>🏏 PitchIQ: Where Data Meets Cricket</h1>
  <p><em>Real-Time Match Simulation, Predictive Telemetry, and AI-Powered Cricket Analytics</em></p>
</div>

---

## 🚀 Overview

**PitchIQ** is a production-grade, highly optimized Monte Carlo simulation engine and analytics suite designed to provide real-time predictive insights for T20 Cricket matches. 
1
Built with an unapologetic focus on **Clean Architecture, O(1) memory footprint during simulations, and zero-latency performance**, PitchIQ is designed to impress engineering teams with its backend rigor and its stunning, F1-inspired telemetry frontend.

Unlike typical sports predictors, PitchIQ features a strict **"Explainable AI"** boundary: The core Monte Carlo engine (pure Java) calculates raw statistical probabilities based on historical `Cricsheet` telemetry, while a tightly scoped integration with **Google Gemini 1.5 Flash** translates those cold, hard numbers into dynamic, persona-driven commentary (Analyst, Coach, or Fan) *without ever hallucinating its own predictions*.

---

## ✨ Key Features & Engineering Highlights

*   **⚡ Zero-Allocation Monte Carlo Engine:** Simulates 10,000 matches per request in milliseconds. Employs a mutable `MatchState` object to entirely eliminate Garbage Collection (GC) pauses during the simulation loop.
*   **🎯 O(log N) Weighted Random Selection:** Uses a `NavigableMap` (TreeMap) for rapid, weighted outcome resolution instead of primitive array iteration, ensuring mathematically sound probability distribution.
*   **🧠 Bounded AI Integration:** Gemini AI is injected as a strict translation layer. The LLM receives pre-calculated analytics (Win Probability, Momentum, Expected Runs) and generates formatted insights, preventing AI hallucinations and ensuring statistical integrity.
*   **🗄️ Resilient Free-Tier Database Architecture:** Uses a highly normalized MySQL schema accessed via Spring Data JPA. Tuned with a custom `HikariCP` connection pool configuration to aggressively manage idle timeouts and maximum lifetimes, preventing connection starvation on strict free-tier cloud databases (Aiven/TiDB).
*   **🏎️ F1-Telemetry Dashboard:** A jaw-dropping `Glassmorphism` frontend built with plain HTML/JS/CSS. Features smooth CSS transitions, interactive SVGs, `tsParticles`, and fallback mock-data mechanisms to handle backend cold-starts gracefully.

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

## 🧪 One-Time Database Seeding (ETL)

To parse raw historical data from `Cricsheet` JSON files and seed your database with true probability weights, PitchIQ includes an isolated `CommandLineRunner`.

To prevent the ETL from running on every startup, it is sequestered behind a Spring Profile. Run the application with the `seed-data` profile:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=seed-data
```
*The script will locate the `cricsheet_data/` directory, process the JSONs, populate the SQL tables, and safely exit the process.*

---

<div align="center">
  <i>"Where Data Meets Cricket"</i><br>
  Built with ❤️ for Technical Excellence
</div>
