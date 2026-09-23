<div align="center">
  <img src="https://img.shields.io/badge/Status-Active-success.svg?style=for-the-badge" alt="Status">
  <img src="https://img.shields.io/badge/Java-21-orange.svg?style=for-the-badge" alt="Java">
  <img src="https://img.shields.io/badge/Spring_Boot-3.2.4-brightgreen.svg?style=for-the-badge" alt="Spring Boot">
  <img src="https://img.shields.io/badge/AI-Gemini_1.5_Flash-blue.svg?style=for-the-badge" alt="Gemini AI">
  
  <h1>🏏 PitchIQ: Where Data Meets Cricket</h1>
  <p><em>Real-Time Match Simulation, Predictive Telemetry, and Bounded AI Analytics</em></p>
  
  <p><strong><a href="https://pitchiq-ai.vercel.app" target="_blank">🌐 View Live Demo</a></strong></p>
</div>

---

## 👋 Hello, I'm Ramu Maddirala.

If you are a recruiter, hiring manager, or fellow engineer visiting this page—welcome! 

I built **PitchIQ** from the ground up to showcase my ability to build **highly performant backend systems** and **gorgeous frontend user experiences**. This isn't just a basic web app; it's a full-stack, real-time sports telemetry dashboard designed to simulate the complexities of T20 Cricket.

I wanted to prove that you don't need bloated frameworks to make something beautiful, and you don't need massive servers to run complex math if you write highly optimized code. 

**Give the live demo a try:** Run a manual simulation, check out the F1-inspired telemetry charts, and chat with the "Ask PI" AI at the bottom of the page!

---

## 🚀 What Does PitchIQ Do?

PitchIQ is a predictive analytics engine. Imagine watching a live cricket match and wanting to know the *exact* mathematical probability of who will win, based on decades of historical data, updated ball-by-ball. 

PitchIQ takes real match states, runs **10,000 Monte Carlo simulations** in a fraction of a second, and translates those cold, hard numbers into dynamic, persona-driven commentary using Google Gemini AI.

---

## 🧠 Why I Built It This Way (The Engineering Highlights)

I wanted to solve real-world engineering problems with this project. Here is how I approached them:

*   **⚡ Extreme Performance (Zero-Allocation Engine):** Running 10,000 simulations concurrently can normally crash a Java application due to memory overload. I engineered a strictly mutable `MatchState` object, driving memory allocations down to effectively **O(1)** during the simulation loop. It runs lightning fast.
*   **🎯 Algorithmic Efficiency:** Instead of looping through standard arrays, the engine leverages a `NavigableMap` (TreeMap) for **O(log N)** weighted random selection. The math is not only fast, it's structurally sound.
*   **🛡️ "Explainable" AI Boundaries:** AI hallucination is a massive risk. I integrated Google Gemini 1.5 Flash *only* as a strict translation layer. The AI is fed pre-calculated, deterministic analytics (Win Probability, Momentum, Expected Runs). **The AI never guesses the score; my math engine dictates it.**
*   **🏎️ The Glassmorphism UI:** I built the F1-inspired, cyberpunk dashboard using **Vanilla JavaScript, HTML, and CSS**. It features smooth transitions, interactive charts, and `tsParticles` glowing in the background, proving a deep understanding of core web technologies without relying on heavy frontend frameworks.

---

## 🏗️ Architecture & Code Structure

I strictly adhered to **Clean Architecture**. The core simulation engine (Domain Logic) has absolutely zero dependencies on the Spring Boot framework, allowing it to be tested and run in complete isolation.

```text
📦 pitchiq-backend
 ┣ 📂 engine/               # Pure Java Domain Layer (Zero Spring dependencies)
 ┃ ┣ 📜 MonteCarloSimulator.java   # The core 10,000-iteration engine
 ┃ ┣ 📜 ProbabilityDistribution.java # O(log N) weighted math engine
 ┃ ┗ 📜 MatchState.java            # Mutable state for extreme performance
 ┣ 📂 entity/               # Data Layer (JPA Entities)
 ┣ 📂 service/              # Application Layer (Orchestrates DB -> Engine -> UI)
 ┗ 📂 controller/           # Presentation Layer (REST APIs)
```

---

## 💻 The Tech Stack

- **Backend:** Java 21, Spring Boot 3.2.4, Clean Architecture (DDD)
- **Database:** MySQL (Production) / H2 (Local testing), Spring Data JPA
- **Frontend:** Vanilla JavaScript (ES6+), CSS3 (Glassmorphism), Chart.js
- **AI Integration:** Google Gemini 1.5 Flash REST API

---

## 🛠️ Quick Start (For Developers)

Want to run it locally? It takes less than two minutes. Ensure Java 21+ and Maven are installed.

```bash
cd backend
mvn spring-boot:run
```
*The API spins up on `http://localhost:8080` with an in-memory database.*

To view the UI, simply serve the frontend directory (no build step required!):
```bash
cd frontend
npx serve -p 3000
```
*Open `http://localhost:3000` in your browser.*

---

<div align="center">
  <i>"Built with Love. Driven by Passion."</i><br>
  <strong>Designed & Developed by Ramu Maddirala</strong>
</div>
