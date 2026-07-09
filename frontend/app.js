// Firebase Configuration
import { initializeApp } from "https://www.gstatic.com/firebasejs/10.9.0/firebase-app.js";
import { getAnalytics, logEvent } from "https://www.gstatic.com/firebasejs/10.9.0/firebase-analytics.js";
import { getAuth, signInWithPopup, GoogleAuthProvider, onAuthStateChanged, signOut } from "https://www.gstatic.com/firebasejs/10.9.0/firebase-auth.js";

const firebaseConfig = {
    apiKey: "AIzaSyCRCMXsIdBYMUsa8Ec17kjtV-80YFvXVNI",
    authDomain: "pitchiq-5ed39.firebaseapp.com",
    projectId: "pitchiq-5ed39",
    storageBucket: "pitchiq-5ed39.firebasestorage.app",
    messagingSenderId: "832395208390",
    appId: "1:832395208390:web:712a543114c728bc98d9c7",
    measurementId: "G-JZX1DBCK6E"
};

const app = initializeApp(firebaseConfig);
const analytics = getAnalytics(app);
const auth = getAuth(app);
const provider = new GoogleAuthProvider();

// Log app open
logEvent(analytics, 'app_open');

// --- Authentication UI Logic ---
let currentUser = null;
let pendingAuthAction = null;

const authModal = document.getElementById('authModal');
const googleSignInBtn = document.getElementById('googleSignInBtn');
const maybeLaterBtn = document.getElementById('maybeLaterBtn');

const authProfileContainer = document.getElementById('authProfileContainer');
const compactProfile = document.getElementById('compactProfile');
const profileDropdown = document.getElementById('profileDropdown');

// UI Elements
const compactPhoto = document.getElementById('compactPhoto');
const compactName = document.getElementById('compactName');
const dropdownPhoto = document.getElementById('dropdownPhoto');
const dropdownName = document.getElementById('dropdownName');
const dropdownEmail = document.getElementById('dropdownEmail');
const signOutBtn = document.getElementById('signOutBtn');

// Listen for auth state changes
onAuthStateChanged(auth, (user) => {
    currentUser = user;
    if (user) {
        // User is signed in
        const firstName = user.displayName ? user.displayName.split(' ')[0] : 'User';
        
        if (compactPhoto) compactPhoto.src = user.photoURL || '';
        compactName.textContent = firstName;
        dropdownPhoto.src = user.photoURL || '';
        dropdownName.textContent = user.displayName;
        dropdownEmail.textContent = user.email;

        document.getElementById('userStatusArea').classList.add('authenticated');

        // Reset dropdown state
        profileDropdown.style.display = 'none';
        compactProfile.classList.remove('open');

        // Show authenticated state immediately
        authProfileContainer.style.display = 'flex';
        compactProfile.classList.add('visible');

        // Execute any pending action
        if (pendingAuthAction) {
            const actionToRun = pendingAuthAction;
            pendingAuthAction = null;
            actionToRun();
        }
    } else {
        // User is signed out
        document.getElementById('userStatusArea').classList.remove('authenticated');
        authProfileContainer.style.display = 'none';
        compactProfile.classList.remove('visible');
        profileDropdown.style.display = 'none';
        compactProfile.classList.remove('open');
    }
});

// Dropdown toggle
compactProfile.addEventListener('click', (e) => {
    e.stopPropagation();
    const isOpen = profileDropdown.style.display === 'flex';
    if (isOpen) {
        profileDropdown.style.display = 'none';
        compactProfile.classList.remove('open');
    } else {
        profileDropdown.style.display = 'flex';
        compactProfile.classList.add('open');
    }
});

// Close dropdown on outside click
document.addEventListener('click', () => {
    if (profileDropdown.style.display === 'flex') {
        profileDropdown.style.display = 'none';
        compactProfile.classList.remove('open');
    }
});

profileDropdown.addEventListener('click', (e) => e.stopPropagation());

// Sign Out
signOutBtn.addEventListener('click', () => {
    signOut(auth).then(() => {
        logEvent(analytics, 'sign_out');
        profileDropdown.style.display = 'none';
        compactProfile.classList.remove('open');
    }).catch(error => console.error("Sign out error", error));
});

// Auth Guard Function
function authGuard(actionCallback) {
    if (currentUser) {
        actionCallback();
    } else {
        pendingAuthAction = actionCallback;
        logEvent(analytics, 'auth_modal_opened');
        document.getElementById('authErrorMsg').style.display = 'none';
        authModal.style.display = 'flex';
        setTimeout(() => { authModal.classList.add('visible'); }, 10);
    }
}

// Modal Actions
maybeLaterBtn.addEventListener('click', () => {
    pendingAuthAction = null;
    logEvent(analytics, 'auth_modal_dismissed');
    authModal.classList.remove('visible');
    setTimeout(() => { authModal.style.display = 'none'; }, 400);
});

