// Initialize Particles background
tsParticles.loadEngine(window.tsparticlesEngine).then(() => {
    tsParticles.load("tsparticles", {
        background: { color: { value: "transparent" } },
        fpsLimit: 60,
        particles: {
            color: { value: "#00f0ff" },
            links: { color: "#00f0ff", distance: 150, enable: true, opacity: 0.2, width: 1 },
            move: { enable: true, speed: 0.5, direction: "none", random: false, straight: false, outModes: { default: "bounce" } },
            number: { density: { enable: true, area: 800 }, value: 40 },
            opacity: { value: 0.3 },
            shape: { type: "circle" },
            size: { value: { min: 1, max: 3 } }
        },
        detectRetina: true
    });
});

document.getElementById('analyzeBtn').addEventListener('click', async () => {
    const btn = document.getElementById('analyzeBtn');
    btn.innerHTML = 'CALCULATING <span class="pulse" style="display:inline-block;margin-left:10px;"></span>';
    btn.disabled = true;

    const payload = {
        venueId: 1, 
        battingTeamId: 1,
        bowlingTeamId: 2,
        currentRuns: parseInt(document.getElementById('currentRuns').value),
        currentWickets: parseInt(document.getElementById('currentWickets').value),
        oversBowled: parseInt(document.getElementById('oversBowled').value),
        ballsBowledInOver: parseInt(document.getElementById('ballsBowled').value),
        targetScore: parseInt(document.getElementById('targetScore').value),
        persona: document.getElementById('personaSelect').value
    };

    try {
        // Attempt fetch, fallback to dummy data if backend is offline to preserve UI demo for user
        let data;
        try {
            const response = await fetch('http://localhost:8080/api/v1/analyze', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            if (!response.ok) throw new Error("Backend offline");
            data = await response.json();
        } catch (e) {
            console.warn("Backend not reachable. Using mock telemetry data for UI presentation.");
            const selectedPersona = document.getElementById('personaSelect').value;
            let mockCommentary;
            if (selectedPersona === 'Funny Fan') {
                mockCommentary = {
                    fact: "Did you know? I once ate 12 hotdogs during a rain delay.",
                    tactical: "Just hit every ball for six, it's not that hard guys.",
                    funny: "At this rate, they'll need a time machine, not a helicopter shot.",
                    confidence: "I'm 100% confident my heart rate is higher than the RRR."
                };
            } else if (selectedPersona === 'Coach') {
                mockCommentary = {
                    fact: "Matches at this venue are historically won in the middle overs.",
                    tactical: "Focus on strike rotation. Don't let the dot ball pressure build.",
                    funny: "If they miss, you hit. If you miss... we'll talk in the dressing room.",
                    confidence: "The 72.5% win probability is solid, but discipline is key here."
                };
            } else {
                mockCommentary = {
                    fact: "Teams batting second here win 65% of the time.",
                    tactical: "Keep rotating strike, boundaries will come automatically on this pitch.",
                    funny: "At this rate, they might finish the game before the pizza arrives.",
                    confidence: "The engine predicts a comfortable win given 8 wickets in hand."
                };
            }

            data = {
                winProbability: 0.725,
                projectedScore: 195,
                expectedRunsRemaining: 75,
                requiredRunRate: 9.6,
                momentumMeter: 0.8,
                aiCommentary: mockCommentary
            };
        }

        // Show HUD
        document.getElementById('hud').style.display = 'grid';

        // Update Ring (Win Probability)
        const probPct = Math.round(data.winProbability * 100);
        document.getElementById('winProbRing').setAttribute('stroke-dasharray', `${probPct}, 100`);
        document.getElementById('winProbText').textContent = `${probPct}%`;

        // Update Stats
        animateValue("projScoreText", 0, data.projectedScore, 1000);
        document.getElementById('expRunsText').textContent = data.expectedRunsRemaining;
        document.getElementById('rrrText').textContent = data.requiredRunRate.toFixed(2);
        
        // Update Momentum Bar
        document.getElementById('momentumFill').style.width = `${data.momentumMeter * 100}%`;

        // Update AI
        if (data.aiCommentary) {
            document.getElementById('aiFact').textContent = data.aiCommentary.fact;
            document.getElementById('aiTactical').textContent = data.aiCommentary.tactical;
            document.getElementById('aiFunny').textContent = data.aiCommentary.funny;
            document.getElementById('aiConfidence').textContent = data.aiCommentary.confidence;
        }

    } catch (error) {
        console.error(error);
        alert("Telemetry sync failed. Check console.");
    } finally {
        btn.innerHTML = 'RUN SIMULATION <span class="arr">&rarr;</span>';
        btn.disabled = false;
    }
});

function animateValue(id, start, end, duration) {
    const obj = document.getElementById(id);
    let startTimestamp = null;
    const step = (timestamp) => {
        if (!startTimestamp) startTimestamp = timestamp;
        const progress = Math.min((timestamp - startTimestamp) / duration, 1);
        obj.innerHTML = Math.floor(progress * (end - start) + start);
        if (progress < 1) {
            window.requestAnimationFrame(step);
        }
    };
    window.requestAnimationFrame(step);
}
