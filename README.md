<div align="center">
  <img src="https://img.shields.io/badge/Status-Active-success.svg?style=for-the-badge" alt="Status">
  <img src="https://img.shields.io/badge/Java-21-orange.svg?style=for-the-badge" alt="Java">
  <img src="https://img.shields.io/badge/Spring_Boot-3.2.4-brightgreen.svg?style=for-the-badge" alt="Spring Boot">
  <img src="https://img.shields.io/badge/AI-Gemini_1.5_Flash-blue.svg?style=for-the-badge" alt="Gemini AI">
  
  <h1>🏏 PitchIQ: Where Data Meets Cricket</h1>
  <p><em>Real-Time Match Simulation, Predictive Telemetry, and Bounded AI Analytics</em></p>
  
  <p><strong><a href="https://pitchiq-swart.vercel.app" target="_blank">🌐 View Live Demo</a></strong></p>
</div>

---

## 🚀 The Vision

**PitchIQ** is not just another sports prediction app. It is a highly optimized, production-grade telemetry engine built to demonstrate how strict software engineering principles can handle massive computational loads in real-time. 

Designed for scalability and performance, PitchIQ combines a pure Java **Monte Carlo simulation engine** (running 10,000 simulations per request) with a stunning **F1-inspired Glassmorphism frontend**. It proves that complex backend analytics and gorgeous frontend UX can coexist without bloated frameworks.

## 🧠 Why This Project Stands Out (For the Engineering Eye)

When building PitchIQ, I focused heavily on solving real-world performance bottlenecks. If you are reviewing this repository, here is what you will find under the hood:

*   **⚡ Zero-Allocation Core Engine:** Simulating 10,000 matches concurrently can crash standard JVMs due to Garbage Collection (GC) pauses. PitchIQ uses a strictly mutable `MatchState` object, driving memory allocations down to effectively **O(1)** during the tight simulation loop.
*   **🎯 Algorithmic Efficiency:** Instead of primitive array iteration for probability weighting, the engine leverages a `NavigableMap` (TreeMap) for **O(log N)** weighted random selection, ensuring rapid and mathematically sound outcome resolution.
*   **🛡️ Bounded "Explainable" AI:** AI hallucination is a massive risk in sports analytics. PitchIQ uses Google Gemini 1.5 Flash *only* as a strict translation layer. The LLM receives pre-calculated, deterministic analytics (Win Probability, Momentum, Expected Runs) and generates persona-driven commentary. **The AI never guesses the score; the math dictates it.**

---

## 🏗️ Clean Architecture & Code Structure

The backend strictly adheres to **Clean Architecture** principles. Domain logic (the Monte Carlo engine) has zero dependencies on the Spring framework, allowing it to be tested and run in complete isolation.

```text
📦 pitchiq-backend
 ┣ 📂 engine/               # Domain Layer (Pure Java, zero Spring dependencies)
 ┃ ┣ 📜 MonteCarloSimulator.java   # The core 10,000-iteration engine
 ┃ ┣ 📜 ProbabilityDistribution.java # O(log N) weighted randomizer
 ┃ ┗ 📜 MatchState.java            # Mutable state for zero-allocation simulation
 ┣ 📂 entity/               # Data Layer (JPA Entities)
 ┣ 📂 repository/           # Persistence Layer (Spring Data JPA)
 ┣ 📂 service/              # Application Layer
 ┃ ┣ 📜 SimulationService.java     # Orchestrates DB -> Engine -> Frontend
 ┃ ┗ 📜 AiCommentaryService.java   # Safely bounds and formats LLM prompts
 ┗ 📂 controller/           # Presentation Layer (REST APIs)
   ┣ 📜 SimulationController.java  # Exposes the /api/v1/analyze endpoint
   ┗ 📜 GlobalExceptionHandler.java# Ensures clean, standard JSON errors
```

---

## 💻 Tech Stack

### Backend
- **Core:** Java 21, Spring Boot 3.2.4
- **Architecture:** Clean Architecture, Domain-Driven Design (DDD)
- **Database:** MySQL (Production) / H2 (Local testing), Spring Data JPA
- **AI Integration:** Google Gemini 1.5 Flash REST API

### Frontend
- **Core:** Vanilla JavaScript (ES6+ Modules), HTML5, CSS3
- **Design:** Glassmorphism UI, Responsive CSS Grid/Flexbox
- **Visuals:** `tsParticles` (Ambient background), CSS Animations, Chart.js
- **Auth:** Firebase Authentication (Google OAuth)

---

## 🛠️ Quick Start

Want to run it locally? It takes less than two minutes.

### 1. Backend (The Engine)
Ensure Java 21+ and Maven are installed. The application gracefully defaults to an in-memory **H2 Database** for immediate local testing.

```bash
cd backend
mvn spring-boot:run
```
*The API spins up on `http://localhost:8080`.*

### 2. Frontend (The Telemetry UI)
Because the frontend is pure Vanilla JS, there is no massive `node_modules` folder or build step required.

```bash
cd frontend
# Using Python
python -m http.server 3000
```
*Open `http://localhost:3000` in your browser. Enter current match stats or click a live match, and watch the telemetry engine crunch the numbers.*

---

## 🧪 One-Time Database Seeding (ETL)

PitchIQ uses external historical cricket datasets from **Cricsheet** to calculate true probability weights. Due to size constraints, this dataset is not committed to version control.

### Setup Instructions
1. Download the T20s JSON dataset from [Cricsheet](https://cricsheet.org/downloads/t20s_json.zip).
2. Extract the JSON files into a directory named `cricsheet_data/` at the root of this project.
3. PitchIQ includes an isolated `CommandLineRunner` to parse these files and populate the database.
4. Run the application with the `seed-data` profile:

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=seed-data
```
*The ETL script will locate the `cricsheet_data/` directory, process the JSONs, populate the SQL tables, and safely exit the process.*

---

<div align="center">
  <i>"Where Data Meets Cricket"</i><br>
  Built with ❤️ by Ramu Maddirala
</div>