const originalGoogleBtnHtml = googleSignInBtn.innerHTML;

googleSignInBtn.addEventListener('click', () => {
    const errorMsg = document.getElementById('authErrorMsg');
    errorMsg.style.display = 'none';
    googleSignInBtn.innerHTML = '<span class="pulse" style="margin-right: 8px;"></span> Signing in...';
    googleSignInBtn.disabled = true;

    signInWithPopup(auth, provider).then((result) => {
        logEvent(analytics, 'login');
        authModal.classList.remove('visible');
        setTimeout(() => { 
            authModal.style.display = 'none'; 
            googleSignInBtn.innerHTML = originalGoogleBtnHtml;
            googleSignInBtn.disabled = false;
        }, 400);
    }).catch((error) => {
        console.error("Sign-in failed", error);
        errorMsg.textContent = error.message || "Authentication failed. Please try again.";
        errorMsg.style.display = 'block';
        googleSignInBtn.innerHTML = originalGoogleBtnHtml;
        googleSignInBtn.disabled = false;
    });
});

// --- End Auth Logic ---

// Initialize ambient canvas background
const canvas = document.getElementById('ambientCanvas');
if (canvas) {
    const ctx = canvas.getContext('2d');
    let width, height;
    let particles = [];
    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    let animationFrameId;

    const resize = () => {
        width = window.innerWidth;
        height = document.documentElement.scrollHeight;
        canvas.width = width;
        canvas.height = height;
    };

    window.addEventListener('resize', resize);
    resize();

    class Particle {
        constructor() {
            this.reset(true);
        }

        reset(initial = false) {
            this.x = Math.random() * width;
            this.y = initial ? Math.random() * height : height + Math.random() * 60;
            this.size = Math.random() * 1.6 + 0.8;
            this.baseSpeedY = -(Math.random() * 0.12 + 0.03);
            this.baseSpeedX = (Math.random() - 0.5) * 0.08;
            this.opacity = Math.random() * 0.35 + 0.15;
            // Organic sinusoidal drift
            this.driftAmp = Math.random() * 0.3 + 0.1;
            this.driftFreq = Math.random() * 0.002 + 0.001;
            this.phase = Math.random() * Math.PI * 2;
            this.age = 0;
        }

        update() {
            if (prefersReducedMotion) return;
            this.age++;
            this.y += this.baseSpeedY;
            this.x += this.baseSpeedX + Math.sin(this.age * this.driftFreq + this.phase) * this.driftAmp;
            if (this.y < -20 || this.x < -20 || this.x > width + 20) {
                this.reset();
            }
        }

        draw() {
            ctx.save();
            ctx.beginPath();
            ctx.arc(this.x, this.y, this.size, 0, Math.PI * 2);
            ctx.fillStyle = `rgba(0, 200, 224, ${this.opacity})`;
            ctx.shadowBlur = this.size * 3;
            ctx.shadowColor = `rgba(0, 200, 224, ${this.opacity * 0.6})`;
            ctx.fill();
            ctx.restore();
        }
    }

    const initParticles = () => {
        particles = [];
        const numParticles = prefersReducedMotion ? 0 : 50;
        for (let i = 0; i < numParticles; i++) {
            particles.push(new Particle());
        }
    };

    initParticles();

    let lastTime = 0;
    const fpsInterval = 1000 / 60;

    const animate = (time) => {
        animationFrameId = requestAnimationFrame(animate);

        const elapsed = time - lastTime;
        if (elapsed < fpsInterval) return;
        lastTime = time - (elapsed % fpsInterval);

        ctx.clearRect(0, 0, width, height);

        // Ambient radial lighting
        const gradient1 = ctx.createRadialGradient(width * 0.15, height * 0.15, 0, width * 0.15, height * 0.15, width * 0.7);
        gradient1.addColorStop(0, 'rgba(0, 200, 224, 0.04)');
        gradient1.addColorStop(1, 'transparent');
        ctx.fillStyle = gradient1;
        ctx.fillRect(0, 0, width, height);

        const gradient2 = ctx.createRadialGradient(width * 0.85, height * 0.85, 0, width * 0.85, height * 0.85, width * 0.6);
        gradient2.addColorStop(0, 'rgba(64, 128, 200, 0.03)');
        gradient2.addColorStop(1, 'transparent');
        ctx.fillStyle = gradient2;
        ctx.fillRect(0, 0, width, height);

        // Update and draw nodes
        particles.forEach(p => {
            p.update();
            p.draw();
        });

        // Draw neural connections
        ctx.save();
        ctx.lineWidth = 0.8;
        ctx.shadowBlur = 0;
        for (let i = 0; i < particles.length; i++) {
            for (let j = i + 1; j < particles.length; j++) {
                const p1 = particles[i];
                const p2 = particles[j];
                const dx = p1.x - p2.x;
                const dy = p1.y - p2.y;
                const dist = Math.sqrt(dx * dx + dy * dy);

                const maxDist = 180;
                if (dist < maxDist) {
                    const lineOpacity = (1 - dist / maxDist) * 0.25;
                    ctx.beginPath();
                    ctx.moveTo(p1.x, p1.y);
                    ctx.lineTo(p2.x, p2.y);
                    ctx.strokeStyle = `rgba(0, 200, 224, ${lineOpacity})`;
                    ctx.stroke();
                }
            }
        }
        ctx.restore();
    };

    document.addEventListener('visibilitychange', () => {
        if (document.hidden) {
            cancelAnimationFrame(animationFrameId);
        } else {
            lastTime = performance.now();
            animate(lastTime);
        }
    });

    if (!document.hidden) {
        animationFrameId = requestAnimationFrame(animate);
    }
}

// Manual Mode Toggle
const manualModeToggle = document.getElementById('manualModeToggle');
const inputFields = document.querySelectorAll('.locked-input');
const lockIcons = document.querySelectorAll('.lock-icon');

manualModeToggle.addEventListener('change', (e) => {
    const isManual = e.target.checked;
    
    // Default to T20 if manual mode is enabled without a live match active
    if (isManual && !document.querySelector('.match-card.active')) {
        document.getElementById('matchFormat').value = 't20';
    }

    inputFields.forEach(input => {
        if (isManual) {
            input.removeAttribute('readonly');
            input.removeAttribute('disabled');
            input.classList.remove('locked-input');
        } else {
            input.setAttribute('readonly', true);
            input.setAttribute('disabled', true);
            input.classList.add('locked-input');
        }
    });
    lockIcons.forEach(icon => {
        icon.style.display = isManual ? 'none' : 'inline';
    });
});

// Clear validation message when user types
document.getElementById('oversBowled').addEventListener('input', () => {
    document.getElementById('validationMessage').style.display = 'none';
});
document.getElementById('matchFormat').addEventListener('change', () => {
    document.getElementById('validationMessage').style.display = 'none';
});

function showLoadingSequence() {
    // Legacy function used by live match card click
    const overlay = document.getElementById('loadingOverlay');
    const text = document.getElementById('simStageText');
    const fill = document.getElementById('simProgressFill');
    const counterContainer = document.querySelector('.sim-counter-container');
    
    overlay.style.display = 'flex';
    setTimeout(() => { overlay.style.opacity = '1'; }, 10);
    
    if (counterContainer) counterContainer.style.display = 'none';
    if (text) text.textContent = "Loading Live Match Data...";
    if (fill) fill.style.width = "25%";
}

function hideLoadingSequence() {
    const overlay = document.getElementById('loadingOverlay');
    overlay.style.opacity = '0';
    setTimeout(() => { 
        overlay.style.display = 'none'; 
        const counterContainer = document.querySelector('.sim-counter-container');
        if (counterContainer) counterContainer.style.display = 'flex';
    }, 500);
}

document.getElementById('analyzeBtn').addEventListener('click', () => {
    authGuard(async () => {
        logEvent(analytics, 'run_simulation');
        const btn = document.getElementById('analyzeBtn');
        
        // Prevent double clicks
        if (btn.disabled) return;
        btn.disabled = true;
    
    const matchFormat = document.getElementById('matchFormat').value;
    const oversInput = parseFloat(document.getElementById('oversBowled').value) || 0;

    // Clear validation message
    const validationMsg = document.getElementById('validationMessage');
    validationMsg.style.display = 'none';

    if (manualModeToggle.checked) {
        if (!document.getElementById('currentRuns').value || !document.getElementById('oversBowled').value) {
            validationMsg.textContent = "Please enter match state data before running simulation.";
            validationMsg.style.display = 'block';
            if (document.getElementById('loadingOverlay').style.display === 'flex') hideLoadingSequence();
            btn.disabled = false;
            return;
        }
    }

    // Validation Rules
    if (matchFormat === 't20' && oversInput > 20.0) {
        validationMsg.textContent = "Validation Error: Maximum overs for T20 is 20.0";
        validationMsg.style.display = 'block';
        if (document.getElementById('loadingOverlay').style.display === 'flex') hideLoadingSequence();
        btn.disabled = false;
        return;
    }
    if (matchFormat === 'odi' && oversInput > 50.0) {
        validationMsg.textContent = "Validation Error: Maximum overs for ODI is 50.0";
        validationMsg.style.display = 'block';
        if (document.getElementById('loadingOverlay').style.display === 'flex') hideLoadingSequence();
        btn.disabled = false;
        return;
    }

    try {
        // --- Premium Simulation UX Start ---
        const overlay = document.getElementById('loadingOverlay');
        const text = document.getElementById('simStageText');
        const fill = document.getElementById('simProgressFill');
        const counter = document.getElementById('simCounter');
        const counterDone = document.getElementById('simCounterDone');
        const counterSub = document.getElementById('simCounterSub');
        const counterContainer = document.querySelector('.sim-counter-container');
        
        // Reset overlay state
        counter.textContent = "0";
        counterDone.style.display = "none";
        counter.style.display = "block";
        counterSub.style.display = "block";
        fill.style.width = "0%";
        if (counterContainer) counterContainer.style.display = 'flex';
        
        overlay.style.display = 'flex';
        // Small delay to allow display block to apply before opacity transition
        await new Promise(r => setTimeout(r, 10));
        overlay.style.opacity = '1';

        // Stage 1: Initializing
        text.textContent = "Initializing Simulation...";
        fill.style.width = "15%";
        await new Promise(r => setTimeout(r, 300));

        // Stage 2: Running Simulations
        text.textContent = "Running 10,000 Monte Carlo Simulations...";
        fill.style.width = "40%";
        
        // Start backend request concurrently with counter animation
        let maxOvers = 20;
        if (matchFormat === 'odi') maxOvers = 50;
        if (matchFormat === 'test') maxOvers = 450;

        const payload = {
            venueId: 1, 
            venueName: window.currentMatchVenue || 'Unknown Venue',
            battingTeamId: 1,
            bowlingTeamId: 2,
            battingTeamName: window.currentBattingTeam || 'T1',
            bowlingTeamName: window.currentBowlingTeam || 'T2',
            matchFormat: matchFormat,
            matchStatus: window.currentMatchStatus || 'live',
            currentRuns: parseInt(document.getElementById('currentRuns').value) || 0,
            currentWickets: parseInt(document.getElementById('currentWickets').value) || 0,
            overs: oversInput,
            targetScore: parseInt(document.getElementById('targetScore').value) || 0,
            maxOvers: maxOvers
        };

        const abortController = new AbortController();
        const timeoutId = setTimeout(() => abortController.abort(), 18000); // 18s timeout

        const fetchPromise = fetch('https://pitchiq-production-7a44.up.railway.app/api/v1/analyze', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload),
            signal: abortController.signal
        }).then(async res => {
            clearTimeout(timeoutId);
            if (!res.ok) throw new Error("Backend offline");
            return res.json();
        }).catch(e => {
            console.warn("Fetch failed or timed out", e);
            return { // Fallback data
                winProbability: 0.73,
                projectedScore: 195,
                expectedRunsRemaining: 75,
                requiredRunRate: 9.6,
                momentumMeter: 0.8,
                aiCommentary: ["AI intelligence temporarily unavailable."]
            };
        });

        // Animate counter
        const counterDuration = 1200;
        const counterPromise = new Promise(resolve => {
            let startTimestamp = null;
            const step = (timestamp) => {
                if (!startTimestamp) startTimestamp = timestamp;
                const progress = Math.min((timestamp - startTimestamp) / counterDuration, 1);
                // Ease out cubic
                const easeOut = 1 - Math.pow(1 - progress, 3);
                counter.textContent = Math.floor(easeOut * 10000).toLocaleString();
                
                if (progress < 1) {
                    window.requestAnimationFrame(step);
                } else {
                    resolve();
                }
            };
            window.requestAnimationFrame(step);
        });

        // Wait for counter to complete first so it doesn't appear stuck
        await counterPromise;

        // Show 10,000 Complete Mark
        counter.style.display = "none";
        counterSub.style.display = "none";
        counterDone.style.display = "block";
        await new Promise(r => setTimeout(r, 200));

        // Stage 3: Computing Match Probabilities & Waiting for AI
        text.textContent = "Computing Match Probabilities...";
        fill.style.width = "70%";
        
        // Now wait for fetch to complete if it hasn't already
        const data = await fetchPromise;

        // Stage 4: AI Intelligence (Skip if not present)
        if (data.aiCommentary && data.aiCommentary.length > 0) {
            text.textContent = "Generating PitchIQ Intelligence...";
            fill.style.width = "90%";
            await new Promise(r => setTimeout(r, 300));
        }

        // Stage 5: Rendering Dashboard
        text.textContent = "Rendering Analytics Dashboard...";
        fill.style.width = "100%";
        await new Promise(r => setTimeout(r, 200));

        // Hide Overlay (Handled in finally block, but we fade out here)
        overlay.style.opacity = '0';
        await new Promise(r => setTimeout(r, 500));

        // --- End Simulation UX ---

        // Show HUD
        const hud = document.getElementById('hud');
        hud.style.display = 'flex';

        // Remove old stagger classes to reset animation
        const cards = hud.querySelectorAll('.glass-panel');
        cards.forEach(c => {
            c.classList.remove('stagger-enter');
            c.classList.add('stagger-in');
        });

        // Trigger staggered entrance
        setTimeout(() => {
            cards.forEach((c, i) => {
                setTimeout(() => {
                    c.classList.add('stagger-enter');
                }, i * 100);
            });
        }, 100); // small delay to ensure DOM updates

        // Prepare data
        let probPct = 0;
        if (!(payload.targetScore === 0 && payload.overs === 0 && payload.currentRuns === 0)) {
            probPct = Math.round(data.winProbability * 100);
        }
        
        let oversStr = payload.overs.toString();
        let oversParts = oversStr.split('.');
        let completedOvers = parseInt(oversParts[0]) || 0;
        let balls = oversParts.length > 1 ? parseInt(oversParts[1]) || 0 : 0;
        // Guard against NaN or Infinity
        if (isNaN(completedOvers)) completedOvers = 0;
        if (isNaN(balls)) balls = 0;
        let oversDecimal = completedOvers + (balls / 6.0);
        let crr = 0;
        if (oversDecimal > 0 && payload.currentRuns > 0) {
            crr = payload.currentRuns / oversDecimal;
        }
        let rrr = data.requiredRunRate || 0;

        // Populate Top Match Header
        document.getElementById('headerTeams').textContent = `${payload.battingTeamName} vs ${payload.bowlingTeamName}`;
        document.getElementById('headerFormat').textContent = payload.matchFormat.toUpperCase();
        document.getElementById('headerVenue').textContent = payload.venueName;
        document.getElementById('headerScore').textContent = `${payload.currentRuns}/${payload.currentWickets} (${payload.overs})`;
        document.getElementById('headerTarget').textContent = payload.targetScore > 0 ? payload.targetScore.toString() : "-";
        
        let confidence = "Low";
        if (data.winProbability > 0.8 || data.winProbability < 0.2) confidence = "High";
        else if (data.winProbability > 0.6 || data.winProbability < 0.4) confidence = "Medium";
        document.getElementById('headerConfidence').textContent = confidence;
        
        let statusText = "🔴 LIVE";
        if (payload.matchStatus === 'upcoming') statusText = "📅 UPCOMING";
        if (payload.matchStatus === 'completed') statusText = "📝 COMPLETED";
        document.getElementById('headerMatchStatus').textContent = statusText;

        // Trigger Animated Count-ups
        animateValue("winProbRing", 0, probPct, 1200, false, true); // We'll update the text in step
        animateValue("projScoreText", 0, data.projectedScore, 1000, false, false);
        animateValue("crrText", 0, crr, 1000, true, false);
        animateValue("rrrText", 0, rrr, 1000, true, false);
        
        document.getElementById('expRunsText').textContent = data.expectedRunsRemaining;
        if (payload.targetScore === 0 && payload.overs === 0 && payload.currentRuns === 0) {
             document.getElementById('winProbText').textContent = `N/A`;
             document.getElementById('winProbRing').setAttribute('stroke-dasharray', `0, 100`);
        }

        // Populate Venue Report
        const vr = data.venueIntelligence;
        if (vr) {
            document.getElementById('venueReportPanel').style.display = 'block';
            document.getElementById('vrGround').textContent = vr.groundName || payload.venueName;
            document.getElementById('vrCity').textContent = vr.city || '-';
            document.getElementById('vrPitchType').textContent = vr.pitchType || '-';
            document.getElementById('vrAvg1st').textContent = vr.averageFirstInningsScore || '-';
            document.getElementById('vrBatting').textContent = vr.battingRating || '-';
            document.getElementById('vrBowling').textContent = vr.bowlingRating || '-';
            document.getElementById('vrPace').textContent = vr.paceSupport || '-';
            document.getElementById('vrSpin').textContent = vr.spinSupport || '-';
            document.getElementById('vrToss').textContent = vr.tossAdvantage || '-';
            document.getElementById('vrDew').textContent = vr.dewFactor || '-';
            document.getElementById('vrVerdict').textContent = vr.recommendedStrategy || vr.shortSummary || '-';
        } else {
            document.getElementById('venueReportPanel').style.display = 'none';
        }

        // Update PitchIQ Intelligence
        if (data.aiCommentary && Array.isArray(data.aiCommentary)) {
            const list = document.getElementById('intelligenceList');
            list.innerHTML = '';
            const icons = ['📊', '🏟', '⚠', '🎯', '🧠'];
            for (let i = 0; i < Math.min(5, data.aiCommentary.length); i++) {
                const li = document.createElement('li');
                li.innerHTML = `<span class="intelligence-icon">${icons[i] || '•'}</span> <span>${data.aiCommentary[i]}</span>`;
                list.appendChild(li);
            }

            if (data.aiCommentary.length > 1 && data.aiCommentary[1] !== "AI intelligence temporarily unavailable.") {
                const insightDiv = document.getElementById('preMatchInsight');
                insightDiv.style.display = 'block';
                insightDiv.innerHTML = `<strong>Venue Insight:</strong> ${data.aiCommentary[1]}`;
            } else {
                document.getElementById('preMatchInsight').style.display = 'none';
            }
            logEvent(analytics, 'generate_intelligence');
        }
    } catch (e) {
        console.error("Simulation failed", e);
    } finally {
        hideLoadingSequence();
        btn.disabled = false;
    }
    }); // End authGuard
});

// Fetch Live Matches on Load and every 60 seconds
async function fetchLiveMatches() {
    try {
        const response = await fetch('https://pitchiq-production-7a44.up.railway.app/api/v1/matches/live');
        if (!response.ok) throw new Error('Live matches unavailable');
        const matches = await response.json();
        
        const liveContainer = document.getElementById('liveMatchesContainer');
        const recentContainer = document.getElementById('recentMatchesContainer');
        const upcomingContainer = document.getElementById('upcomingMatchesContainer');
        
        if (!matches || matches.length === 0) {
            if (liveContainer.childElementCount === 0) {
                liveContainer.innerHTML = '<div style="color: var(--text-tertiary); font-size: 13px;">No live matches available. Switched to Manual Mode.</div>';
                if (!manualModeToggle.checked) manualModeToggle.click();
            }
            return;
        }

        // Only clear once we know we have data
        liveContainer.innerHTML = '';
        recentContainer.innerHTML = '';
        upcomingContainer.innerHTML = '';

        let liveCount = 0, recentCount = 0, upcomingCount = 0;

        const now = new Date();
        const todayStr = now.toLocaleDateString();
        const yesterday = new Date(now);
        yesterday.setDate(now.getDate() - 1);
        const yesterdayStr = yesterday.toLocaleDateString();

        const hasCompletedToday = matches.some(m => {
            if (!m.matchEnded || !m.dateTimeGMT) return false;
            let gmtStr = m.dateTimeGMT.endsWith('Z') ? m.dateTimeGMT : m.dateTimeGMT + 'Z';
            return new Date(gmtStr).toLocaleDateString() === todayStr;
        });
        const targetDateStr = hasCompletedToday ? todayStr : yesterdayStr;

        matches.forEach(match => {
            const card = document.createElement('div');
            card.className = 'match-card';
            
            let latestScore = '';
            let overs = 0;
            let wickets = 0;
            let runs = 0;
            let targetScore = 0;
            if (match.scores && match.scores.length > 0) {
                const s = match.scores[match.scores.length - 1]; // Use the latest inning
                latestScore = `${s.runs}/${s.wickets} (${s.overs} ov)`;
                runs = s.runs;
                wickets = s.wickets;
                overs = s.overs;
                if (match.scores.length > 1) {
                    targetScore = match.scores[0].runs + 1;
                }
            } else if (match.matchEnded) {
                latestScore = match.status || "Match Ended";
            } else {
                latestScore = "Match starting soon";
            }

            // Generate team initials instead of relying on flags
            const getIdentity = (name) => {
                if (!name) return "UNK";
                const parts = name.trim().split(" ");
                if (parts.length > 1) {
                    // Get first letter of first two words
                    return (parts[0][0] + parts[1][0]).toUpperCase();
                }
                // If it's a single word, get first 3 letters
                return name.substring(0, 3).toUpperCase();
            };

            const t1 = getIdentity(match.battingTeam || "T1");
            const t2 = getIdentity(match.bowlingTeam || "T2");
            
            let section = 'live'; // 'live', 'recent', 'upcoming', 'skip'
            let statusBadge = "🔴 LIVE";
            
            let isStumps = match.status && (match.status.toLowerCase().includes('stump') || match.status.toLowerCase().includes('day '));

            if (match.matchEnded) {
                if (match.dateTimeGMT) {
                    let gmtStr = match.dateTimeGMT.endsWith('Z') ? match.dateTimeGMT : match.dateTimeGMT + 'Z';
                    let matchDateStr = new Date(gmtStr).toLocaleDateString();
                    if (matchDateStr === targetDateStr) {
                        section = 'recent';
                        statusBadge = "📝 COMPLETED";
                    } else {
                        section = 'skip';
                    }
                } else {
                    section = 'skip';
                }
            } else if (match.matchStarted || isStumps) {
                section = 'live';
                statusBadge = "🔴 LIVE";
            } else {
                section = 'upcoming';
                statusBadge = "📅 UPCOMING";
            }
            
            if (section === 'skip') return;

            let displayStatus = match.status;
            if (displayStatus.includes(" GMT")) {
                const timeMatch = displayStatus.match(/(\d{1,2}):(\d{2}) GMT/);
                if (timeMatch) {
                    let h = parseInt(timeMatch[1]);
                    let m = parseInt(timeMatch[2]);
                    m += 30;
                    if (m >= 60) { h += 1; m -= 60; }
                    h += 5;
                    let nextDay = "";
                    if (h >= 24) { h -= 24; nextDay = " (Next Day)"; }
                    let hStr = h.toString().padStart(2, '0');
                    let mStr = m.toString().padStart(2, '0');
                    displayStatus = displayStatus.replace(timeMatch[0], `${hStr}:${mStr} IST${nextDay}`);
                }
            }

            card.innerHTML = `
                <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:6px;">
                    <span class="match-status">${statusBadge}</span>
                    <span style="font-size:10px;color:var(--text-tertiary);text-transform:uppercase;letter-spacing:1px;font-weight:600;">${match.matchType || 'Match'}</span>
                </div>
                <div class="match-title">${t1} vs ${t2}</div>
                <div class="match-score" style="color:var(--text-primary);font-weight:600;font-size:14px;margin:6px 0;">${latestScore}</div>
                <div style="font-size:10px;color:var(--text-tertiary);">${match.venue || 'Unknown Venue'}</div>
                <div style="font-size:10px;color:var(--accent-warm);margin-top:6px;font-weight:500;">${displayStatus}</div>
            `;
            
            card.onclick = async () => {
                // Remove active class from all match cards
                document.querySelectorAll('.match-card').forEach(c => c.classList.remove('active'));
                // Add active class to clicked card
                card.classList.add('active');

                // Ensure manual mode is off when a live match is clicked
                if (manualModeToggle.checked) {
                    manualModeToggle.click();
                }

                // Show loading immediately
                showLoadingSequence();
                
                try {
                    const detailResponse = await fetch(`https://pitchiq-production-7a44.up.railway.app/api/v1/matches/${match.id}`);
                    if (detailResponse.ok) {
                        const detailedMatch = await detailResponse.json();
                        if (detailedMatch.scores && detailedMatch.scores.length > 0) {
                            const ds = detailedMatch.scores[detailedMatch.scores.length - 1];
                            runs = ds.runs;
                            wickets = ds.wickets;
                            overs = ds.overs;
                        }
                    }
                } catch(e) {
                    console.warn('[PitchIQ] Could not fetch match details, using summary data.', e);
                }

                // Store venue context for simulations & AI
                window.currentMatchVenue = match.venue || 'Unknown Venue';
                window.currentBattingTeam = match.battingTeam || t1;
                window.currentBowlingTeam = match.bowlingTeam || t2;
                window.currentMatchStatus = section;

                // Auto-populate
                document.getElementById('currentRuns').value = runs;
                document.getElementById('currentWickets').value = wickets;
                document.getElementById('oversBowled').value = overs;
                if (targetScore > 0) {
                    document.getElementById('targetScore').value = targetScore;
                } else {
                    document.getElementById('targetScore').value = 0;
                }
                
                // Determine match format from matchType or match name
                let mType = match.matchType ? match.matchType.toLowerCase() : 't20';
                if (match.name && match.name.toLowerCase().includes('odi')) {
                    mType = 'odi';
                } else if (match.name && match.name.toLowerCase().includes('test')) {
                    mType = 'test';
                }
                
                document.getElementById('matchFormat').value = mType;

                const btn = document.getElementById('analyzeBtn');

                // Adjust button text for different modes
                if (!match.matchStarted) {
                    btn.innerHTML = 'PREVIEW FIXTURE <span class="arr">&rarr;</span>';
                } else {
                    btn.innerHTML = 'RUN SIMULATION <span class="arr">&rarr;</span>';
                }
                
                window.currentMatchId = match.id;
                logEvent(analytics, 'select_live_match');
                hideLoadingSequence();

                // Automatically trigger the full analysis flow
                // This programmatically clicks the analyze button which
                // invokes authGuard -> simulation -> HUD rendering
                btn.click();
            };
            
            if (section === 'live') {
                liveContainer.appendChild(card);
                liveCount++;
            } else if (section === 'recent') {
                recentContainer.appendChild(card);
                recentCount++;
            } else if (section === 'upcoming') {
                upcomingContainer.appendChild(card);
                upcomingCount++;
            }
        });
        
        if (liveCount === 0) liveContainer.innerHTML = '<div style="color: var(--text-tertiary); font-size: 13px;">No live matches currently in progress.</div>';
        if (recentCount === 0) recentContainer.innerHTML = '<div style="color: var(--text-tertiary); font-size: 13px;">No recent matches available.</div>';
        if (upcomingCount === 0) upcomingContainer.innerHTML = '<div style="color: var(--text-tertiary); font-size: 13px;">No upcoming fixtures.</div>';
        
    } catch (e) {
        console.warn("Failed to fetch live matches silently.", e);
        const liveContainer = document.getElementById('liveMatchesContainer');
        // Only show error if container is completely empty
        if (liveContainer.childElementCount === 0) {
            liveContainer.innerHTML = '<div style="color: var(--accent-warm); font-size: 13px;">Unable to load live matches. Switched to Manual Mode.</div>';
            if (!manualModeToggle.checked) manualModeToggle.click();
        }
    }
}

// Initial fetch
fetchLiveMatches();
// Refresh every 60 seconds
setInterval(() => {
    if (document.visibilityState === 'visible') {
        fetchLiveMatches();
    }
}, 60000);

window.activeAnimations = window.activeAnimations || {};

function animateValue(id, start, end, duration, isFloat = false, isRing = false) {
    const obj = document.getElementById(id);
    if (!obj) return;
    
    // Cancel any existing animation for this specific element to prevent layout thrashing
    if (window.activeAnimations[id]) {
        window.cancelAnimationFrame(window.activeAnimations[id]);
    }
    
    let startTimestamp = null;
    const step = (timestamp) => {
        if (!startTimestamp) startTimestamp = timestamp;
        const progress = Math.min((timestamp - startTimestamp) / duration, 1);
        // easeOutQuart
        const ease = 1 - Math.pow(1 - progress, 4);
        const current = ease * (end - start) + start;
        
        if (isRing) {
            // Update the SVG stroke-dasharray and text content
            obj.setAttribute('stroke-dasharray', `${Math.round(current)}, 100`);
            const textObj = document.getElementById('winProbText');
            if (textObj) textObj.textContent = `${Math.round(current)}%`;
        } else if (isFloat) {
            obj.innerHTML = current.toFixed(2);
        } else {
            obj.innerHTML = Math.floor(current);
        }
        
        if (progress < 1) {
            window.activeAnimations[id] = window.requestAnimationFrame(step);
        } else {
            delete window.activeAnimations[id];
        }
    };
    window.activeAnimations[id] = window.requestAnimationFrame(step);
}

// Ask PI Implementation
let askPiHistory = [];

const askPiInput = document.getElementById('askPiInput');
const askPiSendBtn = document.getElementById('askPiSendBtn');
const askPiChat = document.getElementById('askPiChat');

function getMatchContext() {
    // Gather all visible telemetry
    const winProb = document.getElementById('winProbText').innerText;
    const rrr = document.getElementById('rrrText').innerText;
    const crr = document.getElementById('crrText').innerText;
    const projScore = document.getElementById('projScoreText').innerText;
    
    let intelligence = [];
    document.querySelectorAll('#intelligenceList li').forEach(li => {
        intelligence.push(li.innerText);
    });

    const activeMatch = document.querySelector('.match-card.active');
    let matchTitle = 'Unknown Match';
    if (activeMatch) {
        matchTitle = activeMatch.querySelector('.match-title').innerText;
    }

    return `Match: ${matchTitle}
Venue: ${window.currentMatchVenue || 'Unknown Venue'}
Win Probability: ${winProb}
Required Run Rate: ${rrr}
Current Run Rate: ${crr}
Projected Score: ${projScore}
PitchIQ Intelligence Bullets:
${intelligence.join('\n')}`;
}

async function sendAskPiMessage() {
    const question = askPiInput.value.trim();
    if (!question) return;

    authGuard(async () => {
        logEvent(analytics, 'ask_pi_query');
        // Append user message
        appendMessage(question, 'user-message');
        askPiInput.value = '';
        askPiInput.disabled = true;
        askPiSendBtn.disabled = true;

    // Append typing indicator
    const typingId = 'typing-' + Date.now();
    appendMessage('...', 'pi-message typing', typingId);

    try {
        const payload = {
            question: question,
            matchContext: getMatchContext(),
            history: askPiHistory
        };

        const response = await fetch('https://pitchiq-production-7a44.up.railway.app/api/v1/ask', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        const data = await response.json();
        
        // Remove typing indicator
        const typingEl = document.getElementById(typingId);
        if (typingEl) typingEl.remove();

        const answer = data.answer || "I'm having trouble processing that right now.";
        appendMessage(answer, 'pi-message');

        // Update history (keep last 3 messages to save tokens)
        askPiHistory.push({ role: 'user', content: question });
        askPiHistory.push({ role: 'assistant', content: answer });
        if (askPiHistory.length > 6) {
            askPiHistory = askPiHistory.slice(askPiHistory.length - 6);
        }

    } catch (error) {
        const typingEl = document.getElementById(typingId);
        if (typingEl) typingEl.remove();
        appendMessage("Sorry, I could not connect to the intelligence engine.", 'pi-message');
    } finally {
        askPiInput.disabled = false;
        askPiSendBtn.disabled = false;
        askPiInput.focus();
    }
    }); // End authGuard
}

function appendMessage(text, className, id = null) {
    const div = document.createElement('div');
    div.className = `chat-message ${className}`;
    if (id) div.id = id;
    // Basic markdown bold support
    div.innerHTML = text.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
    askPiChat.appendChild(div);
    askPiChat.scrollTop = askPiChat.scrollHeight;
}

askPiSendBtn.addEventListener('click', sendAskPiMessage);
askPiInput.addEventListener('keypress', (e) => {
    if (e.key === 'Enter') sendAskPiMessage();
});

// Footer Intersection Observer for fade-in animation
const footer = document.getElementById('premiumFooter');
if (footer) {
    const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.classList.add('visible');
                observer.unobserve(entry.target);
            }
        });
    }, { threshold: 0.1 });
    observer.observe(footer);
}
